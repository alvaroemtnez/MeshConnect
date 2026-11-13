#!/usr/bin/env bash
# tools/upstream-sync.sh - what upstream (MeshCore Hardened) has that MeshConnect hasn't merged.
#
#   tools/upstream-sync.sh            compare HEAD with upstream's main on GitHub
#
# Read-only: fetches upstream into FETCH_HEAD and tries the merge in a throwaway worktree, so
# the checkout is left as it was. Prints KEY=VALUE lines for $GITHUB_OUTPUT:
#
#   STATUS=up-to-date | new     SHA=<upstream head>     SHORT=<7 chars>     COUNT=<new commits>
#
# and, when there is something new, writes a pull-request body to $BODY_FILE
# (default upstream-sync.md): the commits, the diffstat split the way FORK.md splits the work
# (shared/ merged, androidApp/ ported by hand) and the files a trial merge conflicts on.
#
# Fetches from GitHub, not from code.alvaroedo.io/mirrors: that org is visible to members only
# and the Actions token cannot read it. The mirror stays what it is for, a copy in case upstream
# disappears. UPSTREAM_URL / UPSTREAM_BRANCH override the source (the tests use a local repo).
set -euo pipefail

URL="${UPSTREAM_URL:-https://github.com/thatSFguy/meshcore-mobile-app.git}"
UP_BRANCH="${UPSTREAM_BRANCH:-main}"
WEB="${UPSTREAM_WEB:-https://github.com/thatSFguy/meshcore-mobile-app}"
BODY="${BODY_FILE:-upstream-sync.md}"

git fetch -q --no-tags "$URL" "$UP_BRANCH"
UP="$(git rev-parse FETCH_HEAD)"
SHORT="$(git rev-parse --short=7 "$UP")"

if git merge-base --is-ancestor "$UP" HEAD; then
    printf 'STATUS=up-to-date\nSHA=%s\nSHORT=%s\nCOUNT=0\n' "$UP" "$SHORT"
    exit 0
fi

BASE="$(git merge-base HEAD "$UP")"
COUNT="$(git rev-list --count "HEAD..$UP")"

# Trial merge, away from the checkout.
WT="$(mktemp -d)"
trap 'git worktree remove --force "$WT" >/dev/null 2>&1 || rm -rf "$WT"' EXIT
git worktree add -q --detach "$WT" HEAD
if git -C "$WT" -c user.name=sync -c user.email=sync@invalid \
        merge -q --no-commit --no-ff "$UP" >/dev/null 2>&1; then
    CONFLICTS=""
else
    CONFLICTS="$(git -C "$WT" diff --name-only --diff-filter=U)"
fi

stat() { git diff --stat=100 --stat-count=60 "$BASE" "$UP" -- "$@"; }
section() {
    local out
    out="$(stat "${@:2}")"
    printf '\n## %s\n\n' "$1"
    if [ -z "$out" ]; then echo "No changes."; else printf '```\n%s\n```\n' "$out"; fi
}

{
    echo "Upstream has $COUNT new commit(s), up to [\`$SHORT\`]($WEB/commit/$UP)."
    echo "Compare: $WEB/compare/$BASE...$UP"
    echo
    echo "**Don't merge with the button.** Merge locally as [FORK.md](FORK.md) → *Syncing* says:"
    echo "take upstream's \`shared/\`, port \`androidApp/\` by hand, then \`./build.sh test\`."
    echo
    echo '```sh'
    echo "git fetch origin upstream/$SHORT && git switch -c upstream-sync"
    echo "git merge FETCH_HEAD"
    echo '```'
    section 'shared/ (protocol and engine: merge)' shared
    section 'androidApp/ (hard fork: port by hand)' androidApp
    section 'Everything else' . ':!shared' ':!androidApp'
    printf '\n## Trial merge\n\n'
    if [ -z "$CONFLICTS" ]; then
        echo "Merges without conflicts. Still read the androidApp/ diff: a clean merge can put upstream's"
        echo "UI back on top of ours."
    else
        echo "Conflicts in:"
        echo
        printf '%s\n' "$CONFLICTS" | sed 's/^/- `/; s/$/`/'
    fi
    printf '\n## Commits\n\n'
    git log --reverse --format="- [\`%h\`]($WEB/commit/%H) %s (%an)" "HEAD..$UP" | head -n 150
    [ "$COUNT" -le 150 ] || echo "- … and $((COUNT - 150)) more"
} > "$BODY"

printf 'STATUS=new\nSHA=%s\nSHORT=%s\nCOUNT=%s\n' "$UP" "$SHORT" "$COUNT"
