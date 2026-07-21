#!/usr/bin/env bash
# Licensed to the Apache Software Foundation (ASF) under one or more
# contributor license agreements.  See the NOTICE file distributed with
# this work for additional information regarding copyright ownership.
# The ASF licenses this file to You under the Apache License, Version 2.0
# (the "License"); you may not use this file except in compliance with
# the License. You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

# Downloads a mecab-format dictionary archive for the lattice tokenizer.
#
# This script only fetches the archive and, when a checksum is given, verifies it.
# It does not unpack anything: unpacking is the job of
# opennlp.tools.tokenize.lattice.MecabDictionaryInstaller, which extracts exactly the
# files a MecabDictionary reads and nothing else. See README-mecab-dictionaries.md in
# this directory for the known dictionary projects, their encodings, and the Java
# steps that follow the download.
#
# The dictionary you download carries its own license. Nothing is bundled with
# Apache OpenNLP and no dictionary location is built in.

set -euo pipefail

usage() {
  echo "usage: $0 <archive-url> <target-file> [expected-sha256]" >&2
  echo "" >&2
  echo "  archive-url      where to fetch the gzip-compressed tar from" >&2
  echo "  target-file      where to store the downloaded archive" >&2
  echo "  expected-sha256  optional checksum; when given, the download is verified" >&2
  echo "                   and a mismatch deletes the file and fails the script" >&2
  exit 2
}

[ $# -lt 2 ] && usage
url="$1"
target="$2"
expected="${3:-}"

# Download to a temporary name first so an interrupted transfer never leaves a
# half-written file at the target path.
tmp="${target}.download"
echo "downloading ${url}"
curl --fail --location --retry 3 --output "${tmp}" "${url}"

actual="$(sha256sum "${tmp}" | cut -d' ' -f1)"
if [ -n "${expected}" ]; then
  if [ "${actual}" != "${expected}" ]; then
    rm -f "${tmp}"
    echo "checksum mismatch: expected ${expected}" >&2
    echo "                   but got  ${actual}" >&2
    exit 1
  fi
  echo "checksum verified: ${actual}"
else
  echo "sha256 of the download: ${actual}"
  echo "record this value and pass it as the third argument next time to verify"
fi

mv "${tmp}" "${target}"
echo "stored ${target}"
echo ""
echo "next: unpack and load it from Java; see README-mecab-dictionaries.md"
