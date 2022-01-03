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

package opennlp.dl.doccat;

import ai.onnxruntime.OnnxTensor;
import opennlp.dl.Inference;
import opennlp.dl.Tokens;

import java.io.File;
import java.nio.LongBuffer;
import java.util.HashMap;
import java.util.Map;

public class DocumentCategorizerInference extends Inference {

    private final Map<String, Integer> vocabulary;

    public DocumentCategorizerInference(File model, File vocab) throws Exception {

        super(model, vocab);

        this.vocabulary = loadVocab(vocab);

    }

    @Override
    public double[][] infer(String text) throws Exception {

        final Tokens tokens = tokenize(text);

        final Map<String, OnnxTensor> inputs = new HashMap<>();
        inputs.put("input_ids", OnnxTensor.createTensor(env, LongBuffer.wrap(tokens.getIds()), new long[]{1, tokens.getIds().length}));
        inputs.put("attention_mask", OnnxTensor.createTensor(env, LongBuffer.wrap(tokens.getMask()), new long[]{1, tokens.getMask().length}));
        inputs.put("token_type_ids", OnnxTensor.createTensor(env, LongBuffer.wrap(tokens.getTypes()), new long[]{1, tokens.getTypes().length}));

        return convertFloatsToDoubles((float[][]) session.run(inputs).get(0).getValue());

    }

}
