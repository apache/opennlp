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

import opennlp.tools.namefind.TokenNameFinder;
import opennlp.tools.util.Span;

import java.io.File;
import java.util.Map;

public class NameFinderDL implements TokenNameFinder {

    private final TokenNameFinderInference inference;

    public NameFinderDL(File model, File vocab, boolean doLowerCase, Map<Integer, String> ids2Labels) throws Exception {

        inference = new TokenNameFinderInference(model, vocab, doLowerCase, ids2Labels);

    }

    @Override
    public Span[] find(String[] tokens) {

        try {

            final double[][] v1 = inference.infer(String.join(" ", tokens));

            // TODO: Convert the results to Spans.

        } catch (Exception ex) {
            System.err.println(ex.getMessage());
        }

        System.out.println("Returning null");
        return null;

    }

    @Override
    public void clearAdaptiveData() {
        // No use for this here.
    }

}
