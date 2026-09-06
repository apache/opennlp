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

The dependency parser's unit tests are fully self-contained, but its accuracy evaluation runs against a real Universal Dependencies treebank that the user downloads. Apache OpenNLP bundles no treebank data, distributes none, and ships no models trained on it; the treebanks are used to reproduce accuracy numbers on your own machine.

## Getting a treebank

Every UD treebank lives in its own repository under `github.com/UniversalDependencies`, with its splits named `<lang_code>-ud-train.conllu`, `-dev`, and `-test`. Pass the helper a full commit SHA so later runs use the same data. This example selects the official `r2.18` commit of `UD_English-EWT`:

```
./download-ud-treebank.sh \
    UD_English-EWT \
    b7711cce01cdd4f5fcc0a8199b8a50d951b16c0c \
    /tmp/ud-ewt
```

produces `/tmp/ud-ewt/train.conllu` and `/tmp/ud-ewt/test.conllu`. Any treebank that publishes both splits works the same way.

## Running the accuracy evaluation

`UniversalDependencyParserEval` in `opennlp-eval-tests` extends `AbstractEvalTest` and trains and scores both parsers on the `UD_English` treebank of the Universal Dependencies 2.0 release under `ud20/` in `OPENNLP_DATA_DIR`, the shared `opennlp-data.zip` archive all evaluations use. It verifies the MD5 digests of the train and dev splits before training and checks the exact unlabeled and labeled attachment scores of the transition-based and the feedforward parser on the held-out dev split:

```
./mvnw test -pl opennlp-eval-tests -am -Peval-tests \
    -Dtest=UniversalDependencyParserEval -Dsurefire.failIfNoSpecifiedTests=false \
    -Dopennlp.forkCount=1 -DOPENNLP_DATA_DIR=/path/to/opennlp-data
```

A plain build needs no network access or external data; the evaluation runs only under the profile. The download helper above lets you repeat the measurement on another treebank or a newer release of the same one.

The score uses the treebank's segmentation, tokens, and part-of-speech tags. It measures dependency parsing by itself, not the errors of an upstream text pipeline. The reader retains the syntactic lines of multiword tokens and skips trees with a placeholder in the head or relation column. The arc-standard trainer skips non-projective trees because that transition system cannot derive them.

## Licensing

Each treebank carries its own license, stated in its repository README, and downloading one means accepting those terms yourself. The annotations of `UD_English-EWT`, for example, are licensed under CC BY-SA 4.0. The project's handling: treebanks are benchmark inputs on the user's machine only; no treebank data enters the source tree or any release artifact, and the project publishes no models trained on share-alike data. If you train and distribute your own model from a treebank, checking that treebank's terms is your responsibility.
