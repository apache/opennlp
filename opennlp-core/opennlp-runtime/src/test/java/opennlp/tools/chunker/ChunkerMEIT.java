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

import java.io.IOException;
import java.nio.file.Path;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import opennlp.tools.AbstractModelLoaderTest;

public class ChunkerMEIT extends AbstractModelLoaderTest {

  private static final String[] toks1 = {"Rockwell", "said", "the", "agreement", "calls", "for",
      "it", "to", "supply", "200", "additional", "so-called", "shipsets",
      "for", "the", "planes", "."};

  private static final String[] tags1 = {"NNP", "VBD", "DT", "NN", "VBZ", "IN", "PRP", "TO", "VB",
      "CD", "JJ", "JJ", "NNS", "IN", "DT", "NNS", "."};

  private static final String[] expect1 = {"B-NP", "B-VP", "B-NP", "I-NP", "B-VP", "B-SBAR",
      "B-NP", "B-VP", "I-VP", "B-NP", "I-NP", "I-NP", "I-NP", "B-PP", "B-NP",
      "I-NP", "O"};


  private static final String modelName = "en-chunker.bin";

  private static ChunkerME chunker;

  @BeforeAll
  public static void prepare() throws IOException {
    downloadVersion15Model(modelName);
    final Path modelPath = OPENNLP_DIR.resolve(modelName);
    ChunkerModel model = new ChunkerModel(modelPath);
    chunker = new ChunkerME(model);
  }

  @Test
  void testChunk() {
    String[] preds = chunker.chunk(toks1, tags1);
    Assertions.assertArrayEquals(expect1, preds);
  }

}
