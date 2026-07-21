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

# Fetches one Hunspell dictionary pair (.aff and .dic) plus its license/readme files
# from the LibreOffice dictionaries collection. Each dictionary carries its own
# license, stated in the readme files this script downloads alongside it. Apache
# OpenNLP bundles no dictionary data. See README-hunspell-dictionaries.md in this
# directory for the Java steps that follow.

set -euo pipefail

usage() {
  echo "usage: $0 <collection-dir> <dictionary-name> <target-dir>" >&2
  echo "" >&2
  echo "  collection-dir   the language directory inside the LibreOffice dictionaries" >&2
  echo "                   repository, for example: en" >&2
  echo "  dictionary-name  the dictionary base name inside it, for example: en_US" >&2
  echo "  target-dir       where the .aff, .dic, and readme files are placed" >&2
  echo "" >&2
  echo "example: $0 en en_US /tmp/hunspell-en_US" >&2
  exit 2
}

[ $# -ne 3 ] && usage
collection="$1"
name="$2"
target="$3"

base="https://raw.githubusercontent.com/LibreOffice/dictionaries/master/${collection}"
mkdir -p "${target}"

# The .aff and .dic pair is mandatory; a missing file fails the script.
for ext in aff dic; do
  echo "downloading ${name}.${ext}"
  curl --fail --location --silent --show-error --retry 3 \
    --output "${target}/${name}.${ext}" "${base}/${name}.${ext}"
done

# The readme carries the dictionary's license; keep it next to the data. Different
# collections name it differently, so try the common patterns and keep what exists.
for readme in "README_${name}.txt" "README_${collection}.txt" "README.txt" "license.txt"; do
  if curl --fail --location --silent --retry 3 \
      --output "${target}/${readme}" "${base}/${readme}" 2>/dev/null; then
    echo "downloaded ${readme} (contains the dictionary's license; read it)"
  else
    rm -f "${target}/${readme}"
  fi
done

echo ""
echo "stored ${target}/${name}.aff and ${target}/${name}.dic"
echo "next: load them from Java; see README-hunspell-dictionaries.md"
