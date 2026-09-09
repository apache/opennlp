#!/usr/bin/env bash
# Licensed to the Apache Software Foundation (ASF) under one or more
# contributor license agreements. See the NOTICE file distributed with
# this work for additional information regarding copyright ownership.
# The ASF licenses this file to You under the Apache License, Version 2.0
# (the "License"); you may not use this file except in compliance with
# the License. You may obtain a copy of the License at
#
#   http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

set -euo pipefail

SOURCE_DIR=$(cd "$(dirname "$0")" && pwd)
TEST_ROOT=$(mktemp -d)
trap 'rm -rf "$TEST_ROOT"' EXIT

mkdir -p "$TEST_ROOT/bin"

cp "$SOURCE_DIR/fetch-dictionary.sh" "$TEST_ROOT/fetch-dictionary.sh"
cp "$SOURCE_DIR/fetch-reporter.sh" "$TEST_ROOT/fetch-reporter.sh"

cat > "$TEST_ROOT/bin/curl" <<'EOF'
#!/usr/bin/env bash
set -euo pipefail
output=
while [ "$#" -gt 0 ]; do
  if [ "$1" = "-o" ]; then
    shift
    output=$1
  fi
  shift
done
printf 'fixture download\n' > "$output"
EOF
cat > "$TEST_ROOT/bin/sleep" <<'EOF'
#!/usr/bin/env bash
exit 0
EOF
chmod +x "$TEST_ROOT/bin/curl" "$TEST_ROOT/bin/sleep"

EXPECTED_HASH=$(printf 'fixture download\n' | sha256sum | cut -d' ' -f1)
WRONG_HASH=$(printf '0%.0s' {1..64})

write_bouvier_table() {
  local first_hash=$1
  local hash
  : > "$TEST_ROOT/bouvier-snapshots.tsv"
  for letter in {a..z}; do
    hash=$EXPECTED_HASH
    if [ "$letter" = "a" ]; then
      hash=$first_hash
    fi
    printf '%s\t20011116204718\t%s\t17\n' "$letter" "$hash" \
      >> "$TEST_ROOT/bouvier-snapshots.tsv"
  done
}

printf 'a\t20011116204718\t%s\t17\n' "$EXPECTED_HASH" \
  > "$TEST_ROOT/bouvier-snapshots.tsv"
if PATH="$TEST_ROOT/bin:$PATH" LEGAL_CORPUS_HOME="$TEST_ROOT/dictionary-incomplete" \
    bash "$TEST_ROOT/fetch-dictionary.sh" >/dev/null 2>&1; then
  echo "fetch-dictionary.sh accepted an incomplete snapshot table" >&2
  exit 1
fi

write_bouvier_table "$WRONG_HASH"
if PATH="$TEST_ROOT/bin:$PATH" LEGAL_CORPUS_HOME="$TEST_ROOT/dictionary-wrong" \
    bash "$TEST_ROOT/fetch-dictionary.sh" >/dev/null 2>&1; then
  echo "fetch-dictionary.sh accepted a download with the wrong checksum" >&2
  exit 1
fi

write_bouvier_table "$EXPECTED_HASH"
PATH="$TEST_ROOT/bin:$PATH" LEGAL_CORPUS_HOME="$TEST_ROOT/dictionary-cache" \
  bash "$TEST_ROOT/fetch-dictionary.sh" >/dev/null
printf 'corrupt\n' > "$TEST_ROOT/dictionary-cache/raw/bouvier/bouvier_a.htm"
if PATH="$TEST_ROOT/bin:$PATH" LEGAL_CORPUS_HOME="$TEST_ROOT/dictionary-cache" \
    bash "$TEST_ROOT/fetch-dictionary.sh" >/dev/null 2>&1; then
  echo "fetch-dictionary.sh accepted a corrupted cached file" >&2
  exit 1
fi

printf '190\t%s\t17\n' "$WRONG_HASH" > "$TEST_ROOT/reporter-snapshots.tsv"
if PATH="$TEST_ROOT/bin:$PATH" LEGAL_CORPUS_HOME="$TEST_ROOT/reporter-wrong" \
    bash "$TEST_ROOT/fetch-reporter.sh" 190 190 >/dev/null 2>&1; then
  echo "fetch-reporter.sh accepted a download with the wrong checksum" >&2
  exit 1
fi

printf '190\t%s\t17\n190\t%s\t17\n' "$EXPECTED_HASH" "$EXPECTED_HASH" \
  > "$TEST_ROOT/reporter-snapshots.tsv"
if PATH="$TEST_ROOT/bin:$PATH" LEGAL_CORPUS_HOME="$TEST_ROOT/reporter-duplicate" \
    bash "$TEST_ROOT/fetch-reporter.sh" 190 190 >/dev/null 2>&1; then
  echo "fetch-reporter.sh accepted a duplicate snapshot row" >&2
  exit 1
fi

printf '190\t%s\t17\n' "$EXPECTED_HASH" > "$TEST_ROOT/reporter-snapshots.tsv"
PATH="$TEST_ROOT/bin:$PATH" LEGAL_CORPUS_HOME="$TEST_ROOT/reporter-cache" \
  bash "$TEST_ROOT/fetch-reporter.sh" 190 190 >/dev/null
printf 'corrupt\n' > "$TEST_ROOT/reporter-cache/raw/us/190.zip"
if PATH="$TEST_ROOT/bin:$PATH" LEGAL_CORPUS_HOME="$TEST_ROOT/reporter-cache" \
    bash "$TEST_ROOT/fetch-reporter.sh" 190 190 >/dev/null 2>&1; then
  echo "fetch-reporter.sh accepted a corrupted cached file" >&2
  exit 1
fi

echo "fetch script checksum tests passed"
