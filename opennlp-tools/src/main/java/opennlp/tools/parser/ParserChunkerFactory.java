/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package opennlp.tools.parser;

import opennlp.tools.chunker.ChunkerContextGenerator;
import opennlp.tools.chunker.ChunkerFactory;
import opennlp.tools.chunker.ChunkerME;
import opennlp.tools.ml.model.MaxentModel;
import opennlp.tools.util.SequenceValidator;
import opennlp.tools.util.TokenTag;

public class ParserChunkerFactory extends ChunkerFactory {

  @Override
  public ChunkerContextGenerator getContextGenerator() {
    return new ChunkContextGenerator(ChunkerME.DEFAULT_BEAM_SIZE);
  }

  @Override
  public SequenceValidator<TokenTag> getSequenceValidator() {

    MaxentModel model = artifactProvider.getArtifact("chunker.model");

    String[] outcomes = new String[model.getNumOutcomes()];
    for (int i = 0; i < outcomes.length; i++) {
      outcomes[i] = model.getOutcome(i);
    }

    return new ParserChunkerSequenceValidator(outcomes);
  }

}
