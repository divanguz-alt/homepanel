#!/bin/sh
# Builds, tests and publishes the current commit as a GitHub Release that the app's updater can find.
# Needs: a clean, pushed working tree; `gh` signed in; the signing key from ~/.gradle/gradle.properties.
set -eu
cd "$(dirname "$0")/.."
export JAVA_HOME="${JAVA_HOME:-/Applications/Android Studio.app/Contents/jbr/Contents/Home}"

if [ -n "$(git status --porcelain)" ]; then echo "Commit your changes first." >&2; exit 1; fi
git push -q

code=$(git rev-list --count HEAD)
name="1.$code"
tag="v$name"
if gh release view "$tag" >/dev/null 2>&1; then echo "$tag is already released." >&2; exit 0; fi

./gradlew -q :app:testDebugUnitTest
# Release build: not debuggable (adb can't read the app's data), same signing key as before.
rm -f app/build/outputs/apk/release/app-release.apk # incremental packaging leaves holes otherwise
./gradlew -q :app:assembleRelease

apk="app/build/outputs/apk/release/homepanel-$name.apk"
cp app/build/outputs/apk/release/app-release.apk "$apk"
gh release create "$tag" "$apk" --title "HomePanel $name" --notes "$(git log -1 --format=%s)"
echo "Released $tag"
