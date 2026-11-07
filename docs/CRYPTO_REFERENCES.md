# Cryptographic implementation references

The protocol module delegates HKDF-SHA-256 to Google Tink 1.24.0. The app uses the Android artifact; JVM tests use the Java artifact. Tink's setup page documents version 1.24.0, Java 11+ compatibility for the Java artifact, and Android support from API 24. The project's `minSdk` 26 is within that stated Android support range.

The module uses Java Cryptography Architecture providers for EC P-256 signing and key agreement, SHA-256, and AES-GCM. These choices still require compatibility testing on supported devices and an independent review of the complete protocol before handling user recovery data.

References:

- Google Tink Java and Android setup: https://developers.google.com/tink/setup/java
- Tink HKDF implementation source: https://github.com/tink-crypto/tink-java/blob/main/src/main/java/com/google/crypto/tink/subtle/Hkdf.java
- Tink 1.24.0 release notes: https://github.com/tink-crypto/tink-java/releases/tag/v1.24.0
