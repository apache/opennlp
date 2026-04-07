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

package opennlp.tools.tokenize;

import java.util.List;

/**
 * English-specific tests for the {@link BPEModel} class.
 *
 * @see AbstractBPEModelTest
 * @see BPEModel
 */
public class BPEModelEnTest extends AbstractBPEModelTest {

  private static final List<String> CORPUS = List.of(
      "The quick brown fox jumps over the lazy dog, which was sleeping; it never saw the fox coming",
      "Natural language processing is fascinating: it combines linguistics, computer science, and statistics",
      "After the rain stopped, the sun came out; the children played outside, and the birds sang loudly"
  );

  @Override
  protected List<String> getCorpus() {
    return CORPUS;
  }

  @Override
  protected String getLanguageCode() {
    return "en";
  }
}
