#!/usr/bin/env bash
# Builds the signed APK and App Bundle for a release. Called by semantic-release.
set -euo pipefail

version="${1:?usage: build-release.sh <version>}"
: "${KALORIA_KEYSTORE_PATH:?a release must be signed with the release key}"

./gradlew :app:assembleRelease :app:bundleRelease -PkaloriaVersion="$version" --stacktrace

mkdir -p dist
cp app/build/outputs/apk/release/app-release.apk "dist/kaloria-$version.apk"
cp app/build/outputs/bundle/release/app-release.aab "dist/kaloria-$version.aab"
