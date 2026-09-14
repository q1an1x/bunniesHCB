# Dependency decisions — 2026-09-14

The runtime is pinned in `gradle.lockfile`. Refresh locks deliberately, review the resulting distribution, and run the offline compatibility tests before deployment:

```sh
./gradlew test installDist distTar --write-locks
./gradlew dependencies --configuration runtimeClasspath
```

| Dependency | Original runtime | Candidate | Decision |
| --- | --- | --- | --- |
| Calimero | 3.0-M1 | 3.0-M1 | Keep the tested KNX transport API; lifecycle fixes are in HCB. |
| HAP-Java | 2.0.7 | 2.0.7 | Keep pairing serialization, accessory IDs and the existing server API. |
| Netty | 4.1.72.Final | 4.1.138.Final BOM | Align the whole 4.1 family to the current security/bugfix release. |
| JmDNS | 3.5.6 | 3.6.3 | Update within the existing API family. Real mDNS/HomeKit discovery still needs commissioning. |
| Gson | 2.10.1 | 2.14.0 | Update JSON parsing dependency; HA/Broadlink fixtures and CLI inventory cover application paths. |
| Bouncy Castle | bcprov-jdk15on 1.51 | **Retained** | HAP 2.0.7 cannot use modern BC as a drop-in replacement. |

Netty describes 4.1.138.Final as a security and bugfix release. This is a dependency maintenance decision, not a claim that every published Netty issue is reachable through this controller. [Netty release announcement](https://netty.io/news/2026/09/09/4-1-138-Final.html), [security advisories](https://github.com/netty/netty/security/advisories).

The BC constraint was tested: replacing `bcprov-jdk15on:1.51` with `bcprov-jdk18on:1.86` caused `HapCompatibilityTest.hapEncryptionAuthenticatesBothPayloadAndAdditionalData` to fail with `NoClassDefFoundError: org/bouncycastle/crypto/tls/TlsFatalAlert`. HAP's decoder imports the legacy TLS classes, and its encryption implementation explicitly depends on the old Poly1305 key layout. Passing a modern jar through dependency resolution alone is therefore insufficient. The failed trial was reverted. [HAP 2.0.7 source archive](https://repo.maven.apache.org/maven2/io/github/hap-java/hap/2.0.7/hap-2.0.7-sources.jar), [BC official downloads](https://www.bouncycastle.org/download/bouncy-castle-java/).

`HapCompatibilityTest` covers identity generation and reload, Ed25519 signature acceptance/rejection, ChaCha/Poly1305 round trips and tamper rejection, and fragmented HTTP decoding through Netty's embedded channel. It does **not** prove Apple-device pairing, discovery, end-to-end encryption interoperability, or absence of vulnerabilities. There was no real HomeKit pairing session during this audit.

A follow-up HAP migration should replace the legacy crypto implementation as a unit, validate it against protocol vectors and an independent implementation, preserve existing private keys/pairings, and perform local Apple-client commissioning. Avoid split packages or overriding upstream crypto classes from HCB's application classpath. Until then, keep the HomeKit listener within the trusted home network; the current PR is not a claim that the entire dependency graph is current.

Version availability was checked against Maven Central metadata and the publisher's releases. HAP 2.0.7 remained the published release at the time of the audit. [HAP releases](https://github.com/hap-java/HAP-Java/releases), [Netty metadata](https://repo.maven.apache.org/maven2/io/netty/netty-bom/maven-metadata.xml), [JmDNS metadata](https://repo.maven.apache.org/maven2/org/jmdns/jmdns/maven-metadata.xml), [Gson releases](https://github.com/google/gson/releases).
