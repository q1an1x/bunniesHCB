"""Synthetic ETS fixtures only; never reads the home's project or uses the network."""
import tempfile
import unittest
import zipfile
from pathlib import Path
from audit_ets import inspect_project, compare_contract, group_address

INSTALLATION = '''<KNX xmlns="http://knx.org/xml/project/23"><Project><Installations><Installation>
<GroupAddresses><GroupAddress Id="P-TEST_GA-1" Address="2305" DatapointType="DPST-1-1"/></GroupAddresses>
<Topology><Area Address="1"><Line Address="1"><DeviceInstance Address="10" Hardware2ProgramRefId="M-0001_H-01_HP-0001-01-0000">
<ComObjectInstanceRefs><ComObjectInstanceRef RefId="O-1_R-1" Links="GA-1" WriteFlag="Enabled"/></ComObjectInstanceRefs>
</DeviceInstance></Line></Area></Topology></Installation></Installations></Project></KNX>'''
APPLICATION = '''<KNX><ApplicationProgram><Static>
<ComObjectTable><ComObject Id="M-0001_A-0001-01-0000_O-1" CommunicationFlag="Enabled" ReadFlag="Enabled" WriteFlag="Disabled"/></ComObjectTable>
<ComObjectRefs><ComObjectRef Id="M-0001_A-0001-01-0000_O-1_R-1" RefId="M-0001_A-0001-01-0000_O-1" ReadFlag="Disabled"/></ComObjectRefs>
</Static></ApplicationProgram></KNX>'''


class AuditTest(unittest.TestCase):
    def project(self, installation=INSTALLATION, application=APPLICATION):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / 'synthetic.knxproj'
            with zipfile.ZipFile(path, 'w') as archive:
                archive.writestr('P-TEST/0.xml', installation)
                archive.writestr('M-0001/M-0001_A-0001-01-0000.xml', application)
            return inspect_project(path)

    @staticmethod
    def contract(**changes):
        return {'bindings': [dict(owner='test.light', property='power', address='1/1/1',
                                  dpt='1.001', readable=True, writable=True, **changes)]}

    def test_flags_are_inherited_then_overridden_at_each_level(self):
        report = self.project()
        self.assertEqual([], report['issues'])
        endpoint = report['groups']['1/1/1']['endpoints'][0]
        self.assertTrue(endpoint['resolved'])
        self.assertEqual('Enabled', endpoint['flags']['CommunicationFlag'])
        self.assertEqual('Disabled', endpoint['flags']['ReadFlag'])
        self.assertEqual('Enabled', endpoint['flags']['WriteFlag'])
        self.assertEqual(['NO_READ_RESPONDER'], [f['code'] for f in compare_contract(report, self.contract())])

    def test_main_type_mismatch_is_separate_from_subtype_review(self):
        report = self.project()
        report['groups']['1/1/1']['dpt'] = '3.007'
        findings = compare_contract(report, self.contract())
        self.assertTrue(any(f['code'] == 'DPT_MAIN_MISMATCH' and f['severity'] == 'error' for f in findings))
        report['groups']['1/1/1']['dpt'] = '1.011'
        self.assertIn('DPT_SUBTYPE_REVIEW', [f['code'] for f in compare_contract(report, self.contract())])

    def test_unlinked_and_multiple_responders_are_reported(self):
        report = self.project()
        endpoints = report['groups']['1/1/1']['endpoints']
        endpoints[0]['flags']['ReadFlag'] = 'Enabled'
        endpoints.append(dict(endpoints[0]))
        self.assertIn('MULTIPLE_READ_RESPONDERS', [f['code'] for f in compare_contract(report, self.contract())])
        endpoints.clear()
        self.assertIn('UNLINKED_GROUP', [f['code'] for f in compare_contract(report, self.contract())])

    def test_missing_application_is_reported_without_inventing_flags(self):
        report = self.project(application='<KNX/>')
        self.assertIn('UNRESOLVED_OBJECT', [f['code'] for f in report['issues']])
        self.assertIsNone(report['groups']['1/1/1']['endpoints'][0]['flags']['ReadFlag'])

    def test_dtd_entities_are_rejected_in_utf8_and_utf16(self):
        malicious = '<!DOCTYPE KNX [<!ENTITY x "expansion">]><KNX>&x;</KNX>'
        for encoding in ('utf-8', 'utf-16'):
            with self.subTest(encoding=encoding), self.assertRaises(ValueError):
                self.project(malicious.encode(encoding))

    def test_group_address_bounds(self):
        self.assertEqual('31/7/255', group_address('65535'))
        with self.assertRaises(ValueError):
            group_address('65536')


if __name__ == '__main__':
    unittest.main()
