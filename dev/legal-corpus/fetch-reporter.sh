#!/usr/bin/env bash
# Fetches United States Reports volumes from the Caselaw Access Project's
# static bulk site (https://static.case.law/us/). The supported volumes and
# expected checksums are listed in reporter-snapshots.tsv.
#
# Usage: fetch-reporter.sh [FIRST [LAST]]     (defaults: 190 220)
#
# Each volume arrives as us/<N>.zip containing per-case JSON with the full
# casebody. Every download is appended to $LEGAL_CORPUS_HOME/MANIFEST.tsv as
# url <TAB> fetch-date <TAB> sha256 <TAB> bytes.
#
set -euo pipefail

FIRST=${1:-190}
LAST=${2:-220}
case "$FIRST:$LAST" in
  *[!0-9:]*|:*|*:) echo "FIRST and LAST must be positive integers" >&2; exit 1 ;;
esac
if [ "$FIRST" -lt 1 ] || [ "$LAST" -lt 1 ] || [ "$FIRST" -gt "$LAST" ]; then
  echo "FIRST and LAST must define an ascending positive range" >&2
  exit 1
fi

HERE=$(cd "$(dirname "$0")" && pwd)

validate_snapshots() {
  local previous=0
  local count=0
  local volume expected_hash expected_bytes extra
  while IFS=$'\t' read -r volume expected_hash expected_bytes extra; do
    case "$volume" in
      *[!0-9]*|'')
        echo "invalid reporter snapshot volume on row $((count + 1))" >&2
        return 1
        ;;
    esac
    case "$expected_hash" in
      *[!0-9a-f]*|'')
        echo "invalid reporter snapshot checksum on row $((count + 1))" >&2
        return 1
        ;;
    esac
    case "$expected_bytes" in
      *[!0-9]*|'')
        echo "invalid reporter snapshot size on row $((count + 1))" >&2
        return 1
        ;;
    esac
    if [ -n "${extra:-}" ] || [ "$volume" -le "$previous" ] \
        || [ "${#expected_hash}" -ne 64 ] || [ "$expected_bytes" -lt 1 ]; then
      echo "invalid reporter snapshot values on row $((count + 1))" >&2
      return 1
    fi
    previous=$volume
    count=$((count + 1))
  done < "$HERE/reporter-snapshots.tsv"
  if [ "$count" -eq 0 ]; then
    echo "reporter-snapshots.tsv must not be empty" >&2
    return 1
  fi
}

validate_snapshots

DEST=${LEGAL_CORPUS_HOME:-$HOME/.cache/opennlp-legal-corpus}
RAW="$DEST/raw/us"
MANIFEST="$DEST/MANIFEST.tsv"
mkdir -p "$RAW"
touch "$MANIFEST"

record() {
  printf '%s\t%s\t%s\t%s\n' \
    "$1" "$(date -u +%Y-%m-%dT%H:%M:%SZ)" "$2" "$3" >> "$MANIFEST"
}

verify() {
  local file=$1 expected_hash=$2 expected_bytes=$3
  local actual_hash actual_bytes
  actual_hash=$(sha256sum "$file" | cut -d' ' -f1)
  actual_bytes=$(stat -c%s "$file")
  if [ "$actual_hash" != "$expected_hash" ] || [ "$actual_bytes" != "$expected_bytes" ]; then
    echo "checksum or size mismatch for $file" >&2
    echo "expected $expected_hash $expected_bytes" >&2
    echo "actual   $actual_hash $actual_bytes" >&2
    return 1
  fi
}

for ((vol = FIRST; vol <= LAST; vol++)); do
  snapshot=$(awk -F '\t' -v volume="$vol" '$1 == volume { print $2 "\t" $3; exit }' \
    "$HERE/reporter-snapshots.tsv")
  if [ -z "$snapshot" ]; then
    echo "volume $vol has no checksum in reporter-snapshots.tsv" >&2
    exit 1
  fi
  IFS=$'\t' read -r expected_hash expected_bytes <<< "$snapshot"
  out="$RAW/$vol.zip"
  if [ -s "$out" ]; then
    verify "$out" "$expected_hash" "$expected_bytes"
    echo "verified us/$vol.zip"
    continue
  fi
  url="https://static.case.law/us/$vol.zip"
  echo "fetching us/$vol.zip"
  curl -fsSL --retry 3 --retry-delay 5 --max-time 600 "$url" -o "$out.part"
  if ! verify "$out.part" "$expected_hash" "$expected_bytes"; then
    rm -f "$out.part"
    exit 1
  fi
  mv "$out.part" "$out"
  record "$url" "$expected_hash" "$expected_bytes"
  sleep 1
done

echo "raw reporter volumes under $RAW"
