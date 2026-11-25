#!/usr/bin/env bash
set -euo pipefail

root="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
mode="${1:-build}"
destination="${2:-generic/platform=iOS}"
project="$root/ios/Tether.xcodeproj"
if ! command -v xcodebuild >/dev/null || [[ "$(uname -s)" != Darwin ]]; then
  printf '%s\n' 'NOT TESTED: macOS and Xcode are required.' >&2
  exit 69
fi

args=(-project "$project" -scheme Tether -onlyUsePackageVersionsFromResolvedFile)
case "$mode" in
  build) xcodebuild "${args[@]}" -configuration Release -destination "$destination" CODE_SIGNING_ALLOWED=NO build ;;
  test)
    if [[ "$destination" == generic/platform=iOS ]]; then
      printf '%s\n' 'Pass an installed Simulator destination: platform=iOS Simulator,id=<UDID>' >&2
      exit 64
    fi
    xcodebuild "${args[@]}" -configuration Debug -destination "$destination" CODE_SIGNING_ALLOWED=NO test
    ;;
  archive)
    archive="${TETHER_IOS_ARCHIVE_PATH:-$root/ios/build/Tether-Enhanced-Beta3.xcarchive}"
    xcodebuild "${args[@]}" -configuration Release -destination "$destination" -archivePath "$archive" archive
    ;;
  *) printf '%s\n' 'Usage: build-ios-beta.sh [build|test|archive] [destination]' >&2; exit 64 ;;
esac
