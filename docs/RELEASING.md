# Releasing

GitHub builds, signs and publishes every release. Local builds are for development.

## How a release happens

| Push to | CI (`ci.yml`) | Release (`release.yml`) |
|---|---|---|
| `dev`, PRs | Tests, lint, detekt, formatting, R8 build | Nothing |
| `staging` | Same | Release candidate, e.g. `v1.2.0-rc.1` (marked pre-release) |
| `master` | Same | Release, e.g. `v1.2.0` |

semantic-release reads the Conventional Commits since the last tag. If any warrant a release
(`fix:`, `perf:`, `feat:`, breaking changes), it runs `scripts/build-release.sh`, which builds
both APKs signed with the release key, then tags the commit and publishes a GitHub Release with the
changelog and the APKs. Otherwise nothing is published.

## Installing and updating: Obtainium

[Obtainium](https://github.com/ImranR98/Obtainium) installs apps from GitHub Releases and updates
them automatically. Add `https://github.com/southstar21dev/Kaloria` as an app and:

- keep only the arm64 APK: *Filter APKs by regular expression* `arm64-v8a` (or `universal` on an
  old 32-bit phone);
- to also get release candidates from `staging`, turn on *Include prereleases*.

F-Droid and IzzyOnDroid aren't an option: they don't accept apps with proprietary libraries, and
Kaloria bundles Google's ML Kit.

## Versions

Every build works out its version from git, the same way semantic-release does: it takes the
highest `v*` tag and applies the Conventional Commits made since. Run `git fetch --tags` first so
the tags are current.

```sh
./gradlew -q :app:printVersion
```

Commits that don't warrant a release, such as `docs:` and `chore:`, leave the version alone.

## Building an APK

```sh
./gradlew :app:assembleRelease
```

This makes two APKs in `app/build/outputs/apk/release/`:

| APK | Size | For |
|---|---|---|
| `kaloria-<version>-arm64-v8a-release.apk` | ~26 MB | Any 64-bit ARM phone (practically every phone in use) |
| `kaloria-<version>-universal-release.apk` | ~72 MB | Anything else: 32-bit ARM phones, x86 emulators |

Almost all of the size is ML Kit's native OCR and barcode engines, which ship once per CPU type.

`versionCode` orders releases after their candidates: `1.2.3-rc.4` is 1020304 and `1.2.3` is
1020399, so every published build installs as an update over the previous one.

## Signing

Releases are signed with the Kaloria release key. Android only installs an update signed with the
same key as the installed app, so **losing the key means every user has to uninstall**.

- The key: `~/.config/kaloria/kaloria-release.jks` on the maintainer's Mac (alias `kaloria`). Its
  password is in the git-ignored `keystore.properties` in the project root. Keep a copy of both in
  a password manager.
- In GitHub: the secrets `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS` and `KEY_PASSWORD`.
  The release workflow fails rather than publish a build signed with another key.
- Certificate SHA-256: `F6:D0:CD:66:A4:72:FC:90:26:A4:AF:88:91:CB:9F:B4:85:0A:CA:00:86:7C:42:C1:A6:0B:F6:FE:88:D1:7F:43`

Local release builds use `keystore.properties` when it exists, and the debug key otherwise:

```properties
storeFile=/absolute/path/to/kaloria-release.jks
storePassword=...
keyAlias=kaloria
keyPassword=...
```

To set up the secrets again (e.g. after rotating the key):

```sh
base64 -i kaloria-release.jks | gh secret set KEYSTORE_BASE64
gh secret set KEYSTORE_PASSWORD
gh secret set KEY_ALIAS --body kaloria
gh secret set KEY_PASSWORD
```
