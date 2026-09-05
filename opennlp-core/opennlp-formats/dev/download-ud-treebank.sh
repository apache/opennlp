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

# Fetches one Universal Dependencies treebank and lays its splits out the way the
# gated dependency-parser evaluation expects: <target-dir>/train.conllu and
# <target-dir>/test.conllu. See README-ud-treebanks.md in this directory for the
# evaluation command and the licensing notes; each treebank carries its own license,
# which you accept by downloading it. Nothing is bundled with Apache OpenNLP.

set -euo pipefail

usage() {
  echo "usage: $0 <treebank-repository> <commit> <target-dir>" >&2
  echo "" >&2
  echo "  treebank-repository  a repository name under github.com/UniversalDependencies," >&2
  echo "                       for example UD_English-EWT" >&2
  echo "  commit               the full lowercase commit SHA to download" >&2
  echo "  target-dir           where train.conllu and test.conllu are placed" >&2
  exit 2
}

[ $# -ne 3 ] && usage
treebank="$1"
commit="$2"
target="$3"

if [ "${#commit}" -ne 40 ]; then
  echo "commit must be a full 40-character lowercase SHA" >&2
  exit 2
fi
case "${commit}" in
  *[!0-9a-f]*)
    echo "commit must be a full 40-character lowercase SHA" >&2
    exit 2
    ;;
esac

# Fetch only the selected commit into a temporary checkout. Only the .conllu files
# are copied to the target directory.
checkout="$(mktemp -d)"
trap 'rm -rf "${checkout}"' EXIT
git init --quiet "${checkout}/${treebank}"
git -C "${checkout}/${treebank}" remote add origin \
  "https://github.com/UniversalDependencies/${treebank}.git"
echo "fetching ${treebank} at ${commit}"
git -C "${checkout}/${treebank}" fetch --quiet --depth 1 origin "${commit}"
actual_commit="$(git -C "${checkout}/${treebank}" rev-parse FETCH_HEAD)"
if [ "${actual_commit}" != "${commit}" ]; then
  echo "fetched ${actual_commit}, expected ${commit}" >&2
  exit 1
fi
git -C "${checkout}/${treebank}" checkout --quiet --detach FETCH_HEAD

mkdir -p "${target}"
for split in train test; do
  # UD names its files <lang_code>-ud-<split>.conllu; the code prefix varies per
  # treebank, so match on the stable -ud-<split> suffix.
  found=""
  for f in "${checkout}/${treebank}/"*"-ud-${split}.conllu"; do
    [ -e "$f" ] && found="$f" && break
  done
  if [ -z "${found}" ]; then
    echo "no *-ud-${split}.conllu in ${treebank}; the treebank may not publish" >&2
    echo "that split (some hide test data or ship dev only)" >&2
    exit 1
  fi
  cp "${found}" "${target}/${split}.conllu"
  echo "wrote ${target}/${split}.conllu ($(wc -l < "${target}/${split}.conllu") lines)"
done

echo ""
echo "run the gated evaluation with:"
echo "  ./mvnw -pl opennlp-core/opennlp-formats -am test \\"
echo "      -Dtest=ConlluDependencyParserEvalTest \\"
echo "      -Dopennlp.forkCount=1 \\"
echo "      -Dopennlp.depparse.ud.dir=${target}"
