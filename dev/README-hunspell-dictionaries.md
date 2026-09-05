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

The Hunspell stemmer (`opennlp.tools.stemmer.hunspell`) reads a user-supplied
`.dic` word list and its `.aff` affix file. Apache OpenNLP bundles no dictionary
data. The dictionary's readme states its license.

## Where dictionaries come from

The LibreOffice project maintains Hunspell dictionaries by language at
`github.com/LibreOffice/dictionaries`. Each dictionary has a separate license.
For example, SCOWL is the source for the `en_US` dictionary, with terms in
`README_en_US.txt`. Other sources can be used when the `.aff` and `.dic` files
follow the Hunspell format.

OpenNLP does not ship a URL catalog. Applications that manage downloads can keep a
properties file with an entry id followed by `.url`, `.sha512`, and optionally
`.filename` keys. Use a URL for a stable release or commit.

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

The result depends on the loaded dictionary. The in-tree manual example uses a
small dictionary and checks that `workers` stems to `worker`.

The dictionary is immutable and safe to share between threads. The factory creates a
new stemmer for each call, so each thread can use its own instance. A dictionary that
declares a non-UTF-8 encoding through the `SET` directive in its `.aff` file is decoded
accordingly; no conversion is required.

## Testing against real dictionaries

The in-tree tests use project-authored fixtures only. An opt-in test class, `HunspellRealDictionaryTest`, also checks everyday morphology with the LibreOffice `en_US`, `de_DE_frami`, and `hu_HU` dictionaries. Point it at one directory containing all listed `<name>.aff` and `<name>.dic` files. A missing dictionary skips the associated test; a dictionary that cannot be loaded fails it.

```
./mvnw test -pl opennlp-core/opennlp-runtime -am \
    -Dtest=HunspellRealDictionaryTest -Dsurefire.failIfNoSpecifiedTests=false \
    -Dopennlp.hunspell.dict.dir=/tmp/hunspell-dicts
```

## What the engine supports

The engine applies `PFX` and `SFX` rules with strip strings and character-class conditions. It supports a prefix and suffix cross-product, a double suffix sequence connected by continuation classes, identity rules in continuation paths, file-wide `FLAG` modes, file-wide `AF` aliases, and the `SET` encoding declaration. Numeric flags range from 1 through 65000.

Compound decomposition supports `COMPOUNDFLAG`, `COMPOUNDBEGIN`, `COMPOUNDMIDDLE`, `COMPOUNDEND`, `COMPOUNDMIN`, `COMPOUNDWORDMAX`, `COMPOUNDPERMITFLAG`, `COMPOUNDFORBIDFLAG`, `CHECKCOMPOUNDDUP`, `CHECKCOMPOUNDCASE`, and `CHECKCOMPOUNDTRIPLE`. Compound boundaries and minimum lengths use Unicode code points. `NEEDAFFIX` (also named `PSEUDOROOT`), `ONLYINCOMPOUND`, `FORBIDDENWORD`, `CIRCUMFIX`, and `FULLSTRIP` control whether an analysis is accepted.

Other directives are skipped. Their conversion, suggestion, or advanced compound behavior is not applied by this affix stemmer. Comments and unused metadata may contain legacy-encoded bytes even when the file uses UTF-8. Parsed rules and dictionary text are decoded strictly. Default and `long` flag modes preserve raw one-byte flag values used by published UTF-8 dictionaries. Invalid rule counts, aliases, flags, and compound limits fail during loading. Each affix or dictionary stream is rejected when it exceeds `HunspellDictionary.MAX_STREAM_BYTES` (64 MiB).

Skipped directives include `ICONV`, `OCONV`, `COMPLEXPREFIXES`, `COMPOUNDRULE`,
`IGNORE`, and `KEEPCASE`. Loading a dictionary does not apply these rules;
results can differ from Hunspell for words that need them.
