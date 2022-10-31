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

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

import org.junit.Test;

import opennlp.tools.util.ObjectStream;

public class BratAnnotationStreamTest {

  private ObjectStream<BratAnnotation> creatBratAnnotationStream(
      AnnotationConfiguration conf, String file) {

    InputStream in = BratAnnotationStreamTest.class.getResourceAsStream(file);
    return new BratAnnotationStream(conf, "testing", in);
  }

  static void addEntityTypes(Map<String, String> typeToClassMap) {
    typeToClassMap.put("Person", AnnotationConfiguration.ENTITY_TYPE);
    typeToClassMap.put("Location", AnnotationConfiguration.ENTITY_TYPE);
    typeToClassMap.put("Organization", AnnotationConfiguration.ENTITY_TYPE);
    typeToClassMap.put("Date", AnnotationConfiguration.ENTITY_TYPE);
  }

  @Test
  public void testParsingEntities() throws Exception {
    Map<String, String> typeToClassMap = new HashMap<>();
    addEntityTypes(typeToClassMap);

    AnnotationConfiguration annConfig = new AnnotationConfiguration(typeToClassMap);

    ObjectStream<BratAnnotation> annStream = creatBratAnnotationStream(annConfig,
        "/opennlp/tools/formats/brat/voa-with-entities.ann");

    // TODO: Test if we get the entities ... we expect!

    BratAnnotation ann;
    while ((ann = annStream.read()) != null) {
      System.out.println(ann);
    }
  }

  @Test
  public void testParsingRelations() throws Exception {
    Map<String, String> typeToClassMap = new HashMap<>();
    addEntityTypes(typeToClassMap);
    typeToClassMap.put("Related", AnnotationConfiguration.RELATION_TYPE);

    AnnotationConfiguration annConfig = new AnnotationConfiguration(typeToClassMap);

    ObjectStream<BratAnnotation> annStream = creatBratAnnotationStream(annConfig,
        "/opennlp/tools/formats/brat/voa-with-relations.ann");

    // TODO: Test if we get the entities ... we expect!

    BratAnnotation ann;
    while ((ann = annStream.read()) != null) {
      System.out.println(ann);
    }
  }
}
