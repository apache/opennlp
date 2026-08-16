#!/usr/bin/env bash
# Fetches the raw law dictionary sources for the legal vocabulary work.
#
# Primary: Bouvier's Law Dictionary, 6th revised edition (1856), public domain.
# The hand-keyed HTML transcription formerly hosted at constitution.org is
# fetched as 26 per-letter files from pinned Wayback Machine snapshots listed
# in bouvier-snapshots.tsv, so every run downloads byte-identical content.
#
# Optional (WITH_BLACKS=1): the OCR text of Black's Law Dictionary, 2nd
# edition (1910), public domain, from the Internet Archive. OCR quality is
# rough; use it for headword cross-checks, not for definition text.
#
# Every download is appended to $LEGAL_CORPUS_HOME/MANIFEST.tsv as
# url <TAB> fetch-date <TAB> sha256 <TAB> bytes.
#
# Nothing fetched by this script may ever be committed to the repository.
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

fetch() {
  local url=$1 out=$2
  if [ -s "$out" ]; then
    echo "have $(basename "$out"), skipping"
    return 0
  fi
  echo "fetching $(basename "$out")"
  curl -fsSL --retry 3 --retry-delay 5 --max-time 300 "$url" -o "$out.part"
  mv "$out.part" "$out"
  record "$url" "$(sha256sum "$out" | cut -d' ' -f1)" "$(stat -c%s "$out")"
  sleep 1
}

while IFS=$'\t' read -r letter ts; do
  [ -n "${letter:-}" ] && [ -n "${ts:-}" ] || continue
  fetch \
    "https://web.archive.org/web/${ts}id_/http://www.constitution.org:80/bouv/bouvier_${letter}.htm" \
    "$RAW/bouvier_${letter}.htm"
done < "$HERE/bouvier-snapshots.tsv"

if [ "${WITH_BLACKS:-0}" = "1" ]; then
  fetch \
    "https://archive.org/download/lawdictionar_blac_1910_00/lawdictionar_blac_1910_00_djvu.txt" \
    "$DEST/raw/blacks2-1910-ocr.txt"
fi

echo "raw dictionary files under $RAW"
