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

import opennlp.tools.doccat.DocumentCategorizer;

import java.io.File;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;

/**
 * An implementation of {@link DocumentCategorizer} that performs document classification
 * using ONNX models.
 */
public class DocumentCategorizerDL implements DocumentCategorizer {

    private final File model;
    private final File vocab;
    private final Map<Integer, String> categories;

    /**
     * Creates a new document categorizer using ONNX models.
     * @param model The ONNX model file.
     * @param vocab The model's vocabulary file.
     * @param categories The categories.
     */
    public DocumentCategorizerDL(File model, File vocab, Map<Integer, String> categories) {

        this.model = model;
        this.vocab = vocab;
        this.categories = categories;

    }

    @Override
    public double[] categorize(String[] strings) {

        try {

            final DocumentCategorizerInference inference = new DocumentCategorizerInference(model, vocab);

            final double[][] v1 = inference.infer(strings[0]);

            final double[] results = inference.softmax(v1[0]);

            return results;

        } catch (Exception ex) {
            System.err.println("Unload to perform document classification inference: " + ex.getMessage());
        }

        return new double[]{};

    }

    @Override
    public double[] categorize(String[] strings, Map<String, Object> map) {
        return categorize(strings);
    }

    @Override
    public String getBestCategory(double[] doubles) {
        return categories.get(maxIndex(doubles));
    }

    @Override
    public int getIndex(String s) {
        return getKey(s);
    }

    @Override
    public String getCategory(int i) {
        return categories.get(i);
    }

    @Override
    public int getNumberOfCategories() {
       return categories.size();
    }

    @Override
    public String getAllResults(double[] doubles) {
        return null;
    }

    @Override
    public Map<String, Double> scoreMap(String[] strings) {
        return null;
    }

    @Override
    public SortedMap<Double, Set<String>> sortedScoreMap(String[] strings) {
        return null;
    }

    private int getKey(String value) {

        for (Map.Entry<Integer, String> entry : categories.entrySet()) {

            if (entry.getValue().equals(value)) {
                return entry.getKey();
            }

        }

        // The String wasn't found as a value in the map.
        return -1;

    }

    private int maxIndex(double[] arr) {

        double max = Double.NEGATIVE_INFINITY;
        int index = -1;

        for(int x = 0; x < arr.length; x++) {
            if(arr[x] > max) {
                index = x;
                max = arr[x];
            }
        }

        return index;

    }

}
