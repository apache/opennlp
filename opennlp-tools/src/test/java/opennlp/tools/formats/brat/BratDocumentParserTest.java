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
import java.util.List;
import java.util.Map;

import org.junit.Assert;
import org.junit.Test;

import opennlp.tools.namefind.NameSample;
import opennlp.tools.sentdetect.NewlineSentenceDetector;
import opennlp.tools.tokenize.WhitespaceTokenizer;

public class BratDocumentParserTest {

  @Test
  public void testParse() throws IOException {

    Map<String, String> typeToClassMap = new HashMap<>();
    BratAnnotationStreamTest.addEntityTypes(typeToClassMap);
    AnnotationConfiguration config = new AnnotationConfiguration(typeToClassMap);

    InputStream txtIn = BratDocumentTest.class.getResourceAsStream(
        "/opennlp/tools/formats/brat/opennlp-1193.txt");

    InputStream annIn = BratDocumentTest.class.getResourceAsStream(
        "/opennlp/tools/formats/brat/opennlp-1193.ann");

    BratDocument doc = BratDocument.parseDocument(config, "opennlp-1193", txtIn, annIn);

    BratDocumentParser parser = new BratDocumentParser(new NewlineSentenceDetector(),
        WhitespaceTokenizer.INSTANCE);

    List<NameSample> names = parser.parse(doc);

    Assert.assertEquals(3, names.size());

    NameSample sample1 = names.get(0);

    Assert.assertEquals(1, sample1.getNames().length);
    Assert.assertEquals(0, sample1.getNames()[0].getStart());
    Assert.assertEquals(2, sample1.getNames()[0].getEnd());


    NameSample sample2 = names.get(1);
    Assert.assertEquals(1, sample2.getNames().length);
    Assert.assertEquals(0, sample2.getNames()[0].getStart());
    Assert.assertEquals(1, sample2.getNames()[0].getEnd());

    NameSample sample3 = names.get(2);
    Assert.assertEquals(3, sample3.getNames().length);
    Assert.assertEquals(0, sample3.getNames()[0].getStart());
    Assert.assertEquals(1, sample3.getNames()[0].getEnd());
    Assert.assertEquals(1, sample3.getNames()[1].getStart());
    Assert.assertEquals(2, sample3.getNames()[1].getEnd());
    Assert.assertEquals(2, sample3.getNames()[2].getStart());
    Assert.assertEquals(3, sample3.getNames()[2].getEnd());
  }
}
