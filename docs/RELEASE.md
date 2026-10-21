# Release builds

## 1. Create a signing key (once, on your computer)
Requires a JDK (`keytool`).

    keytool -genkeypair -v -keystore release.jks -alias snakecross \
      -keyalg RSA -keysize 2048 -validity 10000

**Back up `release.jks` and its passwords somewhere safe and never commit it.**
If you lose it you cannot update the app under the same signature (Play Store included).

## 2. Add GitHub secrets
Repo -> Settings -> Secrets and variables -> Actions -> New repository secret:

| Secret              | Value                                                    |
|---------------------|----------------------------------------------------------|
| `KEYSTORE_BASE64`   | output of `base64 -w0 release.jks` (macOS: `base64 -i release.jks`) |
| `KEYSTORE_PASSWORD` | the keystore password                                    |
| `KEY_ALIAS`         | `snakecross` (or the alias you chose)                    |
| `KEY_PASSWORD`      | the key password                                         |

## 3. Build
- Tag a version: `git tag v1.0 && git push origin v1.0` -> builds a signed APK + AAB
  and attaches them to a GitHub Release.
- Or Actions -> Release -> Run workflow (artifacts only, no release page).

Bump `versionCode`/`versionName` in `app/build.gradle.kts` before each release.
The `.aab` is what the Play Store wants; the `.apk` is for direct installs.

## Troubleshooting: "base64: invalid input"
`KEYSTORE_BASE64` must be the base64 *text* of `release.jks`, pasted whole, as one value.
- Linux: `base64 -w0 release.jks`   macOS: `base64 -i release.jks | tr -d '\n'`
- Windows PowerShell: `[Convert]::ToBase64String([IO.File]::ReadAllBytes("release.jks"))`
Copy the entire output (it is long, thousands of characters) and paste it into the secret.
Do not paste the file path or the file's raw contents.
