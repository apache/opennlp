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

package opennlp.uima.util;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.apache.uima.cas.CAS;
import org.apache.uima.cas.text.AnnotationFS;
import org.apache.uima.resource.metadata.TypeSystemDescription;

import org.junit.Assert;
import org.junit.Test;

public class AnnotationComboIteratorTest {

  /**
   * Tests ensures that the bug observed in OPENNLP 676 is fixed. The described
   * bug occurs if there are tokens which are out side of the sentence bounds.
   * In that case an uncommon code path in the iterator is used to skip the
   * out-of-sentence tokens until it again finds tokens which are inside a sentence.
   * <p>
   * The iterator was either crashing with a NoSuchElementException or it just left
   * out the first token in the next sentence.
   *
   * @throws IOException
   */
  @Test
  public void OPENNLP_676() throws IOException {
    TypeSystemDescription ts = CasUtil
        .createTypeSystemDescription(AnnotationComboIteratorTest.class
            .getResourceAsStream("/test-descriptors/TypeSystem.xml"));

    CAS cas = CasUtil.createEmptyCAS(ts);

    CasUtil.deserializeXmiCAS(cas, AnnotationComboIteratorTest.class
        .getResourceAsStream("/cas/OPENNLP-676.xmi"));

    AnnotationComboIterator comboIterator = new AnnotationComboIterator(cas,
        cas.getTypeSystem().getType("opennlp.uima.Sentence"), cas
            .getTypeSystem().getType("opennlp.uima.Token"));

    List<List<String>> tokensBySentence = new ArrayList<>();

    for (AnnotationIteratorPair annotationIteratorPair : comboIterator) {

      final List<String> tokens = new ArrayList<>();

      for (AnnotationFS tokenAnnotation : annotationIteratorPair
          .getSubIterator()) {
        tokens.add(tokenAnnotation.getCoveredText());
      }

      tokensBySentence.add(tokens);
    }

    Assert.assertEquals(Collections.singletonList("A"), tokensBySentence.get(0));
    Assert.assertEquals(Arrays.asList("H", "I"), tokensBySentence.get(1));
  }

}
