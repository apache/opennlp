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
import java.nio.LongBuffer;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;

import opennlp.dl.AbstractDL;
import opennlp.dl.Tokens;
import opennlp.tools.tokenize.Tokenizer;
import opennlp.tools.tokenize.WordpieceTokenizer;

/**
 * Facilitates the generation of sentence vectors using
 * a sentence-transformers model converted to ONNX.
 */
public class SentenceVectorsDL extends AbstractDL {

  /**
   * Instantiates a {@link SentenceVectorsDL sentence detector} using ONNX models.
   *
   * @param model The file name of a sentence vectors ONNX model.
   * @param vocabulary The file name of the vocabulary file for the model.
   *
   * @throws OrtException Thrown if the {@code model} cannot be loaded.
   * @throws IOException Thrown if errors occurred loading the {@code model} or {@code vocabulary}.
   */
  public SentenceVectorsDL(final File model, final File vocabulary)
      throws OrtException, IOException {

    env = OrtEnvironment.getEnvironment();
    session = env.createSession(model.getPath(), new OrtSession.SessionOptions());
    vocab = loadVocab(new File(vocabulary.getPath()));
    tokenizer = new WordpieceTokenizer(vocab.keySet());

  }

  /**
   * Generates vectors given a sentence.
   * 
   * @param sentence The input sentence.
   *
   * @throws OrtException Thrown if an error occurs during inference.
   */
  public float[] getVectors(final String sentence) throws OrtException {

    final Tokens tokens = tokenize(sentence, tokenizer, vocab);

    final Map<String, OnnxTensor> inputs = new HashMap<>();

    inputs.put(INPUT_IDS, OnnxTensor.createTensor(env, LongBuffer.wrap(tokens.ids()),
        new long[] {1, tokens.ids().length}));

    inputs.put(ATTENTION_MASK, OnnxTensor.createTensor(env,
        LongBuffer.wrap(tokens.mask()), new long[] {1, tokens.mask().length}));

    inputs.put(TOKEN_TYPE_IDS, OnnxTensor.createTensor(env,
        LongBuffer.wrap(tokens.types()), new long[] {1, tokens.types().length}));

    final float[][][] v = (float[][][]) session.run(inputs).get(0).getValue();

    return v[0][0];

  }

  private Tokens tokenize(final String text, Tokenizer tokenizer, Map<String, Integer> vocab) {

    final String[] tokens = tokenizer.tokenize(text);

    final int[] ids = new int[tokens.length];
    final long[] mask = new long[ids.length];

    for (int x = 0; x < tokens.length; x++) {
      ids[x] = vocab.get(tokens[x]);
    }

    final long[] lids = Arrays.stream(ids).mapToLong(i -> i).toArray();

    final long[] types = new long[ids.length];
    Arrays.fill(types, 1);

    return new Tokens(tokens, lids, mask, types);

  }

}
