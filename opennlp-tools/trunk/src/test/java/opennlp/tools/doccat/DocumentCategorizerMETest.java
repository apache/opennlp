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

package opennlp.tools.doccat;

import static org.junit.Assert.assertEquals;

import java.io.IOException;

import opennlp.tools.util.ObjectStream;
import opennlp.tools.util.ObjectStreamUtils;

import org.junit.Test;

public class DocumentCategorizerMETest {

  @Test
  public void testSimpleTraining() throws IOException {
   
    ObjectStream<DocumentSample> samples = ObjectStreamUtils.createObjectStream(new DocumentSample[]{
        new DocumentSample("1", new String[]{"a", "b", "c"}),
        new DocumentSample("1", new String[]{"a", "b", "c", "1", "2"}),
        new DocumentSample("1", new String[]{"a", "b", "c", "3", "4"}),
        new DocumentSample("0", new String[]{"x", "y", "z"}),
        new DocumentSample("0", new String[]{"x", "y", "z", "5", "6"}),
        new DocumentSample("0", new String[]{"x", "y", "z", "7", "8"})
    });
    
    DoccatModel model = DocumentCategorizerME.train("x-unspecified", samples,
        0, 100, new BagOfWordsFeatureGenerator());
    
    DocumentCategorizer doccat = new DocumentCategorizerME(model);
    
    double aProbs[] = doccat.categorize("a");
    assertEquals("1", doccat.getBestCategory(aProbs));
    
    double bProbs[] = doccat.categorize("x");
    assertEquals("0", doccat.getBestCategory(bProbs));
  }
}
