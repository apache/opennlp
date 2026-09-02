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

# CJK dictionaries for the lattice tokenizer

The lattice tokenizer (`opennlp.tools.tokenize.lattice`) segments Japanese and Korean over a MeCab-format dictionary, and the unigram segmenter handles Chinese over a plain word-frequency lexicon. Apache OpenNLP bundles no dictionary data. Download a dictionary from its project and read the license file inside the archive before use.

## Known MeCab-format dictionary projects

| Dictionary | Language | Encoding |
|---|---|---|
| IPADIC 2.7.0 | Japanese | EUC-JP |
| mecab-ko-dic 2.1.1 | Korean | UTF-8 |

Download a release archive directly from the dictionary project. The installer reads
gzip-compressed ustar archives.

The installer extracts only the dictionary payload: the `*.csv` and `*.def` files a
`MecabDictionary` reads, plus the `dicrc` configuration file the distributions ship
alongside them. It flattens the entries into the target directory, and by the same
flattening makes it impossible for an archive path to escape that directory. The
returned count is the number of dictionary files extracted. Tar headers are
checksum-validated, and files are staged on the target filesystem before publication.
The installer does not replace files already present in the target directory.

## Install a local archive

```java
import java.nio.file.Path;
import opennlp.tools.tokenize.lattice.MecabDictionaryInstaller;

Path localArchive = Path.of("mecab-ipadic-2.7.0-20070801.tar.gz");
int files = MecabDictionaryInstaller.install(localArchive.toUri(), Path.of("ipadic"));
```

`MecabDictionaryInstaller.install` accepts trusted local `file:` URIs. Remote download
and verification are outside this API.

## Size budgets for larger dictionaries

Extraction is bounded so a crafted archive cannot fill the disk. By default one
extracted tar entry is limited to 512 MiB and the total extracted payload to 2 GiB.
IPADIC and mecab-ko-dic fit within these limits. For larger dictionaries, such as
UniDic, raise the limits at JVM startup:

```bash
-Dopennlp.install.max.entry.bytes=4294967296 \
-Dopennlp.install.max.total.bytes=8589934592
```

Values must be positive byte counts; anything absent or invalid falls back to the
default.

## Load and tokenize

`MecabDictionary.load(Path)` assumes UTF-8. IPADIC needs the two-argument overload:

```java
import java.nio.charset.Charset;
import java.nio.file.Path;
import opennlp.tools.tokenize.lattice.LatticeTokenizer;
import opennlp.tools.tokenize.lattice.MecabDictionary;

MecabDictionary dictionary =
    MecabDictionary.load(Path.of("ipadic"), Charset.forName("EUC-JP"));
LatticeTokenizer tokenizer = new LatticeTokenizer(dictionary);
// "Tokyo-to ni iku" (go to the Tokyo metropolis), escaped to keep this file ASCII
String[] tokens = tokenizer.tokenize("\u6771\u4EAC\u90FD\u306B\u884C\u304F");
```

For a UTF-8 dictionary such as mecab-ko-dic, `MecabDictionary.load(Path.of("ko-dic"))`
is enough. Loaded dictionaries and tokenizers are immutable and safe to share between
threads, so load once and reuse.

## Chinese: the unigram segmenter needs only a frequency lexicon

`opennlp.tools.tokenize.lattice.UnigramSegmenter` does not use MeCab dictionaries. It
loads a plain text lexicon, one entry per line: the word, its count, and optionally a
tag, separated by whitespace. Any word-frequency list you have the rights to use works:

```java
import java.nio.file.Path;
import opennlp.tools.tokenize.lattice.UnigramSegmenter;

UnigramSegmenter segmenter = UnigramSegmenter.load(Path.of("words.txt"));
// "wo laidao Beijing Tian'anmen" (I arrive at Beijing Tiananmen), escaped as above
String[] tokens = segmenter.tokenize("\u6211\u6765\u5230\u5317\u4EAC\u5929\u5B89\u95E8");
```

As with the dictionaries, the lexicon has its own license; no lexicon data is bundled.
