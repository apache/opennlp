#!/usr/bin/env bash
# Fetches the raw law dictionary sources for the legal vocabulary work.
#
# Primary: Bouvier's Law Dictionary, 6th revised edition (1856), public domain.
# The HTML transcription is fetched as 26 per-letter files from the Wayback
# Machine snapshots and checksums in bouvier-snapshots.tsv.
#
# Optional (WITH_BLACKS=1): the OCR text of Black's Law Dictionary, 2nd
# edition (1910), public domain, from the Internet Archive. OCR quality is
# rough; use it for headword cross-checks, not for definition text.
#
# Every download is appended to $LEGAL_CORPUS_HOME/MANIFEST.tsv as
# url <TAB> fetch-date <TAB> sha256 <TAB> bytes.
#
set -euo pipefail

HERE=$(cd "$(dirname "$0")" && pwd)
DEST=${LEGAL_CORPUS_HOME:-$HOME/.cache/opennlp-legal-corpus}
RAW="$DEST/raw/bouvier"
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

fetch() {
  local url=$1 out=$2 expected_hash=$3 expected_bytes=$4
  if [ -s "$out" ]; then
    verify "$out" "$expected_hash" "$expected_bytes"
    echo "verified $(basename "$out")"
    return 0
  fi
  echo "fetching $(basename "$out")"
  curl -fsSL --retry 3 --retry-delay 5 --max-time 300 "$url" -o "$out.part"
  if ! verify "$out.part" "$expected_hash" "$expected_bytes"; then
    rm -f "$out.part"
    return 1
  fi
  mv "$out.part" "$out"
  record "$url" "$expected_hash" "$expected_bytes"
  sleep 1
}

fetch_recorded() {
  local url=$1 out=$2
  if [ -s "$out" ]; then
    echo "using cached $(basename "$out")"
    return 0
  fi
  echo "fetching $(basename "$out")"
  curl -fsSL --retry 3 --retry-delay 5 --max-time 300 "$url" -o "$out.part"
  mv "$out.part" "$out"
  record "$url" "$(sha256sum "$out" | cut -d' ' -f1)" "$(stat -c%s "$out")"
  sleep 1
}

validate_snapshots() {
  local letters=abcdefghijklmnopqrstuvwxyz
  local count=0
  local letter timestamp expected_hash expected_bytes extra
  while IFS=$'\t' read -r letter timestamp expected_hash expected_bytes extra; do
    if [ "$count" -ge 26 ] || [ "${letter:-}" != "${letters:$count:1}" ] \
        || [ -n "${extra:-}" ]; then
      echo "invalid Bouvier snapshot row $((count + 1))" >&2
      return 1
    fi
    case "$timestamp" in
      *[!0-9]*|'')
        echo "invalid Bouvier snapshot timestamp on row $((count + 1))" >&2
        return 1
        ;;
    esac
    case "$expected_hash" in
      *[!0-9a-f]*|'')
        echo "invalid Bouvier snapshot checksum on row $((count + 1))" >&2
        return 1
        ;;
    esac
    case "$expected_bytes" in
      *[!0-9]*|'')
        echo "invalid Bouvier snapshot size on row $((count + 1))" >&2
        return 1
        ;;
    esac
    if [ "${#timestamp}" -ne 14 ] || [ "${#expected_hash}" -ne 64 ] \
        || [ "$expected_bytes" -lt 1 ]; then
      echo "invalid Bouvier snapshot values on row $((count + 1))" >&2
      return 1
    fi
    count=$((count + 1))
  done < "$HERE/bouvier-snapshots.tsv"
  if [ "$count" -ne 26 ]; then
    echo "bouvier-snapshots.tsv must list letters a through z" >&2
    return 1
  fi
}

validate_snapshots

while IFS=$'\t' read -r letter timestamp expected_hash expected_bytes; do
  fetch \
    "https://web.archive.org/web/${timestamp}id_/http://www.constitution.org:80/bouv/bouvier_${letter}.htm" \
    "$RAW/bouvier_${letter}.htm" "$expected_hash" "$expected_bytes"
done < "$HERE/bouvier-snapshots.tsv"

if [ "${WITH_BLACKS:-0}" = "1" ]; then
  fetch_recorded \
    "https://archive.org/download/lawdictionar_blac_1910_00/lawdictionar_blac_1910_00_djvu.txt" \
    "$DEST/raw/blacks2-1910-ocr.txt"
fi

echo "raw dictionary files under $RAW"
