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

package opennlp.uima.dictionary;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import org.apache.uima.UIMAFramework;
import org.apache.uima.analysis_engine.AnalysisEngine;
import org.apache.uima.cas.CAS;
import org.apache.uima.cas.FSIterator;
import org.apache.uima.cas.Type;
import org.apache.uima.cas.text.AnnotationFS;
import org.apache.uima.resource.ResourceInitializationException;
import org.apache.uima.resource.ResourceSpecifier;
import org.apache.uima.util.InvalidXMLException;
import org.apache.uima.util.XMLInputSource;

import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import opennlp.tools.util.StringList;
import opennlp.uima.util.CasUtil;

public class DictionaryResourceTest {

  private static final String PATHNAME = "src/test/resources/test-descriptors/";

  private static AnalysisEngine AE;

  @BeforeClass
  public static void beforeClass() throws Exception {
    AE = produceAE("DictionaryNameFinder.xml");
  }

  @AfterClass
  public static void afterClass() {
    AE.destroy(); // is this necessary?
  }

  private static AnalysisEngine produceAE(String descName)
      throws IOException, InvalidXMLException, ResourceInitializationException {
    File descFile = new File(PATHNAME + descName);
    XMLInputSource in = new XMLInputSource(descFile);
    ResourceSpecifier specifier = UIMAFramework.getXMLParser()
        .parseResourceSpecifier(in);
    return UIMAFramework.produceAnalysisEngine(specifier);
  }

  @Test
  public void testDictionaryWasLoaded() {

    try {
      DictionaryResource dic = (DictionaryResource) AE.getResourceManager()
          .getResource("/opennlp.uima.Dictionary");
      // simple check if ordering always is the same...
      Assert.assertEquals(
          "[[Berlin], [Stockholm], [New,York], [London], [Copenhagen], [Paris]]",
          dic.getDictionary().toString());
      // else we can do a simple test like this
      Assert.assertEquals("There should be six entries in the dictionary", 6,
          dic.getDictionary().asStringSet().size());
      Assert.assertTrue("London should be in the dictionary",
          dic.getDictionary().contains(new StringList("London")));
    } catch (Exception e) {
      Assert.fail("Dictionary was not loaded.");
    }

  }

  @Test
  public void testDictionaryNameFinder() {

    Set<String> expectedLocations = new HashSet<>();
    Collections.addAll(expectedLocations, "London", "Stockholm", "Copenhagen",
        "New York");

    try {
      CAS cas = AE.newCAS();
      CasUtil.deserializeXmiCAS(cas, DictionaryResourceTest.class
          .getResourceAsStream("/cas/dictionary-test.xmi"));
      AE.process(cas);
      Type locationType = cas.getTypeSystem().getType("opennlp.uima.Location");
      FSIterator<AnnotationFS> locationIterator = cas
          .getAnnotationIndex(locationType).iterator();

      while (locationIterator.isValid()) {
        AnnotationFS annotationFS = locationIterator.get();
        Assert.assertTrue(expectedLocations.contains(annotationFS.getCoveredText()));
        expectedLocations.remove(annotationFS.getCoveredText());
        locationIterator.moveToNext();
      }
      Assert.assertEquals(0, expectedLocations.size());
    } catch (Exception e) {
      e.printStackTrace();
      Assert.fail(e.getLocalizedMessage());
    }

  }

}
