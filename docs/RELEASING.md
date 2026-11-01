# Releasing

Merging to `staging` or `master` runs [`release.yml`](../.github/workflows/release.yml), which picks
the version from the commit messages, builds a signed APK and App Bundle, tags the commit and
publishes a GitHub Release. If no commit warrants a release, nothing is published.

## One-time setup: the signing key

Keep the keystore and its passwords in a password manager as well as in GitHub. Android only
accepts an update signed with the same key, so if the key is lost, every user has to uninstall and
reinstall the app.

```sh
keytool -genkeypair -v -keystore kaloria-release.jks -alias kaloria \
  -keyalg RSA -keysize 4096 -validity 10000 \
  -dname "CN=Kaloria"

base64 -i kaloria-release.jks | gh secret set KEYSTORE_BASE64
gh secret set KEYSTORE_PASSWORD
gh secret set KEY_ALIAS --body kaloria
gh secret set KEY_PASSWORD
```

Until `KEYSTORE_BASE64` is set, the workflow finishes with a warning and publishes nothing.

## Versions

`versionName` is the release version. `versionCode` is derived from it, so `1.2.3-rc.4` becomes
`1020304` and the final `1.2.3` becomes `1020399`. Minor and patch must stay below 100.

## Building locally

Without a release key, local release builds use the debug key, and they install only on a device
that doesn't have a differently signed copy. To use the release key locally, create
`keystore.properties` (it's git-ignored):

```properties
storeFile=/absolute/path/to/kaloria-release.jks
storePassword=...
keyAlias=kaloria
keyPassword=...
```
