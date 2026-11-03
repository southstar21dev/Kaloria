#!/usr/bin/env bash
# Builds the signed release APKs for version $1 into build/release/, for semantic-release to attach to the
# GitHub Release. Refuses to run without the release key: an APK signed with a throwaway debug key
# couldn't update an installed copy.
set -euo pipefail

version="${1:?usage: build-release.sh <version>}"
if [[ -z "${KALORIA_KEYSTORE_PATH:-}" || ! -f "${KALORIA_KEYSTORE_PATH}" ]]; then
  echo "::error::Release key missing (KALORIA_KEYSTORE_PATH); not publishing unsigned builds." >&2
  exit 1
fi

./gradlew :app:assembleRelease -PkaloriaVersion="$version" --stacktrace

dest=build/release
rm -rf "$dest" && mkdir -p "$dest"
out=app/build/outputs/apk/release
# Stable names, so Obtainium and similar can pick the right file with a pattern.
cp "$out/kaloria-$version-arm64-v8a-release.apk" "$dest/kaloria-$version-arm64-v8a.apk"
cp "$out/kaloria-$version-universal-release.apk" "$dest/kaloria-$version-universal.apk"
ls -l "$dest"
