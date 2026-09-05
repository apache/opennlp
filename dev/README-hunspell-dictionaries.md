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
data. Retain the upstream copyright notices and full license text with downloaded
files. A dictionary's license is separate from OpenNLP's Apache License.

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

The in-tree tests use project-authored fixtures only. An opt-in test class, `HunspellRealDictionaryTest`, checks inflections with the LibreOffice `en_US`, `de_DE_frami`, and `hu_HU` dictionaries using `ALLOW_PARTIAL`. It also checks that strict loading rejects dictionaries requiring unsupported directives. Point it at one directory containing the listed `<name>.aff` and `<name>.dic` files. A missing dictionary skips the associated test; a dictionary that cannot be loaded in partial mode fails it. These checks do not establish full Hunspell compatibility.

Keep these downloads outside the checkout and out of source archives and JARs.
The English dictionary's `README_en_US.txt` contains the SCOWL and Ispell
copyright and license notices. The German dictionary is GPL-licensed and must
not be bundled in an Apache release. The Hungarian dictionary offers MPL-2.0
or LGPL-3.0-or-later; select MPL-2.0 and retain that license text with its README.
These are optional local test inputs, not redistributed OpenNLP resources.
See the [ASF third-party license policy](https://www.apache.org/legal/resolved.html)
before proposing to bundle any dictionary.

```
./mvnw test -pl opennlp-core/opennlp-runtime -am \
    -Dtest=HunspellRealDictionaryTest -Dsurefire.failIfNoSpecifiedTests=false \
    -Dopennlp.hunspell.dict.dir=/tmp/hunspell-dicts
```

## What the engine supports

The engine applies `PFX` and `SFX` rules with strip strings and character-class conditions. It supports a prefix and suffix cross-product, a double suffix sequence connected by continuation classes, identity rules in continuation paths, file-wide `FLAG` modes, file-wide `AF` aliases, and the `SET` encoding declaration. Numeric flags range from 1 through 65000.

Compound decomposition supports `COMPOUNDFLAG`, `COMPOUNDBEGIN`, `COMPOUNDMIDDLE`, `COMPOUNDEND`, `COMPOUNDMIN`, `COMPOUNDWORDMAX`, `COMPOUNDPERMITFLAG`, `COMPOUNDFORBIDFLAG`, `CHECKCOMPOUNDDUP`, `CHECKCOMPOUNDCASE`, and `CHECKCOMPOUNDTRIPLE`. Compound boundaries and minimum lengths use Unicode code points. `NEEDAFFIX` (also named `PSEUDOROOT`), `ONLYINCOMPOUND`, `FORBIDDENWORD`, `CIRCUMFIX`, and `FULLSTRIP` control whether an analysis is accepted.

Comments and unused metadata may contain legacy-encoded bytes even when the file uses UTF-8. Parsed rules and dictionary text are decoded strictly. Default and `long` flag modes preserve raw one-byte flag values used by published UTF-8 dictionaries. Invalid rule counts, aliases, flags, and compound limits fail during loading in both modes. Each affix or dictionary stream is rejected when it exceeds `HunspellDictionary.MAX_STREAM_BYTES` (64 MiB).

## Loading policy

`HunspellDictionary.load(...)` defaults to `LoadMode.STRICT`. Unsupported affix
directives cause an `IOException` identifying the directive and source line.
Path-based loading includes the affix path. Valid Hunspell dictionaries using
unsupported features require an explicit choice to load partially.

Unsupported directives include `ICONV`, `OCONV`, `COMPLEXPREFIXES`, `COMPOUNDRULE`,
`IGNORE`, and `KEEPCASE`. Unknown directive names also cause rejection. Recognized
metadata and settings outside stemming, such as `NAME`, `TRY`, `REP`, and
`WORDCHARS`, are ignored. `CHECKCOMPOUNDREP` and `FORBIDWARN` affect accepted
analyses and remain unsupported, even though their associated suggestion or
warning settings can be ignored independently.

Use `ALLOW_PARTIAL` to skip unsupported directives and inspect the diagnostics:

```java
HunspellDictionary partial = HunspellDictionary.load(
    Path.of("dictionary.aff"), Path.of("dictionary.dic"),
    HunspellDictionary.LoadMode.ALLOW_PARTIAL);
for (HunspellDictionary.UnsupportedDirective diagnostic : partial.getUnsupportedDirectives()) {
  System.err.println(diagnostic.directive() + " at "
      + diagnostic.source() + ":" + diagnostic.lineNumber());
}
```

`getUnsupportedDirectives()` returns an immutable list containing the first
source location of each unsupported directive in file order. Recognized settings
outside stemming are excluded from the list. File paths identify file-based
loads; stream-based loads use `affix stream` as the source description.

Partial loading does not apply the skipped behavior. Dictionary morphology
fields are ignored in both modes. Strict affix loading does not establish
complete Hunspell compatibility.
