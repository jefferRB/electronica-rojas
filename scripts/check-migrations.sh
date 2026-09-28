#!/usr/bin/env bash
# Fails when a Flyway migration that already exists in BASE was modified, renamed or deleted
# (ER-ARCH-001 ADR-012). Adding new V{n}__*.sql files is always allowed.
# Also fails when two migrations share a version number.
#
# Usage: scripts/check-migrations.sh <base-ref>     e.g. origin/main, a commit SHA
# Local use (Git Bash): scripts/check-migrations.sh main
set -euo pipefail

MIGRATIONS='backend/src/main/resources/db/migration'
base="${1:?usage: check-migrations.sh <base-ref>}"

if ! git rev-parse --verify --quiet "${base}^{commit}" > /dev/null; then
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

duplicates=$(ls "$MIGRATIONS" | sed -n 's/^V\([0-9][0-9._]*\)__.*\.sql$/\1/p' | sort | uniq -d)
if [ -n "$duplicates" ]; then
  echo "::error::Duplicate migration versions: $duplicates"
  exit 1
fi

echo "Migrations OK: existing files unchanged since ${base}; new versions allowed."
