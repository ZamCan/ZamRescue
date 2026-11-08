# Termux build, signing and installation (verification required)

This guide applies after Android source builds successfully. Do not treat an APK as available before verifying it.

## 1. Pull the repository

In Termux, or in your existing Debian proot environment with Android SDK and a complete JDK:

```sh
git clone https://github.com/ZamCan/ZamRescue.git
cd ZamRescue
java -version
javac -version
./gradlew :secure-protocol:test :app:assembleDebug
```

The Android build currently uses compile SDK 36; install SDK platform 36 and compatible build tools before building. A full JDK 17+ is required. A build may fail in proot on ARM if its Android build-tools binary is not compatible; do not bypass checks. Verify the Gradle wrapper and plugin compatibility.

## 2. Find the APK

```sh
ls -lh app/build/outputs/apk/debug/app-debug.apk
```

The debug APK is signed with the Android debug key automatically; it is suitable for installation tests, not public release.

## 3. Release signing (after release build succeeds)

```sh
./gradlew :app:assembleRelease
```

Do not generate a new signing identity if you need to update an installed application signed with an existing key. Keep signing secrets out of Git. Locate compatible zipalign and apksigner tools and confirm availability:

```sh
command -v zipalign
command -v apksigner
```

If the release APK is unsigned, align it with zipalign, then sign it with apksigner using your privately stored keystore; do not paste keystore passwords into chat or commit them. Confirm signature using `apksigner verify --verbose`. Keep the keystore backed up securely.

## 4. Install

Copy a verified APK to Android shared storage and open it with the system package installer, or use `adb install -r path/to/app.apk` from an authorized host. An update must use the same application ID and signing certificate as the installed version.

## 5. Mandatory physical testing

Pair two devices; establish a real authenticated socket session; transfer an encrypted backup; restore and verify file hashes; test revocation, denied permissions, and interruptions. Do not describe the product as production-ready until these pass.
