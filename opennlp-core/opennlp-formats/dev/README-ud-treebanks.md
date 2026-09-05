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

## Running the gated evaluation

`ConlluDependencyParserEvalTest` is disabled unless the `opennlp.depparse.ud.dir` system property points at a directory containing `train.conllu` and `test.conllu`:

```
./mvnw -pl opennlp-core/opennlp-formats -am test \
    -Dtest=ConlluDependencyParserEvalTest \
    -Dopennlp.forkCount=1 \
    -Dopennlp.depparse.ud.dir=/tmp/ud-ewt
```

Without the property the test reports as skipped, which is why a plain build never needs network access or external data.

The score uses the treebank's sentence boundaries, tokens, and part-of-speech tags. It measures dependency parsing rather than the errors of an upstream text pipeline. The reader retains the syntactic rows of multiword tokens. The arc-standard trainer skips non-projective trees because that transition system cannot derive them.

## Licensing

Each treebank carries its own license, stated in its repository README, and downloading one means accepting those terms yourself. The annotations of `UD_English-EWT`, for example, are licensed under CC BY-SA 4.0. The project's handling: treebanks are benchmark inputs on the user's machine only; no treebank data enters the source tree or any release artifact, and the project publishes no models trained on share-alike data. If you train and distribute your own model from a treebank, checking that treebank's terms is your responsibility.
