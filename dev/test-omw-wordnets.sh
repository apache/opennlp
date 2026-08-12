#!/usr/bin/env bash
# Licensed to the Apache Software Foundation (ASF) under one or more
# contributor license agreements.  See the NOTICE file distributed with
# this work for additional information regarding copyright ownership.
# The ASF licenses this file to You under the Apache License, Version 2.0
# (the "License"); you may not use this file except in compliance with
# the License.  You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

set -euo pipefail

readonly RELEASE_URL="https://github.com/omwn/omw-data/releases/download/v2.0"
readonly IT_SHA512="d0ed09eaa6617509a7c8a1162e92d8085570328f5f773a108a168d7c517b02c8a1a79fe81297b7e94722b480b22f44f34419c46675f0c3e3af253504c2c5b380"
readonly ES_SHA512="86851763f10cf9ba1c5ea42e8c09bcbff7954aea22c62fbd229bcf546f236de4d67251582ecfd7956e8dae22e975fdf1d5bfa3fd7e2fbf4d901751c89bc0ca66"
readonly SV_SHA512="897a79c6a6ec43c10024c6ee55aac886bd27182e28127ef1248cc8748c1189e573b05b0b2cd9ada91756f0527b25cbac9a49b88a1f13a897f5442da7f0656c13"

fixture_dir=$(mktemp -d "${TMPDIR:-/tmp}/opennlp-omw.XXXXXXXX")
trap 'rm -rf -- "$fixture_dir"' EXIT

fetch() {
  local language=$1
  local expected=$2
  local archive="$fixture_dir/omw-$language-2.0.tar.xz"
  curl --fail --location --silent --show-error \
    --connect-timeout 15 --max-time 120 \
    --output "$archive" "$RELEASE_URL/omw-$language-2.0.tar.xz"
  local actual
  actual=$(sha512sum "$archive" | cut -d' ' -f1)
  if [[ "$actual" != "$expected" ]]; then
    echo "SHA-512 mismatch for omw-$language-2.0.tar.xz" >&2
    exit 1
  fi
  tar -xJf "$archive" -C "$fixture_dir"
}

fetch it "$IT_SHA512"
fetch es "$ES_SHA512"
fetch sv "$SV_SHA512"

repo_dir=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)
cd "$repo_dir"
./mvnw -pl opennlp-extensions/opennlp-wordnet -am \
  -Dopennlp.forkCount=1 -Drat.skip=true \
  -Dtest=WnLmfOmwIntegrationTest -Dsurefire.failIfNoSpecifiedTests=false \
  -Dopennlp.wordnet.omwDir="$fixture_dir" test
