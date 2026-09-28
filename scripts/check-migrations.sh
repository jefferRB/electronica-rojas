#!/usr/bin/env bash
# Fails when a Flyway migration that already exists in BASE was modified, renamed or deleted
# (ER-ARCH-001 ADR-012). Adding new V{n}__*.sql files is always allowed.
# Also fails when two migrations share a version number.
#
# Usage: scripts/check-migrations.sh <base-ref>     e.g. origin/main, a commit SHA
#        scripts/check-migrations.sh --push-before <sha>
# Local use (Git Bash): scripts/check-migrations.sh main
#
# <base-ref> is strict: if it cannot be resolved (after trying to fetch a SHA from origin) the
# check fails. --push-before is for CI pushes (github.event.before) and only skips the comparison
# when there is demonstrably no previous revision: the null SHA (first push of the branch), or an
# old tip that origin no longer serves while the pushed HEAD is a root commit (history of the
# initial release rewritten by amend + force push). Any other unresolvable base still fails.
set -euo pipefail

MIGRATIONS='backend/src/main/resources/db/migration'
NULL_SHA='0000000000000000000000000000000000000000'

push_mode=false
if [ "${1:-}" = '--push-before' ]; then
  push_mode=true
  shift
fi
base="${1:?usage: check-migrations.sh [--push-before] <base-ref>}"

has_commit() { git rev-parse --verify --quiet "${1}^{commit}" > /dev/null; }

check_duplicates() {
  duplicates=$(ls "$MIGRATIONS" | sed -n 's/^V\([0-9][0-9._]*\)__.*\.sql$/\1/p' | sort | uniq -d)
  if [ -n "$duplicates" ]; then
    echo "::error::Duplicate migration versions: $duplicates"
    exit 1
  fi
}

skip_comparison() {
  echo "::notice::No previous reachable commit is available ($1); migration immutability comparison skipped."
  check_duplicates
  echo "Migration versions are unique. Existing migrations were NOT compared: there was no earlier revision."
  exit 0
}

if $push_mode && [ "$base" = "$NULL_SHA" ]; then
  skip_comparison 'first push of the branch'
fi

# A full clone only has commits reachable from refs; an old tip left orphaned by a force push is
# not among them. Ask origin for it by SHA (only a bare hex SHA, never an arbitrary refspec).
if ! has_commit "$base" && [[ "$base" =~ ^[0-9a-f]{40}([0-9a-f]{24})?$ ]] \
    && git remote get-url origin > /dev/null 2>&1; then
  echo "Base ${base} not in the local clone; fetching it from origin."
  git fetch --no-tags --quiet origin "$base" || true
fi

if ! has_commit "$base"; then
  if $push_mode && ! git rev-parse --verify --quiet 'HEAD^' > /dev/null; then
    skip_comparison "previous tip ${base} is no longer available and HEAD is a root commit"
  fi
  echo "::error::Base ref '${base}' not found (is the checkout shallow?)"
  exit 2
fi

# Compare the working tree with the base: covers committed and uncommitted changes.
# --no-renames reports a rename as delete + add, so it is caught as a deletion.
changed=$(git diff --no-renames --name-status --diff-filter=DMT "$base" -- "$MIGRATIONS" || true)
if [ -n "$changed" ]; then
  echo "::error::Applied migrations must never change. Add a new V{n}__*.sql instead:"
  echo "$changed"
  exit 1
fi

check_duplicates

echo "Migrations OK: existing files unchanged since ${base}; new versions allowed."
