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

The in-tree tests run against project-authored fixtures only. An opt-in test class, `HunspellRealDictionaryTest`, additionally checks everyday morphology against published dictionaries when pointed at a directory of `<name>.aff`/`<name>.dic` pairs (each test skips when its pair is absent):

```
./mvnw test -pl opennlp-core/opennlp-runtime -Dtest=HunspellRealDictionaryTest \
    -Dopennlp.hunspell.dict.dir=/tmp/hunspell-dicts
```

## What the engine supports

Supported affix features include `PFX` and `SFX` rules, continuation classes,
compound flags, blocking flags, `CIRCUMFIX`, and `FULLSTRIP`. The parser rejects
directives that would change stems if ignored. It skips cosmetic tables that do not
affect stemming. Malformed files report the relevant line number. Each affix or
dictionary stream is limited to 64 MiB.
