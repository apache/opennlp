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

package opennlp.dl.namefinder;

import java.io.File;
import java.nio.LongBuffer;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import ai.onnxruntime.OnnxTensor;

import opennlp.dl.Inference;
import opennlp.dl.Tokens;

public class TokenNameFinderInference extends Inference {

  private final boolean doLowerCase;

  public TokenNameFinderInference(File model, File vocab, boolean doLowerCase) throws Exception {

    super(model, vocab);

    this.doLowerCase = doLowerCase;

  }

  @Override
  public double[][] infer(String text) throws Exception {

    if (doLowerCase) {
      text = text.toLowerCase(Locale.ROOT);
    }

    final List<Tokens> t = tokenize(text);

    //for(final Tokens tokens : t) {

    // TODO: Need to do all in the list.
    final Tokens tokens = t.get(0);

      final Map<String, OnnxTensor> inputs = new HashMap<>();
      inputs.put(INPUT_IDS, OnnxTensor.createTensor(env,
          LongBuffer.wrap(tokens.getIds()), new long[] {1, tokens.getIds().length}));

      inputs.put(ATTENTION_MASK, OnnxTensor.createTensor(env,
          LongBuffer.wrap(tokens.getMask()), new long[] {1, tokens.getMask().length}));

      inputs.put(TOKEN_TYPE_IDS, OnnxTensor.createTensor(env,
          LongBuffer.wrap(tokens.getTypes()), new long[] {1, tokens.getTypes().length}));

      final float[][][] v = (float[][][]) session.run(inputs).get(0).getValue();

    //}

    return convertFloatsToDoubles(v[0]);

  }

}
