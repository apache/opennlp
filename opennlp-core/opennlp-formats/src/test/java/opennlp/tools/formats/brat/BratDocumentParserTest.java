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
import java.util.List;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import opennlp.tools.namefind.NameSample;
import opennlp.tools.sentdetect.NewlineSentenceDetector;
import opennlp.tools.tokenize.WhitespaceTokenizer;

public class BratDocumentParserTest extends AbstractBratTest {

  @BeforeEach
  public void setup() throws IOException {
    super.setup();
  }
  
  @Test
  void testParse() throws IOException {
    AnnotationConfiguration config = new AnnotationConfiguration(typeToClassMap);

    InputStream txtIn = getResourceStream("brat/opennlp-1193.txt");
    InputStream annIn = getResourceStream("brat/opennlp-1193.ann");

    BratDocument doc = BratDocument.parseDocument(config, "opennlp-1193", txtIn, annIn);
    Assertions.assertNotNull(doc);
    
    BratDocumentParser parser = new BratDocumentParser(new NewlineSentenceDetector(),
        WhitespaceTokenizer.INSTANCE);

    List<NameSample> names = parser.parse(doc);
    Assertions.assertEquals(3, names.size());

    NameSample sample1 = names.get(0);
    Assertions.assertNotNull(sample1);

    Assertions.assertEquals(1, sample1.getNames().length);
    Assertions.assertEquals(0, sample1.getNames()[0].getStart());
    Assertions.assertEquals(2, sample1.getNames()[0].getEnd());


    NameSample sample2 = names.get(1);
    Assertions.assertNotNull(sample2);
    Assertions.assertEquals(1, sample2.getNames().length);
    Assertions.assertEquals(0, sample2.getNames()[0].getStart());
    Assertions.assertEquals(1, sample2.getNames()[0].getEnd());

    NameSample sample3 = names.get(2);
    Assertions.assertNotNull(sample3);
    Assertions.assertEquals(3, sample3.getNames().length);
    Assertions.assertEquals(0, sample3.getNames()[0].getStart());
    Assertions.assertEquals(1, sample3.getNames()[0].getEnd());
    Assertions.assertEquals(1, sample3.getNames()[1].getStart());
    Assertions.assertEquals(2, sample3.getNames()[1].getEnd());
    Assertions.assertEquals(2, sample3.getNames()[2].getStart());
    Assertions.assertEquals(3, sample3.getNames()[2].getEnd());
  }
}
