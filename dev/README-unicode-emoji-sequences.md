<!--
Licensed to the Apache Software Foundation (ASF) under one or more
contributor license agreements.  See the NOTICE file distributed with
this work for additional information regarding copyright ownership.
The ASF licenses this file to You under the Apache License, Version 2.0
(the "License"); you may not use this file except in compliance with
the License.  You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
-->

# Unicode emoji sequence data

`UnicodeEmojiSequenceGenerator.java` derives the bundled
`opennlp-core/opennlp-runtime/src/main/resources/opennlp/tools/util/normalizer/EmojiSequences.txt`
from these official Unicode Emoji 18.0 files:

* `https://www.unicode.org/Public/18.0.0/emoji/emoji-test.txt`
  (`8f3735cda1f92a779d78af67cf86066bb1f07143dc22f2ac29394d9bc57ab21a`)
* `https://www.unicode.org/Public/18.0.0/ucd/emoji/emoji-data.txt`
  (`80d00f8e616a0ef27fd6b8de3b758c06383b5d917e2977709578e68baf733bf1`)

The generator verifies both SHA-256 checksums and both `# Version:` headers (`18.0`, or `18.0.0`
as emoji-data.txt writes it) against the release pinned in its `RELEASE` table, so a version bump
changes that table, the data file and the NOTICE files. The other bundled Unicode tables
(WordBreakProperty.txt, CaseFolding.txt, confusables.txt, ExtendedPictographic.txt) are separate
copies of Unicode 17.0 files and are not touched by this generator. It keeps the exact
`fully-qualified` and `minimally-qualified` sequences from `emoji-test.txt` (the `S;` records)
and the `Emoji_Component` ranges from `emoji-data.txt` (the `C;` records). The component ranges
only tell the normalizer which stray joiners, modifiers, selectors and tags belong to a neighboring
emoji; a component on its own is never treated as an emoji.

Run the generator from the repository root:

```shell
mkdir -p target/unicode-emoji-generator
javac -d target/unicode-emoji-generator dev/UnicodeEmojiSequenceGenerator.java
java -cp target/unicode-emoji-generator UnicodeEmojiSequenceGenerator \
  /path/to/emoji-test.txt /path/to/emoji-data.txt \
  opennlp-core/opennlp-runtime/src/main/resources/opennlp/tools/util/normalizer/EmojiSequences.txt
```

The generated inventory and its sources are covered by Unicode License V3, reproduced in the
project `LICENSE`. Attribution is recorded in `NOTICE`. The generator is Apache License 2.0.
The runtime module's `src/main/appended-resources/META-INF/LICENSE` and `NOTICE` also carry
the data license and attribution into the standalone runtime JAR.
