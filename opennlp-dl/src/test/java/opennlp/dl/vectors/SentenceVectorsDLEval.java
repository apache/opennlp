/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package opennlp.dl.vectors;

import java.io.File;
import java.io.IOException;

import ai.onnxruntime.OrtException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import opennlp.dl.AbstractDLTest;

public class SentenceVectorsDLEval extends AbstractDLTest {

  @Test
  public void generateVectorsTest() throws IOException, OrtException {

    final File MODEL_FILE_NAME = new File(getOpennlpDataDir(), "onnx/sentence-transformers/model.onnx");
    final File VOCAB_FILE_NAME = new File(getOpennlpDataDir(), "onnx/sentence-transformers/vocab.txt");

    final String sentence = "george washington was president";

    final SentenceVectorsDL sv = new SentenceVectorsDL(MODEL_FILE_NAME, VOCAB_FILE_NAME);

    final float[] vectors = sv.getVectors(sentence);

    Assertions.assertEquals(vectors[0], 0.39994872, 0.00001);
    Assertions.assertEquals(vectors[1], -0.055101186, 0.00001);
    Assertions.assertEquals(vectors[2], 0.2817594, 0.00001);
    Assertions.assertEquals(vectors.length, 384);

  }

}
