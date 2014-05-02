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

package opennlp.tools.chunker;

import opennlp.tools.util.SequenceValidator;

public class DummyChunkerFactory extends ChunkerFactory {

  public DummyChunkerFactory() {
  }

  @Override
  public ChunkerContextGenerator getContextGenerator() {
    return new DummyContextGenerator();
  }

  @Override
  public SequenceValidator<String> getSequenceValidator() {
    return new DummySequenceValidator();
  }

  static class DummyContextGenerator extends DefaultChunkerContextGenerator {

    @Override
    public String[] getContext(int i, String[] toks, String[] tags,
        String[] preds) {
      return super.getContext(i, toks, tags, preds);
    }
  }

  static class DummySequenceValidator extends DefaultChunkerSequenceValidator {

    @Override
    public boolean validSequence(int i, String[] sequence, String[] s,
        String outcome) {
      return super.validSequence(i, sequence, s, outcome);
    }
  }
}
