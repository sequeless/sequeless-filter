#!/usr/bin/env bash
# Scaffold a new ADR from the template with the next sequential number.
# Usage: scripts/new-adr.sh "Short title of the decision"
set -euo pipefail

cd "$(dirname "$0")/.."
adr_dir="docs/adr"
template="$adr_dir/_template.md"

title="${1:-}"
if [ -z "$title" ]; then
  echo "Usage: scripts/new-adr.sh \"Short title of the decision\"" >&2
  exit 1
fi

# Next number = highest existing NNNN + 1.
last=$(ls "$adr_dir" | grep -E '^[0-9]{4}-' | sort | tail -n1 | cut -d- -f1 || echo "0000")
next=$(printf "%04d" $((10#${last:-0} + 1)))

slug=$(echo "$title" | tr '[:upper:]' '[:lower:]' | sed -E 's/[^a-z0-9]+/-/g; s/^-+|-+$//g')
file="$adr_dir/${next}-${slug}.md"

sed -e "s/^# NNNN\. Short title of the decision/# ${next}. ${title}/" \
    -e "s/^- Date: YYYY-MM-DD/- Date: $(date +%F)/" \
    "$template" > "$file"

echo "Created $file"
