# HKey

- Android keyboard project. Run `./gradlew testDebugUnitTest --max-workers=2 --console=plain`; build signed APK with `./gradlew assembleRelease --max-workers=2 --console=plain`.
- Regression for the 1.5.17 report: `./gradlew testDebugUnitTest --tests '*Report1517Test' --max-workers=2`. Covers Telex prefixes, learned raw sequences, numeric field flags, view recreation, and returning to text fields.
- Optional render output: `-Dhkey.appearanceOutputDir=/opt/Hkey/app/build/appearance`. Create the output directory before running the full suite: SettingsAppearanceTest does not create it; KeyboardAppearanceTest does.
- Release output: `app/build/outputs/apk/release/app-release.apk`. Signing is configured through existing Gradle properties; do not print signing credentials or replace the release identity.
- SDK location: `/root/Android/Sdk`; build tools: `35.0.0`. Validate release with `apksigner verify` and `tools/check_release_listener.py <apk> /root/Android/Sdk/build-tools/35.0.0/dexdump`.
