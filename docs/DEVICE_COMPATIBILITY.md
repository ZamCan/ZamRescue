# Physical-device compatibility matrix

No physical Android devices are attached to the current development environment. Therefore, every device-specific behavior below is unverified until recorded by an actual test. The project's declared `minSdk` is API 26, `compileSdk` is 36, and `targetSdk` is 36; these configuration values are not evidence that all vendor builds work.

| Device / platform area | Current expectation | Evidence status |
|---|---|---|
| Android API 26–28 | Compose app shell and JVM protocol design target this range; provider and UI compatibility must be tested | Not tested |
| Android API 29–30 | App shell and protocol design target this range | Not tested |
| Android API 31–35 | App shell and protocol design target this range | Not tested |
| Android API 36 | Current compile/target SDK configuration | Not built or tested here |
| Android API 37+ | Future target requires local-network permission/picker review before direct LAN use | Not implemented or tested |
| Android Keystore P-256 signing | Intended production identity storage | Not implemented or tested |
| StrongBox | Optional device capability; must not be assumed | Not queried |
| Wi-Fi Direct / local TCP | Future transports | Not implemented or tested |
| Bluetooth LE | Future transport/discovery | Not implemented or tested |
| USB-C host/accessory/device modes | Compatibility depends on both devices, adapters, and Android support | Not researched on device or tested |
| Direct Boot | No Direct Boot-aware feature exists | Not implemented or tested |
| Accessibility/screen sharing | No accessibility service or screen capture exists | Not implemented or tested |

For each tested device, record manufacturer and model as shown by Android, OS build/API level, transport mode, exact app build hash, test steps, outcome, and any denied permissions. Never infer a compatibility result from a simulator, emulator, or another device in the same product family.
