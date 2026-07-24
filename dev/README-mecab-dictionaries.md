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

The lattice tokenizer (`opennlp.tools.tokenize.lattice`) segments Japanese and Korean over a mecab-format dictionary, and the unigram segmenter handles Chinese over a plain word-frequency lexicon. Apache OpenNLP bundles no dictionary data: you download a dictionary from the project of your choice, and each dictionary carries its own license. Read the license file inside the archive before use.

## Known mecab-format dictionary projects

| Dictionary | Language | Archive to download | Encoding of the files |
|---|---|---|---|
| IPADIC 2.7.0 | Japanese | `mecab-ipadic-2.7.0-20070801.tar.gz` from the MeCab project's SourceForge file area (`sourceforge.net/projects/mecab/files/mecab-ipadic/2.7.0-20070801/`) | EUC-JP |
| mecab-ko-dic 2.1.1 | Korean | `mecab-ko-dic-2.1.1-20180720.tar.gz` from the project's Bitbucket downloads page (`bitbucket.org/eunjeon/mecab-ko-dic/downloads/`) | UTF-8 |

Both archives are gzip-compressed tars, the format `MecabDictionaryInstaller` reads. The encoding column matters: `MecabDictionary.load(Path)` assumes UTF-8, and a dictionary in any other encoding is loaded with the two-argument overload, for example `MecabDictionary.load(dir, Charset.forName("EUC-JP"))` for IPADIC.

## Step 1: download the archive

Either fetch the archive with any tool you like, or use the helper next to this file, which adds checksum verification:

```
./download-mecab-dictionary.sh <archive-url> ipadic.tar.gz [expected-sha256]
```

On the first run without a checksum the script prints the SHA-256 it computed; record it and pass it on later runs so a changed or corrupted download fails loudly instead of being installed.

## Step 2: unpack with the installer

The installer extracts only the files a `MecabDictionary` reads (`*.csv`, `*.def`, and `dicrc`), flattens them into the target directory, and by the same flattening makes it impossible for an archive path to escape that directory:

```java
import java.nio.file.Path;
import opennlp.tools.tokenize.lattice.MecabDictionaryInstaller;

int files = MecabDictionaryInstaller.install(
    Path.of("ipadic.tar.gz").toUri(), Path.of("ipadic"));
```

The returned count is the number of dictionary files extracted. A remote URI works in the same call if you prefer to skip step 1 entirely and let the installer stream the download.

## Step 3: load and tokenize

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

For a UTF-8 dictionary such as mecab-ko-dic, `MecabDictionary.load(Path.of("ko-dic"))` is enough. Loaded dictionaries and tokenizers are immutable and safe to share between threads, so load once and reuse.

## Chinese: the unigram segmenter needs only a frequency lexicon

`opennlp.tools.tokenize.lattice.UnigramSegmenter` does not use mecab dictionaries. It loads a plain text lexicon, one entry per line: the word, its count, and optionally a tag, separated by whitespace. Any word-frequency list you have the rights to use works:

```java
import java.nio.file.Path;
import opennlp.tools.tokenize.lattice.UnigramSegmenter;

UnigramSegmenter segmenter = UnigramSegmenter.load(Path.of("words.txt"));
// "wo laidao Beijing Tian'anmen" (I arrive at Beijing Tiananmen), escaped as above
String[] tokens = segmenter.tokenize("\u6211\u6765\u5230\u5317\u4EAC\u5929\u5B89\u95E8");
```

As with the dictionaries, the lexicon carries its own license; nothing is bundled.
