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

The Hunspell stemmer (`opennlp.tools.stemmer.hunspell`) implements the documented Hunspell dictionary format: a `.dic` word list plus its `.aff` affix companion, both supplied by the user. Apache OpenNLP bundles no dictionary data; whichever dictionary you download, its license is stated in the readme shipped alongside it.

## Where dictionaries come from

The LibreOffice project maintains a large collection of Hunspell dictionaries, one directory per language, at `github.com/LibreOffice/dictionaries`. Licenses differ per dictionary, which is why nothing is bundled: for example, the `en_US` dictionary derives from SCOWL and states its terms in `README_en_US.txt` in the same directory. Many other sources work too; the engine only cares that the pair follows the Hunspell format.

OpenNLP does not ship a URL catalog. Applications that manage downloads can keep a
properties file with an entry id followed by `.url`, `.sha512`, and optionally
`.filename` keys. Pin each URL to a stable release or commit.

## Option A: application catalog

Catalog downloads stay inactive until you set `-Dopennlp.download.remote=true`.

```java
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import opennlp.tools.stemmer.hunspell.HunspellDictionaryDownload;
import opennlp.tools.util.DictionaryCatalog;

// JVM flag: -Dopennlp.download.remote=true
try (InputStream in = Files.newInputStream(Path.of("dictionary-catalog.properties"))) {
  DictionaryCatalog catalog = DictionaryCatalog.load(in);
  HunspellDictionaryDownload.downloadFromCatalog(
      catalog, "en_US", Path.of("/tmp/hunspell-en_US"));
}
```

For `en_US`, the catalog ids are `hunspell.en_US.aff`, `hunspell.en_US.dic`, and
optionally `hunspell.en_US.readme`. A complete catalog example lives at
`opennlp-core/opennlp-runtime/src/test/resources/opennlp/tools/util/dictionary-catalog.properties`.
The download test uses local file URLs to exercise this flow without network access.

## Option B: your own files

Fetch `.aff` / `.dic` (and the license readme) with any tool, or with
`ResourceInstaller.install(uri, directory, sha512)`, then load them:

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

What `stem` evaluates to is decided by the dictionary you loaded, and this project ships no dictionary data, so no result is claimed here for `en_US`. The same load-and-stem flow is pinned by `HunspellManualExampleTest` (miniature in-memory dictionary, asserted stems for `workers` and `worker`) and by `HunspellStemmerFactoryTest#testEndToEndUsageFromFiles` (the same pair written to disk). The developer manual chapter `stemmer.xml` cites `HunspellManualExampleTest`.

The dictionary is immutable and safe to share between threads; the factory hands out a fresh stemmer per call, so each thread takes its own from `newStemmer()`. A dictionary that declares a non-UTF-8 encoding through the `SET` directive in its `.aff` file is decoded accordingly; nothing needs converting beforehand.

## Testing against real dictionaries

The in-tree tests run against project-authored fixtures only. An opt-in test class, `HunspellRealDictionaryTest`, additionally checks everyday morphology against published dictionaries when pointed at a directory of `<name>.aff`/`<name>.dic` pairs (each test skips when its pair is absent):

```
./mvnw test -pl opennlp-core/opennlp-runtime -Dtest=HunspellRealDictionaryTest \
    -Dopennlp.hunspell.dict.dir=/tmp/hunspell-dicts
```

## What the engine supports

Supported affix features: `PFX` and `SFX` rules with strip strings, character-class conditions, cross-product combination of one prefix with one suffix, twofold suffixes through continuation classes, `FLAG` modes `char`, `UTF-8`, `long`, and `num`, the `AF` flag alias table, the `SET` encoding declaration, compound decomposition under `COMPOUNDFLAG`, the positional `COMPOUNDBEGIN`/`COMPOUNDMIDDLE`/`COMPOUNDEND` flags, `COMPOUNDMIN`, `COMPOUNDWORDMAX`, `COMPOUNDPERMITFLAG`, `COMPOUNDFORBIDFLAG`, and the `CHECKCOMPOUNDDUP`/`CHECKCOMPOUNDCASE`/`CHECKCOMPOUNDTRIPLE` declarations (compound parts stand on their entries alone or on an entry plus one affix, the zero and dash suffixes dictionaries position linking forms with included), the blocking flags `NEEDAFFIX` (alias `PSEUDOROOT`), `ONLYINCOMPOUND`, and `FORBIDDENWORD`, which keep virtual stems, compound-only parts, and forbidden words out of the reported analyses, and `CIRCUMFIX`, which binds marked prefix and suffix halves to one another as in the German `ge...t` participle, and the `FULLSTRIP` declaration, without which a rule that strips a whole stem is not applied, matching Hunspell. Directives that would change stems when ignored (`ICONV`, `OCONV`, `COMPLEXPREFIXES`, `COMPOUNDRULE`, `IGNORE`, `KEEPCASE`) fail at load time. Cosmetic tables such as `REP`, `MAP`, and `KEY` are skipped, so analyses that would need them are missed rather than invented. A malformed `.aff` file fails loudly at load time with the offending line number in the message. Each affix or dictionary stream is rejected when it exceeds `HunspellDictionary.MAX_STREAM_BYTES` (64 MiB).
