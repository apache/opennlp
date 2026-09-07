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

# Universal Dependencies treebanks for the dependency parser evaluation

The dependency parser's unit tests are fully self-contained, but its accuracy evaluation runs against real Universal Dependencies treebanks that the user provides. Apache OpenNLP bundles no treebank data, distributes none, and ships no models trained on it; the treebanks are used to reproduce accuracy numbers on your own machine.

## Getting a treebank

Every UD treebank lives in its own repository under `github.com/UniversalDependencies`, with its splits named `<lang_code>-ud-train.conllu`, `-dev`, and `-test`. Pass the helper a full commit SHA so later runs use the same data. This example selects the official `r2.18` commit of `UD_English-EWT`:

```
./download-ud-treebank.sh \
    UD_English-EWT \
    b7711cce01cdd4f5fcc0a8199b8a50d951b16c0c \
    /tmp/ud-ewt
```

produces `/tmp/ud-ewt/train.conllu` and `/tmp/ud-ewt/test.conllu`. Any treebank that publishes both splits works the same way. The helper is for experiments of your own with the training and evaluation API; the pinned evaluation below reads the Universal Dependencies 2.0 release layout of the shared evaluation data instead.

## Running the accuracy evaluation

`UniversalDependencyParserEval` in `opennlp-eval-tests` extends `AbstractEvalTest` and reads the Universal Dependencies 2.0 treebanks under `ud20/` in `OPENNLP_DATA_DIR`, the shared `opennlp-data.zip` archive all evaluations use. It verifies the MD5 digest of every split it reads, trains each parser from scratch on a training split, and asserts the exact scores on sentences the parser has not seen:

| Test | Parser | Treebank | Held-out data |
|---|---|---|---|
| `crossValidateTransitionParserEnglish` | transition | `UD_English` | 5-fold cross validation of the training split |
| `trainAndEvalTransitionParserEnglish` | transition | `UD_English` | development split |
| `trainAndEvalTransitionParserGerman` | transition | `UD_German` | development split |
| `trainAndEvalTransitionParserSpanishAncora` | transition | `UD_Spanish-AnCora` | development split |
| `trainAndEvalTransitionParserFrench` | transition | `UD_French` | development split |
| `trainAndEvalFeedforwardParserEnglish` | feedforward | `UD_English` | development split |
| `trainAndEvalFeedforwardParserSpanishAncora` | feedforward | `UD_Spanish-AnCora` | development split |

The transition parser is the maximum-entropy arc-standard parser trained with a feature cutoff of 5; the feedforward parser is trained with `FeedforwardDependencyTrainer.Settings.defaults()` and decodes greedily. Each test pins four numbers: the unlabeled attachment score (UAS, the fraction of tokens with the correct head), the labeled attachment score (LAS, the fraction with the correct head and relation label), and both again over the tokens not tagged `PUNCT`, the customary reporting convention for Universal Dependencies. The cross validation trains five parsers, each on four fifths of the English training split, and scores each on the remaining fifth, so every training sentence is scored once by a parser that did not see it. The pinned values, rounded to the four decimals the tests assert with `ACCURACY_DELTA`; the token column counts every scored token, and the two rightmost columns leave out the tokens tagged `PUNCT`:

| Test | Tokens | UAS | LAS | UAS no punct | LAS no punct |
|---|---|---|---|---|---|
| `crossValidateTransitionParserEnglish` | 204,585 | 0.8205 | 0.7885 | 0.8422 | 0.8065 |
| `trainAndEvalTransitionParserEnglish` | 25,148 | 0.8182 | 0.7861 | 0.8372 | 0.8015 |
| `trainAndEvalTransitionParserGerman` | 12,348 | 0.7843 | 0.7306 | 0.8030 | 0.7414 |
| `trainAndEvalTransitionParserSpanishAncora` | 52,336 | 0.8355 | 0.7914 | 0.8599 | 0.8099 |
| `trainAndEvalTransitionParserFrench` | 35,766 | 0.8501 | 0.8192 | 0.8795 | 0.8450 |
| `trainAndEvalFeedforwardParserEnglish` | 25,148 | 0.8413 | 0.8174 | 0.8540 | 0.8273 |
| `trainAndEvalFeedforwardParserSpanishAncora` | 52,336 | 0.8617 | 0.8279 | 0.8780 | 0.8396 |

```
./mvnw test -pl opennlp-eval-tests -am -Peval-tests \
    -Dtest=UniversalDependencyParserEval -Dsurefire.failIfNoSpecifiedTests=false \
    -Dopennlp.forkCount=1 -DOPENNLP_DATA_DIR=/path/to/opennlp-data
```

A plain build needs no network access or external data; the evaluation runs only under the profile. The whole class takes about 46 minutes on one core: the four transition-parser development runs take one and a half to three minutes each, the five-fold cross validation about six minutes, the feedforward run on English about eleven minutes and on Spanish about 22 minutes.

The scores use the treebank's segmentation, tokens, and universal part-of-speech tags. They measure dependency parsing by itself, not the errors of an upstream text pipeline. The reader retains the syntactic lines of multiword tokens and skips trees with a placeholder in the head or relation column. The arc-standard trainers skip non-projective trees because that transition system cannot derive them.

## Licensing

Each treebank carries its own license, stated in its repository README, and downloading one means accepting those terms yourself. The annotations of `UD_English-EWT`, for example, are licensed under CC BY-SA 4.0. The project's handling: treebanks are benchmark inputs on the user's machine only; no treebank data enters the source tree or any release artifact, and the project publishes no models trained on share-alike data. If you train and distribute your own model from a treebank, checking that treebank's terms is your responsibility.
