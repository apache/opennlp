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

package opennlp.dl;

import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;
import opennlp.tools.tokenize.Tokenizer;
import opennlp.tools.tokenize.WordpieceTokenizer;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public abstract class Inference {

    protected final OrtEnvironment env;
    protected final OrtSession session;

    private final Tokenizer tokenizer;
    private final Map<String, Integer> vocabulary;

    public abstract double[][] infer(String input) throws Exception;

    public Inference(File model, File vocab) throws OrtException, IOException {

        this.env = OrtEnvironment.getEnvironment();
        this.session = env.createSession(model.getPath(), new OrtSession.SessionOptions());
        this.vocabulary = loadVocab(vocab);
        this.tokenizer = new WordpieceTokenizer(vocabulary.keySet());

    }

    public Tokens tokenize(String text) {

        final String[] tokens = tokenizer.tokenize(text);

        final int[] ids = new int[tokens.length];

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

    public Map<String, Integer> loadVocab(File vocab) throws IOException {

        final Map<String, Integer> v = new HashMap<>();

        BufferedReader br = new BufferedReader(new FileReader(vocab.getPath()));
        String line = br.readLine();
        int x = 0;

        while(line != null) {

            line = br.readLine();
            x++;

            v.put(line, x);

        }

        return v;

    }

    public double[] softmax(final double[] input) {

        final double[] t = new double[input.length];
        double sum = 0.0;

        for (int x = 0; x < input.length; x++) {
            double val = Math.exp(input[x]);
            sum += val;
            t[x] = val;
        }

        final double[] output = new double[input.length];

        for (int x = 0; x < output.length; x++) {
            output[x] = (float) (t[x] / sum);
        }

        return output;

    }

    public double[][] convertFloatsToDoubles(float[][] inputs) {

        final double[][] outputs = new double[inputs.length][inputs[0].length];

        for(int i = 0; i < inputs.length; i++) {
            for(int j = 0; j < inputs[0].length; j++) {
                outputs[i][j] = (double) inputs[i][j];
            }
        }

        return outputs;

    }

    public double[] convertFloatsToDoubles(float[] input) {

        final double[] output = new double[input.length];

        for (int i = 0; i < input.length; i++) {
            output[i] = input[i];
        }

        return output;

    }

}
