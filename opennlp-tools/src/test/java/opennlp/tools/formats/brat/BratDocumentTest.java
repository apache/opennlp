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
import java.util.HashMap;
import java.util.Map;

import org.junit.Assert;
import org.junit.Test;

public class BratDocumentTest {

  @Test
  public void testDocumentWithEntitiesParsing() throws IOException {

    Map<String, String> typeToClassMap = new HashMap<>();
    BratAnnotationStreamTest.addEntityTypes(typeToClassMap);
    AnnotationConfiguration config = new AnnotationConfiguration(typeToClassMap);

    InputStream txtIn = BratDocumentTest.class.getResourceAsStream(
        "/opennlp/tools/formats/brat/voa-with-entities.txt");

    InputStream annIn = BratDocumentTest.class.getResourceAsStream(
        "/opennlp/tools/formats/brat/voa-with-entities.ann");

    BratDocument doc = BratDocument.parseDocument(config, "voa-with-entities", txtIn, annIn);

    Assert.assertEquals("voa-with-entities", doc.getId());
    Assert.assertTrue(doc.getText().startsWith(" U . S .  President "));
    Assert.assertTrue(doc.getText().endsWith("multinational process . \n"));

    Assert.assertEquals(18, doc.getAnnotations().size());
    
    BratAnnotation annotation = doc.getAnnotation("T2");
    checkNote(annotation, "Barack Obama", "President Obama was the 44th U.S. president");
    annotation = doc.getAnnotation("T3");
    checkNote(annotation,"South Korea","The capital of South Korea is Seoul");
  }
  
  private void checkNote(BratAnnotation annotation, String expectedCoveredText, String expectedNote) {
    Assert.assertTrue(annotation instanceof SpanAnnotation);
    SpanAnnotation spanAnn = (SpanAnnotation) annotation;
    Assert.assertEquals(expectedCoveredText, spanAnn.getCoveredText());
    Assert.assertEquals(expectedNote, spanAnn.getNote());
  }

  /**
   * Parse spans that have multiple fragments and ensure they are matched to the correct tokens.
   *
   * Test to ensure OPENNLP-1193 works.
   */
  @Test
  public void testSpanWithMultiFragments() throws IOException {
    Map<String, String> typeToClassMap = new HashMap<>();
    BratAnnotationStreamTest.addEntityTypes(typeToClassMap);
    AnnotationConfiguration config = new AnnotationConfiguration(typeToClassMap);

    InputStream txtIn = BratDocumentTest.class.getResourceAsStream(
        "/opennlp/tools/formats/brat/opennlp-1193.txt");

    InputStream annIn = BratDocumentTest.class.getResourceAsStream(
        "/opennlp/tools/formats/brat/opennlp-1193.ann");

    BratDocument doc = BratDocument.parseDocument(config, "opennlp-1193", txtIn, annIn);

    SpanAnnotation t1 = (SpanAnnotation) doc.getAnnotation("T1");
    Assert.assertEquals(t1.getSpans()[0].getStart(), 0);
    Assert.assertEquals(t1.getSpans()[0].getEnd(), 7);
    Assert.assertEquals(t1.getSpans()[1].getStart(), 8);
    Assert.assertEquals(t1.getSpans()[1].getEnd(), 15);
    Assert.assertEquals(t1.getSpans()[2].getStart(), 17);
    Assert.assertEquals(t1.getSpans()[2].getEnd(), 24);

    SpanAnnotation t2 = (SpanAnnotation) doc.getAnnotation("T2");
    Assert.assertEquals(t2.getSpans()[0].getStart(), 26);
    Assert.assertEquals(t2.getSpans()[0].getEnd(), 33);
    Assert.assertEquals(t2.getSpans()[1].getStart(), 40);
    Assert.assertEquals(t2.getSpans()[1].getEnd(), 47);
  }
}
