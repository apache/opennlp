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

import org.junit.Assert;
import org.junit.Test;

import java.io.File;
import java.net.URISyntaxException;
import java.util.Arrays;

public class DocumentCategorizerDLTest {

    @Test
    public void doccat() throws URISyntaxException {

        // This test was written using the nlptown/bert-base-multilingual-uncased-sentiment model.
        // You will need to update the assertions if you use a different model.

        final File model = new File(getClass().getClassLoader().getResource("doccat/model.onnx").toURI());
        final File vocab = new File(getClass().getClassLoader().getResource("doccat/vocab.txt").toURI());

        final DocumentCategorizerDL documentCategorizerDL = new DocumentCategorizerDL(model, vocab);
        final double[] result = documentCategorizerDL.categorize(new String[]{"I am happy"});

        System.out.println(Arrays.toString(result));

        final double[] expected = new double[]{0.00752239441499114, 0.0074586994014680386, 0.05470007658004761, 0.3344593346118927, 0.5958595275878906};
        Assert.assertTrue(Arrays.equals(expected, result));
        Assert.assertEquals(5, result.length);

    }

}
