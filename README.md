# bunniesHCB

A local KNX home controller with HomeKit, lighting policies, and optional Home Assistant and Broadlink integrations. Wall controls linked directly to actuators in ETS remain independent of this process; software buttons and policies require HCB.

The controller includes daily, away, sleep, movie, cleaning, and guest modes. A shared coordinator owns mode transitions; room-level manual holds keep lighting policies from immediately undoing a wall-button or HomeKit change. See [household modes and priorities](docs/house-modes.md).

Requires Java 25. The Gradle wrapper is included. Start with offline validation:

```sh
./gradlew test installDist
python3 -m unittest discover -s tools -p 'test_*.py'
build/install/bunniesHCB/bin/bunniesHCB --mode offline --inventory /tmp/hcb-contract.json
build/install/bunniesHCB/bin/bunniesHCB --explain-mode movie
```

The default mode is **offline**. It validates the entity catalog and exits without opening network connections. Tests use synthetic transports and identities.

| Mode | KNX tunnel | Group reads | Group writes | HomeKit / other integrations |
| --- | --- | --- | --- | --- |
| `offline` | No | No | No | No |
| `observe` | Yes | No | No | No |
| `live` | Yes | Initial state synchronization | Explicit commands | HomeKit enabled; HA/oven optional |

In `live`, policies and software button actions additionally require `--enable-automations`. The KNX clock broadcaster requires `--enable-time-service`. Commands are rejected while disconnected and are never replayed into a replacement connection.

Passive observation example, using a separate state directory:

```sh
build/install/bunniesHCB/bin/bunniesHCB \
  --mode observe --knx-gateway knxd.example.invalid \
  --state-dir /tmp/hcb-observe --observe-seconds 60
```

`observe` sends KNXnet/IP connection/keepalive traffic; it sends no group reads or writes. `health.json` in the state directory records connection state, receive/drop counts, state availability, house-mode phase and manual-hold deadlines every 10 seconds. A fresh `CONNECTED` report describes the receive connection, not proof that every actuator is working.

The installation intentionally sends sensor online telegrams every 15 seconds. **Any received process telegram refreshes the watchdog; 45 seconds of silence requests reconnection.** This does not depend on a particular heartbeat address. Initial state synchronization is excluded from this timeout.

Read [the research and architecture review](docs/architecture-review.md), [rollout and rollback instructions](docs/operations.md), and [dependency constraints](docs/dependencies.md) before running `live`. Do not run two live controllers against the same installation. Keep the original distribution and pairing files for rollback.

Home Assistant credentials can be supplied through `HCB_HA_HOST` and `HCB_HA_TOKEN_FILE`, or `--ha-host` and `--ha-token-file`. The older `--ha-token` option remains accepted but exposes the secret in process arguments. Oven support requires an explicit host/MAC and `--enable-oven`; its experimental HomeKit controls additionally require `--oven-homekit`.

The topology remains declared in `KNXEntities`; typed `KnxBinding` metadata supports offline auditing:

```sh
python3 tools/audit_ets.py /private/home.knxproj \
  --contract /tmp/hcb-contract.json --output /private/ets-audit.json
```

The report contains private home topology. Do not commit ETS projects, runtime state, pairing identities, credentials, or raw bus logs. The auditor does not connect to KNX or modify the ETS project. Its exit codes are `0` for a completed audit without contract errors (warnings may remain), `2` for contract errors, and `1` for an unreadable/invalid input.
