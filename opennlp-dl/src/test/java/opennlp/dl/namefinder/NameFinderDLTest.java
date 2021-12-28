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

import org.junit.Test;

import java.io.File;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.Map;

public class NameFinderDLTest {

    @Test
    public void ner() throws Exception {

        final File model = new File(getClass().getClassLoader().getResource("namefinder/model.onnx").toURI());
        final File vocab = new File(getClass().getClassLoader().getResource("namefinder/vocab.txt").toURI());

        final Map<Integer, String> ids2Labels = new HashMap<>();
        ids2Labels.put(0, "O");
        ids2Labels.put(1, "B-MISC");
        ids2Labels.put(2, "I-MISC");
        ids2Labels.put(3, "B-PER");
        ids2Labels.put(4, "I-PER");
        ids2Labels.put(5, "B-ORG");
        ids2Labels.put(6, "I-ORG");
        ids2Labels.put(7, "B-LOC");
        ids2Labels.put(8, "I-LOC");

        final String[] tokens = new String[]{"George", "Washington", "was", "president", "of", "the", "United", "States"};

        final NameFinderDL nameFinderDL = new NameFinderDL(model, vocab, ids2Labels);
        nameFinderDL.find(tokens);

    }

}
