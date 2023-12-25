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

package opennlp.tools.formats.brat;

import java.io.IOException;
import java.io.InputStream;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class BratDocumentTest extends AbstractBratTest {

  @BeforeEach
  public void setup() throws IOException {
    super.setup();
  }

  @Test
  void testDocumentWithEntitiesParsing() throws IOException {

    AnnotationConfiguration config = new AnnotationConfiguration(typeToClassMap);

    InputStream txtIn = getResourceStream("brat/voa-with-entities.txt");
    InputStream annIn = getResourceStream("brat/voa-with-entities.ann");

    BratDocument doc = BratDocument.parseDocument(config, "voa-with-entities", txtIn, annIn);

    Assertions.assertEquals("voa-with-entities", doc.getId());
    Assertions.assertTrue(doc.getText().startsWith(" U . S .  President "));
    Assertions.assertTrue(doc.getText().endsWith("multinational process . \n"));

    Assertions.assertEquals(18, doc.getAnnotations().size());

    BratAnnotation annotation = doc.getAnnotation("T2");
    checkNote(annotation, "Barack Obama", "President Obama was the 44th U.S. president");
    annotation = doc.getAnnotation("T3");
    checkNote(annotation, "South Korea", "The capital of South Korea is Seoul");
  }

  private void checkNote(BratAnnotation annotation, String expectedCoveredText, String expectedNote) {
    Assertions.assertInstanceOf(SpanAnnotation.class, annotation);
    SpanAnnotation spanAnn = (SpanAnnotation) annotation;
    Assertions.assertEquals(expectedCoveredText, spanAnn.getCoveredText());
    Assertions.assertEquals(expectedNote, spanAnn.getNote());
  }

  /**
   * Parse spans that have multiple fragments and ensure they are matched to the correct tokens.
   * <p>
   * Test to ensure OPENNLP-1193 works.
   */
  @Test
  void testSpanWithMultiFragments() throws IOException {
    AnnotationConfiguration config = new AnnotationConfiguration(typeToClassMap);

    InputStream txtIn = getResourceStream("brat/opennlp-1193.txt");
    InputStream annIn = getResourceStream("brat/opennlp-1193.ann");

    BratDocument doc = BratDocument.parseDocument(config, "opennlp-1193", txtIn, annIn);

    SpanAnnotation t1 = (SpanAnnotation) doc.getAnnotation("T1");
    Assertions.assertEquals(t1.getSpans()[0].getStart(), 0);
    Assertions.assertEquals(t1.getSpans()[0].getEnd(), 7);
    Assertions.assertEquals(t1.getSpans()[1].getStart(), 8);
    Assertions.assertEquals(t1.getSpans()[1].getEnd(), 15);
    Assertions.assertEquals(t1.getSpans()[2].getStart(), 17);
    Assertions.assertEquals(t1.getSpans()[2].getEnd(), 24);

    SpanAnnotation t2 = (SpanAnnotation) doc.getAnnotation("T2");
    Assertions.assertEquals(t2.getSpans()[0].getStart(), 26);
    Assertions.assertEquals(t2.getSpans()[0].getEnd(), 33);
    Assertions.assertEquals(t2.getSpans()[1].getStart(), 40);
    Assertions.assertEquals(t2.getSpans()[1].getEnd(), 47);
  }
}
