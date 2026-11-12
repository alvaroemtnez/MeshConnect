#!/usr/bin/env bash
# release-version.sh TAG - what a release tag stands for, as KEY=value lines for $GITHUB_ENV.
#
#   v1.3.0          a release
#   v1.3.0-beta.2   a pre-release of 1.3.0: Obtainium offers it only with "Include
#                   prereleases" on, and F-Droid never sees it (its recipe follows ^v[0-9.]+$)
#
# Prints VERSION_NAME (1.3.0 / 1.3.0-beta.2), VERSION_CODE, NOTES_VERSION (the CHANGELOG.md
# section, 1.3.0 for both) and PRERELEASE (true/false).
#
# versionCode is MAJOR*10000 + MINOR*100 + PATCH, and a beta carries the code of the release it
# leads to: the scheme leaves no room below a release, and Android installs an equal code as an
# update, so beta.1 -> beta.2 -> 1.3.0 each install over the last.
set -eu

tag="${1:?usage: release-version.sh TAG}"
n='(0|[1-9][0-9]*)'
re="^v$n\\.$n\\.$n(-beta\\.([1-9][0-9]*))?\$"
if ! [[ "$tag" =~ $re ]]; then
    echo "Tag $tag is not vMAJOR.MINOR.PATCH or vMAJOR.MINOR.PATCH-beta.N" >&2
    exit 1
fi
major=${BASH_REMATCH[1]}
minor=${BASH_REMATCH[2]}
patch=${BASH_REMATCH[3]}
beta="${BASH_REMATCH[5]}"
# Two digits each, or 1.2.100 and 1.3.0 would share a versionCode.
if [ "$minor" -gt 99 ] || [ "$patch" -gt 99 ]; then
    echo "Tag $tag: minor and patch must be at most 99 (versionCode is MAJOR*10000 + MINOR*100 + PATCH)" >&2
    exit 1
fi

notes="$major.$minor.$patch"
echo "VERSION_NAME=$notes${beta:+-beta.$beta}"
echo "VERSION_CODE=$((major * 10000 + minor * 100 + patch))"
echo "NOTES_VERSION=$notes"
if [ -n "$beta" ]; then echo "PRERELEASE=true"; else echo "PRERELEASE=false"; fi
