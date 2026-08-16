#!/usr/bin/env bash
# Fetches United States Reports volumes from the Caselaw Access Project's
# static bulk site (https://static.case.law/us/). Court opinions are edicts
# of government and public domain; CAP's access restrictions were lifted in
# 2024. Stay pre-1928 (volumes through 275) so volume front matter and
# headnotes are public domain by age as well.
#
# Usage: fetch-reporter.sh [FIRST [LAST]]     (defaults: 190 220)
#
# Each volume arrives as us/<N>.zip containing per-case JSON with the full
# casebody. Every download is appended to $LEGAL_CORPUS_HOME/MANIFEST.tsv as
# url <TAB> fetch-date <TAB> sha256 <TAB> bytes.
#
# Nothing fetched by this script may ever be committed to the repository.
set -euo pipefail

FIRST=${1:-190}
LAST=${2:-220}
if [ "$LAST" -gt 275 ]; then
  echo "refusing volumes past 275: post-1927 front matter is not public domain by age" >&2
  exit 1
fi

DEST=${LEGAL_CORPUS_HOME:-$HOME/.cache/opennlp-legal-corpus}
RAW="$DEST/raw/us"
MANIFEST="$DEST/MANIFEST.tsv"
mkdir -p "$RAW"
touch "$MANIFEST"

record() {
  printf '%s\t%s\t%s\t%s\n' \
    "$1" "$(date -u +%Y-%m-%dT%H:%M:%SZ)" "$2" "$3" >> "$MANIFEST"
}

for vol in $(seq "$FIRST" "$LAST"); do
  out="$RAW/$vol.zip"
  if [ -s "$out" ]; then
    echo "have us/$vol.zip, skipping"
    continue
  fi
  url="https://static.case.law/us/$vol.zip"
  echo "fetching us/$vol.zip"
  curl -fsSL --retry 3 --retry-delay 5 --max-time 600 "$url" -o "$out.part"
  mv "$out.part" "$out"
  record "$url" "$(sha256sum "$out" | cut -d' ' -f1)" "$(stat -c%s "$out")"
  sleep 1
done

echo "raw reporter volumes under $RAW"
