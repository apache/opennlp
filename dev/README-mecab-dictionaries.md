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

The lattice tokenizer (`opennlp.tools.tokenize.lattice`) segments Japanese and Korean over a MeCab-format dictionary, and the unigram segmenter handles Chinese over a plain word-frequency lexicon. Apache OpenNLP bundles no dictionary data: you download a dictionary from the project of your choice, and each dictionary contains its own license. Read the license file inside the archive before use.

## Known MeCab-format dictionary projects

| Catalog id | Dictionary | Language | Encoding |
|---|---|---|---|
| `mecab.ipadic` | IPADIC 2.7.0 | Japanese | EUC-JP |
| `mecab.ko-dic` | mecab-ko-dic 2.1.1 | Korean | UTF-8 |

Example download URLs and SHA-512 digests for those ids live in the test resource
`opennlp-core/opennlp-runtime/src/test/resources/opennlp/tools/util/dictionary-catalog.properties`. Both archives are
gzip-compressed tars; `MecabDictionaryInstaller` reads the ustar, pax, and GNU
formats through `ResourceInstaller`.

The installer extracts only the dictionary payload: the `*.csv` and `*.def` files a
`MecabDictionary` reads, plus the `dicrc` configuration file the distributions ship
alongside them. `ResourceInstaller` rejects paths outside the staging directory,
then `MecabDictionaryInstaller` flattens the selected files into the target. The
returned value is the number of dictionary files installed.

## Option A: opt-in catalog install

Applications supply the catalog. Catalog URLs are inactive until you set
`-Dopennlp.download.remote=true` or the equivalent system property in code.

```java
import java.nio.file.Path;
import opennlp.tools.tokenize.lattice.MecabDictionaryInstaller;
import opennlp.tools.util.DictionaryCatalog;

// JVM flag: -Dopennlp.download.remote=true
DictionaryCatalog catalog = DictionaryCatalog.load(catalogProperties);
int files = MecabDictionaryInstaller.installFromCatalog(
    catalog, "mecab.ipadic", Path.of("ipadic"));
```

## Option B: your own URL and digest

```java
import java.net.URI;
import java.nio.file.Path;
import opennlp.tools.tokenize.lattice.MecabDictionaryInstaller;

String expectedSha512 = "..."; // the 128-hex SHA-512 of the archive
int files = MecabDictionaryInstaller.install(
    URI.create("https://example.example/dict.tar.gz"),
    Path.of("dict"),
    expectedSha512);
```

A local `file:` URI may omit the digest:
`MecabDictionaryInstaller.install(localArchive.toUri(), targetDirectory)`.
HTTP and HTTPS sources require a digest. Other URI schemes are rejected.

## Size budgets for larger dictionaries

Fetching and unpacking go through `ResourceInstaller` and are bounded so a crafted
archive cannot fill the disk: by default one download is capped at 1 GiB, the
unpacked payload at 4 GiB, and the archive at 100000 entries. IPADIC and
mecab-ko-dic fit comfortably. For larger dictionaries, such as UniDic, raise the
limits at JVM startup:

```bash
-Dopennlp.download.max.bytes=4294967296 \
-Dopennlp.install.max.total.bytes=8589934592 \
-Dopennlp.install.max.entries=200000
```

Missing, invalid, and nonpositive property values use the default limits.

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

The lexicon archive includes its license; OpenNLP bundles no data.
