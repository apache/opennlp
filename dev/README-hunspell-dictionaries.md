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
small dictionary and checks that `workers` stems to `work`.

The dictionary is immutable and safe to share between threads. The factory creates a
new stemmer for each call, so each thread can use its own instance. A dictionary that
declares a non-UTF-8 encoding through the `SET` directive in its `.aff` file is decoded
accordingly; no conversion is required.

## Testing against real dictionaries

The runtime tests use project-authored fixtures. `HunspellCompatibilityEval` in `opennlp-eval-tests` extends `AbstractEvalTest` and loads the LibreOffice `en_US`, `de_DE_frami`, and `hu_HU` dictionaries from the `hunspell/` directory of `OPENNLP_DATA_DIR`, the shared `opennlp-data.zip` archive every evaluation uses. It checks strict loading, expected inflections, compounds, concurrent stemming and analysis, and the results recorded from the reference implementation as described below.

The dictionary revision is
[`32b006a2c22a4ac7e8ed3f03346f7b3d85a970a4`](https://github.com/LibreOffice/dictionaries/tree/32b006a2c22a4ac7e8ed3f03346f7b3d85a970a4).
The archive holds `<name>.aff`, `<name>.dic`, and `README_<name>.txt` for each of
the three dictionaries under `hunspell/`. The evaluation verifies the MD5 digests of
the affix and word-list files before loading them, as the other evaluations do, and
fails when a file is missing or changed. These checks cover selected examples, not
all possible words or dictionaries.

The English dictionary's `README_en_US.txt` contains the SCOWL and Ispell
copyright and license notices. The German dictionary is GPL-licensed and must
not be bundled in an Apache release. The Hungarian dictionary offers MPL-2.0
or LGPL-3.0-or-later; select MPL-2.0 and retain that license text with the README.
They are evaluation inputs, not redistributed OpenNLP resources.
See the [ASF third-party license policy](https://www.apache.org/legal/resolved.html)
before proposing to bundle any dictionary.

```
./mvnw test -pl opennlp-eval-tests -am -Peval-tests \
    -Dtest=HunspellCompatibilityEval -Dsurefire.failIfNoSpecifiedTests=false \
    -Dopennlp.forkCount=1 -DOPENNLP_DATA_DIR=/path/to/opennlp-data
```

The evaluation compares 49 input forms with the stems, analyses, and recognition
recorded from the reference implementation. It reports exact result-set matches,
expected differences, unknown-input identity fallbacks, and unexpected results
separately, and fails on an unexpected result. Expected differences specify the
complete OpenNLP output for inputs whose recorded reference output differs, so a
change on either side fails. Concurrency checks compare repeated results with a
single-threaded reference. They do not measure throughput, and compatibility
counts are not accuracy scores.

### How the reference results were recorded

The reference outcomes recorded in `HunspellCompatibilityTest`, `HunspellCompletionTest`, and `HunspellCompatibilityEval` come from Hunspell revision [`e184e22c51fe213f4490e9b36998f0ad3e5e606b`](https://github.com/hunspell/hunspell/commit/e184e22c51fe213f4490e9b36998f0ad3e5e606b), built from source and driven through its C API. The project contains no native source, and no test forks a native process; the fixtures and the recorded outputs are what is committed.

The driver used for recording is about thirty lines of C++ against `hunspell.h`: it calls `Hunspell_create(affixPath, dictionaryPath)`, reads one input per line from standard input in the encoding the affix file declares with `SET`, and for each line calls `Hunspell_spell`, `Hunspell_stem`, or `Hunspell_analyze` as selected by a command-line argument, printing one output line per input with multiple stems or analyses joined by a tab, then releases each result list with `Hunspell_free_list` and the handle with `Hunspell_destroy`. It builds with:

```sh
g++ -std=c++17 -O2 -DHUNSPELL_STATIC \
    -I/path/to/hunspell/src/hunspell \
    /path/to/hunspell/src/hunspell/*.cxx driver.cc -o hunspell-reference
```

Whitespace inside recorded analyses is normalized to single spaces. The fixture tests assert the OpenNLP results and, where recognition deliberately deviates from the recorded reference outcome, name the deviation from the manual; a fixture whose deviation disappears fails, so the recorded outcomes stay honest. To re-record after a reference upgrade, rebuild the driver from the new revision, run the fixtures and the evaluation inputs through it, and update the recorded values.

## What the engine supports

The engine applies `PFX` and `SFX` rules with strip strings and character-class conditions. It supports a prefix and suffix cross-product, a double suffix sequence connected by continuation classes, rules that add and strip no material both on their own and in continuation paths, file-wide `FLAG` modes, file-wide `AF` aliases, and the `SET` encoding declaration. Numeric flags range from 1 through 65535, the full range the reference accepts. A number sign starts a comment at the beginning of a line or after the fields a directive consumes; elsewhere it is an ordinary value, so `BREAK #`, `NEEDAFFIX #`, and affix material consisting of `#` load as written.

`COMPLEXPREFIXES` selects 2 prefix levels and 1 suffix level instead of 1
prefix and 2 suffixes. `ICONV` and `OCONV` use longest-match conversions;
`IGNORE` removes configured characters from input, entries, and affix material.
`KEEPCASE`, `CHECKSHARPS`, `LANG`, `WARN`, and `FORBIDWARN` control case variants
and warning-marked entries. A capitalized word with a further inner capital is also
tried with a lowercase initial, and the Turkic `LANG` values map the dotted and
dotless `i` in both case directions. All-uppercase input also matches mixed-case
entries and flagged all-uppercase entries in their capitalized form, as the
reference does through hidden capitalized homonyms, so `IPODS` stems to `Ipod`
while `Ipods` stays unrecognized; these forms take no part in compounds. An
all-uppercase word with an apostrophe is also tried with the part after the
apostrophe capitalized, so `L'AFRIQUE` finds an elided article rule. Trailing
periods are removed before lookup, and one period is restored when only an entry
listed with it matches, so `texts.` stems to `text` and `etc.` stays `etc.`.
Under `LANG hu`, the part of a word before a hyphen follows the reference's
moving rule: it may be a compound whose opening entry carries one of the
hardwired flags `F`, `G`, or `H`, ignoring compound-forbid and size limits.

Compound decomposition supports positional flags and independent `COMPOUNDRULE`
patterns, including optional and repeated flags. It applies compound permit and
forbid flags, word-count limits, `COMPOUNDROOT`, `COMPOUNDSYLLABLE`, duplicate,
case, triple-letter and pattern restrictions, simplified junctions,
`COMPOUNDMORESUFFIXES`, and `FORCEUCASE`. `CHECKCOMPOUNDREP` checks both `REP`
entries and dictionary `ph:` replacements. Compound boundaries and minimum
lengths use Unicode code points. `BREAK` splits recognized parts recursively;
the default separators are `-`, `^-`, and `-$`, and `BREAK 0` disables them.

The compound restrictions follow the reference implementation in detail. `CHECKCOMPOUNDDUP` compares the two parts joined at each level, so only a repeated closing part rejects a compound. The `CHECKCOMPOUNDREP` and word-pair checks apply to the complete input and to every remainder a further level splits. A junction restored from a `CHECKCOMPOUNDPATTERN` replacement is exempt from the other patterns. A listed spelling whose first homonym carries `COMPOUNDFORBIDFLAG` is barred from every position but the last, including its affixed readings, and a suffix marked `ONLYINCOMPOUND` cannot close a compound.

`NEEDAFFIX` (also named `PSEUDOROOT`), `ONLYINCOMPOUND`, `FORBIDDENWORD`,
`CIRCUMFIX`, and `FULLSTRIP` control whether an analysis is accepted. As in the
reference implementation, the first listed homonym decides whether a spelling is
forbidden, and a forbidden direct or affixed reading also blocks the compound and
`BREAK` readings of that input. Morphology
aliases use `AM`; `st:` supplies an explicit stem, `sp:` prepends surface
material, and `ds:` makes the form derived by the entry's suffixes the stem.

`SYLLABLENUM` supports Hungarian compound syllable adjustments. The deprecated
`LEMMA_PRESENT` directive is validated but has no effect. Obsolete
`COMPOUNDFIRST`, `COMPOUNDLAST`, `ONLYROOT`, `HU_KOTOHANGZO`, and `GENERATE`
metadata have no effect on stemming or analysis in the pinned reference and
are ignored. The active compound and affix directives remain applicable.

`HunspellStemmer.analyze(text)` returns an immutable list of distinct analyses
as space-separated Hunspell fields in the reference field order. Entries without
`st:` use the entry text. A suffix without morphological fields contributes `fl:`
and its flag after the entry fields. A prefix without morphological fields
contributes its affix text before the stem when no suffix follows and `fl:` with
its flag otherwise; an entry without fields then contributes the prefix's `fl:`
field after the stem. Compound components begin with `pa:`, and a closing
component without affixes or entry fields carries no `st:` field. Unknown input
returns an empty list.
Analysis preserves field text without `OCONV`. The shared `Stemmer` interface
is unchanged. The manual contains an executable example.

Comments and unused metadata may contain legacy-encoded bytes even when the file uses UTF-8. Parsed rules and dictionary text are decoded strictly. Default and `long` flag modes preserve raw one-byte flag values used by published UTF-8 dictionaries. Invalid rule counts, aliases, flags, and compound limits fail during loading in both modes. Each affix or dictionary stream is rejected when it exceeds `HunspellDictionary.MAX_STREAM_BYTES` (64 MiB).

## Loading policy

`HunspellDictionary.load(...)` defaults to `LoadMode.STRICT`. Unsupported affix
directives cause an `IOException` identifying the directive and source line.
Path-based loading includes the affix path. Valid Hunspell dictionaries using
unsupported features require an explicit choice to load partially.

Unknown directive names cause rejection. Recognized metadata and settings outside stemming,
such as `NAME`, `TRY`, and `WORDCHARS`, are ignored. `REP` is parsed when
`CHECKCOMPOUNDREP` makes replacements affect compound recognition; otherwise it
is unused suggestion data.

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

Partial loading does not apply skipped behavior. Strict loading does not
establish complete Hunspell compatibility. The engine does not generate
inflected forms or spelling suggestions.

Compound search permits at most 64 parts and 2048 candidate checks per spelling
variant. Recursive word-break search has the same depth and candidate limits.
Sharp-s case expansion permits at most 64 variants. Compound-rule patterns are
limited to 4096 flag elements. Results are limited to 2048 distinct stems or
analyses. These limits can exclude valid analyses; all included candidates
must pass validation. Simplified triple letters can be restored at multiple
junctions. `CHECKCOMPOUNDPATTERN` replacement applies at one junction.

Native Hunspell's `stem()` and `spell()` do not have equivalent acceptance rules.
The native stemmer can return a stem for KEEPCASE or FORBIDWARN input rejected by
the spell checker, or return no stem for accepted complex-prefix and simplified
compound forms. OpenNLP applies the dictionary restrictions and returns recognized
compound part stems separately. Tests record native stemming and recognition
results independently. Compatibility requires checking recognition and output.

The German comparisons also distinguish standalone entries from compound-only
readings. For example, OpenNLP returns `Kind` for `Kinder`; native stemming also
returns the compound-only `kind` and an identity-affixed `kinder` reading.
For `Vorschläge`, the pinned native implementation recognizes the input but
returns no stem or morphological analysis. OpenNLP returns component stems and
fields. These are documented differences, not exact matches or a general
accuracy claim.

For prefix-only forms without morphological fields, native analysis may include
untagged prefix text, such as `un st:done fl:U` for `undone`. OpenNLP uses
`fl:U st:done`. The evaluation classifies this formatting distinction as an
expected difference. It also checks incomplete native output for `well-known`
and German compounds such as `Haustür`, without treating additional OpenNLP
output as a general correctness advantage.
