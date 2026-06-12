<!--
   Licensed to the Apache Software Foundation (ASF) under one
   or more contributor license agreements.  See the NOTICE file
   distributed with this work for additional information
   regarding copyright ownership.  The ASF licenses this file
   to you under the Apache License, Version 2.0 (the
   "License"); you may not use this file except in compliance
   with the License.  You may obtain a copy of the License at

     http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing,
   software distributed under the License is distributed on an
   "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
   KIND, either express or implied.  See the License for the
   specific language governing permissions and limitations
   under the License.
-->

# Apache OpenNLP :: SpellChecker Addon

A native, language-agnostic spell-correction component for Apache OpenNLP, backed by the
**SymSpell (Symmetric Delete)** algorithm. SymSpell precomputes a *deletes-only* index, so
candidate generation and dictionary lookup reduce to hash lookups — orders of magnitude
faster than Norvig-style edit enumeration while staying memory-reasonable.

## Features

- **`SpellChecker` API** — `lookup` (single word) with `Verbosity` `TOP`/`CLOSEST`/`ALL` and a
  configurable maximum edit distance, and `lookupCompound` (multi-word, context-aware: handles
  wrongly split, wrongly merged, and misspelled words via an optional bigram dictionary).
- **Pluggable edit distance** — Damerau-OSA (default; transpositions cost 1) or Apache Commons
  Text Levenshtein, behind an `EditDistance` interface.
- **Models** — load from plain frequency dictionaries, or from a serialized `SymSpellModel`
  (`SerializableArtifact`) resolvable by language from the classpath via the OpenNLP
  model-resolver (`opennlp-models-spellcheck-{lang}` artifacts).
- **Pipeline integration** — `SpellCheckingCharSequenceNormalizer` (a
  `CharSequenceNormalizer`) and `FilterObjectStream` adapters (line- and token-level) drop
  into existing OpenNLP preprocessing chains.
- **Command-line tools** — build a model and correct text from the shell.

## API

```java
SymSpell spell = new SymSpell(SymSpellConfig.builder()
    .maxDictionaryEditDistance(2)
    .prefixLength(7)
    .build());
new FrequencyDictionaryLoader().loadUnigrams(spell, unigramSource); // word<sep>count
new FrequencyDictionaryLoader().loadBigrams(spell, bigramSource);   // optional: w1 w2<sep>count

// single word
List<SuggestItem> s = spell.lookup("exampel", Verbosity.TOP, 2);   // -> "example"

// multi-word, context aware
List<SuggestItem> c = spell.lookupCompound("the quikc broun fox", 2); // -> "the quick brown fox"
```

## Command line

```bash
# build a model from frequency lists
bin/spellcheck SpellCheckModelBuilder -unigrams freq_en.txt -bigrams bigram_en.txt \
    -model en-spellcheck.bin -lang en

# correct text (per-token, or -compound true for sentence-level)
bin/spellcheck CorrectText -model en-spellcheck.bin -compound true < input.txt

# list suggestions per token instead of correcting (honors -verbosity)
bin/spellcheck CorrectText -model en-spellcheck.bin -suggest true -verbosity CLOSEST < input.txt
```

## Dictionary format

Whitespace-separated plain text (TAB or spaces), UTF-8 (a leading byte-order mark is tolerated):

```
word<sep>count            # unigram dictionary
word1 word2<sep>count     # bigram dictionary (for lookupCompound)
```

## Documentation & data

See the *SpellChecker* chapter of the [Apache OpenNLP manual](../../opennlp-docs) for full
details. The full English benchmark dictionaries and noisy query set used by the evaluation
tests (`opennlp-eval-tests`, `opennlp.spellcheck.eval.SpellCheckerEval`) are not committed;
they ship in the `opennlp-data` nightly archive under `spellcheck/en/`.

## Attribution

This module is a clean-room Java re-implementation of the SymSpell algorithm
(<https://github.com/wolfgarbe/SymSpell>, MIT, © Wolf Garbe). See the project `NOTICE` file.
