#!/usr/bin/env python3
"""Offline ETS project audit. Does not extract archives, use the network, or change a project.

Detailed output contains home topology; keep it outside a public repository.
"""
import argparse
import collections
import hashlib
import json
import re
import sys
import xml.etree.ElementTree as ET
import zipfile
from pathlib import Path

FLAGS = ('CommunicationFlag', 'ReadFlag', 'WriteFlag', 'TransmitFlag', 'UpdateFlag', 'ReadOnInitFlag')


def local(element):
    return element.tag.rsplit('}', 1)[-1]


def nodes(element, tag):
    return (e for e in element.iter() if local(e) == tag)


def group_address(raw):
    number = int(raw)
    if not 0 <= number <= 65535:
        raise ValueError('Invalid group address')
    return f'{number >> 11}/{(number >> 8) & 7}/{number & 255}'


def datapoint(value):
    if not value:
        return None
    parts = value.split('-')
    if parts[0] == 'DPST' and len(parts) == 3:
        return f'{int(parts[1])}.{int(parts[2]):03}'
    if parts[0] == 'DPT' and len(parts) == 2:
        return str(int(parts[1]))
    return value


def read_xml(archive, name):
    info = archive.getinfo(name)
    if info.file_size > 64 * 1024 * 1024:
        raise ValueError('XML member exceeds size limit')
    data = archive.read(name)
    markup = data.replace(b'\x00', b'').upper()  # Also reject UTF-16/32 DTDs before parsing.
    if b'<!DOCTYPE' in markup or b'<!ENTITY' in markup:
        raise ValueError('DTD/entities are not accepted')
    return ET.fromstring(data)


def inspect_project(project):
    with zipfile.ZipFile(project) as archive:
        members = archive.namelist()
        installations = sorted(n for n in members if re.fullmatch(r'P-[^/]+/\d+\.xml', n))
        if not installations:
            raise ValueError('No readable installation XML. Password-protected projects require a local unencrypted export.')
        applications = {}
        groups, devices = {}, []
        issues = []
        for member in installations:
            root = read_xml(archive, member)
            short_groups = {}
            for element in nodes(root, 'GroupAddress'):
                address = group_address(element.get('Address'))
                data = {'address': address, 'name': element.get('Name', ''), 'dpt': datapoint(element.get('DatapointType')), 'endpoints': []}
                if address in groups:
                    issues.append({'code': 'DUPLICATE_GROUP_ADDRESS', 'address': address})
                groups[address] = data
                short_groups[element.get('Id').rsplit('_', 1)[-1]] = data
            for area in nodes(root, 'Area'):
                for line in nodes(area, 'Line'):
                    for device in nodes(line, 'DeviceInstance'):
                        physical = f"{area.get('Address')}.{line.get('Address')}.{device.get('Address')}"
                        hardware = device.get('Hardware2ProgramRefId', '')
                        manufacturer = hardware.split('_')[0]
                        program = manufacturer + '_A-' + hardware.split('_HP-', 1)[1] if '_HP-' in hardware else None
                        app_file = f'{manufacturer}/{program}.xml'
                        if program not in applications:
                            if app_file in members:
                                app = read_xml(archive, app_file)
                                applications[program] = ({e.get('Id'): e.attrib for e in nodes(app, 'ComObject')},
                                                         {e.get('Id'): e.attrib for e in nodes(app, 'ComObjectRef')})
                            else:
                                applications[program] = ({}, {})
                                issues.append({'code': 'UNRESOLVED_APPLICATION', 'device': physical})
                        objects, references = applications[program]
                        devices.append({'address': physical, 'description': device.get('Description', '').strip(),
                                        'product': device.get('ProductRefId'), 'application': program,
                                        'last_download': device.get('LastDownload'),
                                        'additional_addresses': [a.get('Address') for container in nodes(device, 'AdditionalAddresses') for a in nodes(container, 'Address')]})
                        for instance in nodes(device, 'ComObjectInstanceRef'):
                            links = instance.get('Links', '').split()
                            if not links:
                                continue
                            ref_id = instance.get('RefId', '')
                            full_id = ref_id if ref_id.startswith('M-') else f'{program}_{ref_id}'
                            ref = references.get(full_id, {})
                            base = objects.get(ref.get('RefId', ''), {})
                            effective = {**base, **ref, **instance.attrib}
                            resolved = bool(base and ref)
                            if not resolved:
                                issues.append({'code': 'UNRESOLVED_OBJECT', 'device': physical, 'object': ref_id})
                            endpoint = {'device': physical, 'object': ref_id, 'text': effective.get('Text', ''),
                                        'dpt': datapoint(effective.get('DatapointType')), 'size': effective.get('ObjectSize'),
                                        'resolved': resolved, 'flags': {f: effective.get(f) for f in FLAGS}}
                            for index, link in enumerate(links):
                                short_id = link.rsplit('_', 1)[-1]
                                if short_id in short_groups:
                                    short_groups[short_id]['endpoints'].append({**endpoint, 'sending_address': index == 0})
                                else:
                                    issues.append({'code': 'UNRESOLVED_GROUP_LINK', 'device': physical, 'link': link})
        physical_counts = collections.Counter(d['address'] for d in devices)
        for address, count in physical_counts.items():
            if count > 1:
                issues.append({'code': 'DUPLICATE_INDIVIDUAL_ADDRESS', 'device': address})
        return {'project_file': Path(project).name, 'sha256': hashlib.sha256(Path(project).read_bytes()).hexdigest(),
                'devices': devices, 'groups': groups, 'issues': issues,
                'limitations': ['Project data is not proof of current device programming or physical wiring.',
                                'Vendor plugin binary state is not decoded.',
                                'Flags are merged from ComObject, ComObjectRef and ComObjectInstanceRef; missing flags remain unknown.',
                                'No live group reads, tunneling or downloads are performed.']}


def compare_contract(report, contract):
    findings = []
    for binding in contract['bindings']:
        address = binding['address']
        group = report['groups'].get(address)
        common = {'owner': binding['owner'], 'property': binding['property'], 'address': address, 'expected_dpt': binding['dpt']}
        def finding(code, severity='warning', **details):
            findings.append({**common, 'code': code, 'severity': severity, **details})
        if group is None:
            finding('MISSING_GROUP', 'error')
            continue
        endpoints = group['endpoints']
        if not endpoints:
            finding('UNLINKED_GROUP')
        actual = group['dpt']
        if actual and actual.split('.')[0] != binding['dpt'].split('.')[0]:
            finding('DPT_MAIN_MISMATCH', 'error', actual_dpt=actual)
        elif actual and '.' in actual and actual != binding['dpt']:
            finding('DPT_SUBTYPE_REVIEW', actual_dpt=actual)
        if any(not e['resolved'] for e in endpoints):
            finding('UNRESOLVED_ENDPOINT')
        if binding['readable'] and endpoints:
            readers = [e for e in endpoints if e['flags']['CommunicationFlag'] == 'Enabled' and e['flags']['ReadFlag'] == 'Enabled']
            if not readers:
                finding('NO_READ_RESPONDER')
            elif len(readers) > 1:
                finding('MULTIPLE_READ_RESPONDERS', count=len(readers))
        if binding['writable'] and endpoints and not any(e['flags']['CommunicationFlag'] == 'Enabled' and e['flags']['WriteFlag'] == 'Enabled' for e in endpoints):
            finding('NO_WRITE_RECEIVER')
    return findings


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('project', type=Path)
    parser.add_argument('--contract', type=Path)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args(argv)
    try:
        report = inspect_project(args.project)
        if args.contract:
            contract = json.loads(args.contract.read_text())
            report['contract_findings'] = compare_contract(report, contract)
        args.output.parent.mkdir(parents=True, exist_ok=True)
        # Refuse to overwrite an input file or follow a report symlink.
        if args.output.is_symlink() or args.output.resolve() in [args.project.resolve(), args.contract.resolve() if args.contract else None]:
            raise ValueError('Output must be a separate regular file')
        with args.output.open('w') as out:
            args.output.chmod(0o600)
            json.dump(report, out, ensure_ascii=False, indent=2)
        counts = collections.Counter(f['code'] for f in report.get('contract_findings', []))
        print(json.dumps({'devices': len(report['devices']), 'group_addresses': len(report['groups']),
                          'project_issues': len(report['issues']), 'contract_findings': dict(counts)}, ensure_ascii=False))
        return 2 if any(f['severity'] == 'error' for f in report.get('contract_findings', [])) else 0
    except (ValueError, KeyError, OSError, zipfile.BadZipFile, RuntimeError, ET.ParseError) as error:
        print(f'Audit failed: {error}', file=sys.stderr)
        return 1

if __name__ == '__main__':
    sys.exit(main())
