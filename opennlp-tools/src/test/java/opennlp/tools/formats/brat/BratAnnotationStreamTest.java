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
import java.util.LinkedHashSet;
import java.util.Set;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import opennlp.tools.util.ObjectStream;

import static org.junit.jupiter.api.Assertions.fail;

public class BratAnnotationStreamTest extends AbstractBratTest {

  /* Expectations */
  private static final String[] VOA_PERSONS = new String[]{
      "Obama", "Barack Obama", "Lee Myung - bak"};
  private static final String[] VOA_LOCATIONS = new String[]{
      "South Korea", "North Korea", "China", "South Korean", "United States", "Pyongyang"};
  private static final String[] VOA_DATES = new String[]{
      "Wednesday", "Wednesday evening", "Thursday"};

  @BeforeEach
  public void setup() throws IOException {
    super.setup();
  }

  @Test
  void testParsingEntities() throws IOException {
    AnnotationConfiguration annConfig = new AnnotationConfiguration(typeToClassMap);
    ObjectStream<BratAnnotation> annStream = creatBratAnnotationStream(annConfig,
        "brat/voa-with-entities.ann");
    Assertions.assertNotNull(annStream);

    validateBratAnnotationStream(annStream, 5, 10, 3, 0, 2, 0);
  }

  @Test
  void testParsingRelations() throws IOException {
    // add relation type for this config
    typeToClassMap.put(BRAT_TYPE_RELATED, AnnotationConfiguration.RELATION_TYPE);

    AnnotationConfiguration annConfig = new AnnotationConfiguration(typeToClassMap);
    ObjectStream<BratAnnotation> annStream = creatBratAnnotationStream(annConfig,
        "brat/voa-with-relations.ann");
    Assertions.assertNotNull(annStream);

    validateBratAnnotationStream(annStream, 5, 10, 3, 0, 0, 7);
  }

  private ObjectStream<BratAnnotation> creatBratAnnotationStream(AnnotationConfiguration conf,
                                                                 String file) {
    return new BratAnnotationStream(conf, "testing", getResourceStream(file));
  }

  private void validateBratAnnotationStream(ObjectStream<BratAnnotation> annStream, int expectPersons,
                                            int expectLocations, int expectDates, int expectOrganizations,
                                            int expectAnnotations, int expectRelations) throws IOException {
    int dates = 0;
    int persons = 0;
    int relations = 0;
    int locations = 0;
    int annotations = 0;
    int organizations = 0;
    Set<String> annotatedDates = new LinkedHashSet<>();
    Set<String> annotatedPersons = new LinkedHashSet<>();
    Set<String> annotatedLocations = new LinkedHashSet<>();

    BratAnnotation ann;
    while ((ann = annStream.read()) != null) {
      Assertions.assertNotNull(ann);
      String type = ann.getType();
      Assertions.assertNotNull(type);

      String coveredText = null;
      RelationAnnotation rAnnotation = null;
      AnnotatorNoteAnnotation aAnnotation = null;
      if (ann instanceof SpanAnnotation sAnnotation) {
        coveredText = sAnnotation.getCoveredText();
        Assertions.assertNotNull(coveredText);
      } else if (ann instanceof RelationAnnotation) {
        rAnnotation = (RelationAnnotation) ann;
      } else if (ann instanceof AnnotatorNoteAnnotation) {
        aAnnotation = (AnnotatorNoteAnnotation) ann;
      } else {
        fail("Found object of invalid class for '" + type + "' type!");
      }
      switch (type) {
        case BRAT_TYPE_PERSON: {
          persons++;
          annotatedPersons.add(coveredText);
          break;
        } case BRAT_TYPE_LOCATION: {
          locations++;
          annotatedLocations.add(coveredText);
          break;
        } case BRAT_TYPE_DATE: {
          dates++;
          annotatedDates.add(coveredText);
          break;
        } case BRAT_TYPE_ORGANIZATION: {
          organizations++;
          break;
        } case BRAT_TYPE_RELATED: {
          relations++;
          Assertions.assertNotNull(rAnnotation);
          break;
        } case BRAT_TYPE_ANNOTATION: {
          annotations++;
          Assertions.assertNotNull(aAnnotation);
          break;
        } default: {
          fail("Found an unsupported BRAT type!");
        }
      }
    }
    Assertions.assertEquals(expectDates, dates);
    Assertions.assertEquals(expectPersons, persons);
    Assertions.assertEquals(expectLocations, locations);
    Assertions.assertEquals(expectAnnotations, annotations);
    Assertions.assertEquals(expectOrganizations, organizations);
    Assertions.assertEquals(expectRelations, relations);

    Assertions.assertArrayEquals(VOA_DATES, annotatedDates.toArray());
    Assertions.assertArrayEquals(VOA_PERSONS, annotatedPersons.toArray());
    Assertions.assertArrayEquals(VOA_LOCATIONS, annotatedLocations.toArray());
  }
}
