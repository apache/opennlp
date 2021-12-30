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

package opennlp.dl.namefinder;

import ai.onnxruntime.OnnxTensor;
import com.robrua.nlp.bert.FullTokenizer;
import com.robrua.nlp.bert.WordpieceTokenizer;
import opennlp.dl.Inference;
import opennlp.dl.Tokens;

import java.io.File;
import java.nio.LongBuffer;
import java.util.*;

public class TokenNameFinderInference extends Inference {

    private final Map<Integer, String> classes;
    private final WordpieceTokenizer tokenizer;
    private final Map<String, Integer> vocabulary;

    public TokenNameFinderInference(File model, File vocab, boolean doLowerCase, Map<Integer, String> classes) throws Exception {

        super(model, vocab);

        this.classes = classes;
        this.vocabulary = loadVocab(vocab);
        this.tokenizer = new WordpieceTokenizer(vocabulary);

    }

    @Override
    public double[][] infer(String text) throws Exception {

        final Tokens tokens = tokenize(text);

        final Map<String, OnnxTensor> inputs = new HashMap<>();
        inputs.put("input_ids", OnnxTensor.createTensor(env, LongBuffer.wrap(tokens.getIds()), new long[]{1, tokens.getIds().length}));
        inputs.put("attention_mask", OnnxTensor.createTensor(env, LongBuffer.wrap(tokens.getMask()), new long[]{1, tokens.getMask().length}));
        inputs.put("token_type_ids", OnnxTensor.createTensor(env, LongBuffer.wrap(tokens.getTypes()), new long[]{1, tokens.getTypes().length}));

        final float[][][] v = (float[][][]) session.run(inputs).get(0).getValue();
        //final double[][] d = convertFloatsToDoubles(v);

        System.out.println(v.length);
        System.out.println(v[0].length);

        System.out.println("--------");

        for(int x = 0; x < v[0].length; x++) {
            System.out.println(Arrays.toString(v[0][x]));

            float[] arr = v[0][x];
            int max = max(arr);
            System.out.println(classes.get(max));

        }

        System.out.println("--------");

        return null;
       // return convertFloatsToDoubles(v);

    }

    public Tokens tokenize(String text) {

        final List<String> tokensList = new ArrayList<>();

        tokensList.add("[CLS]");
        tokensList.addAll(Arrays.asList(tokenizer.tokenize(text)));
        tokensList.add("[SEP]");

        //tokensList.stream().forEach(System.out::println);

        final String[] tokens = tokensList.toArray(new String[0]);

        final int[] ids = new int[tokens.length];//tokenizer.convert(tokens);

        for(int x = 0; x < tokens.length; x++) {
            ids[x] = vocabulary.get(tokens[x]);
        }

        final long[] lids = Arrays.stream(ids).mapToLong(i -> i).toArray();

        final long[] mask = new long[ids.length];
        Arrays.fill(mask, 1);

        final long[] types = new long[ids.length];
        Arrays.fill(types, 0);

        return new Tokens(tokens, lids, mask, types);

    }

}
