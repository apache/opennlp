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

# Hunspell dictionaries for the affix stemmer

The Hunspell stemmer (`opennlp.tools.stemmer.hunspell`) is a clean-room engine over the documented Hunspell dictionary format: a `.dic` word list plus its `.aff` affix companion, both supplied by the user. Apache OpenNLP bundles no dictionary data, so the dictionaries' own licenses never attach to the library; whichever dictionary you download, its license is stated in the readme shipped alongside it and is yours to comply with.

## Where dictionaries come from

The LibreOffice project maintains a large collection of Hunspell dictionaries, one directory per language, at `github.com/LibreOffice/dictionaries`. Licenses differ per dictionary, which is exactly why nothing is bundled: for example, the `en_US` dictionary derives from SCOWL and states its terms in `README_en_US.txt` in the same directory. Many other sources work too; the engine only cares that the pair follows the Hunspell format.

The helper next to this file fetches a pair together with its readme files:

```
./download-hunspell-dictionary.sh en en_US /tmp/hunspell-en_US
```

## Loading and stemming

```java
import java.nio.file.Path;
import opennlp.tools.stemmer.Stemmer;
import opennlp.tools.stemmer.hunspell.HunspellDictionary;
import opennlp.tools.stemmer.hunspell.HunspellStemmerFactory;

HunspellDictionary dictionary = HunspellDictionary.load(
    Path.of("/tmp/hunspell-en_US/en_US.aff"),
    Path.of("/tmp/hunspell-en_US/en_US.dic"));
HunspellStemmerFactory factory = new HunspellStemmerFactory(dictionary);

Stemmer stemmer = factory.newStemmer();
CharSequence stem = stemmer.stem("workers");
```

The dictionary is immutable and safe to share between threads; the factory hands out a fresh stemmer per call, so each thread takes its own from `newStemmer()`. A dictionary that declares a non-UTF-8 encoding through the `SET` directive in its `.aff` file is decoded accordingly; nothing needs converting beforehand.

## What the engine supports

Supported affix features: `PFX` and `SFX` rules with strip strings, character-class conditions, cross-product combination of one prefix with one suffix, twofold suffixes through continuation classes, `FLAG` modes `char`, `long`, and `num`, and the `SET` encoding declaration. Compounding and conversion tables are not interpreted; rules that use them simply do not fire, so unsupported analyses are missed rather than invented. A malformed `.aff` file fails loudly at load time with the offending line number in the message.
