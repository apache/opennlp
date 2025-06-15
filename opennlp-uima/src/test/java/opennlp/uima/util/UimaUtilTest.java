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

import org.apache.uima.analysis_engine.AnalysisEngine;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.cas.CAS;
import org.apache.uima.cas.Type;
import org.apache.uima.cas.text.AnnotationFS;
import org.apache.uima.cas.text.AnnotationIndex;
import org.apache.uima.resource.ResourceInitializationException;
import org.apache.uima.util.InvalidXMLException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import opennlp.tools.EnabledWhenCDNAvailable;
import opennlp.tools.models.ModelType;
import opennlp.tools.sentdetect.SentenceModel;
import opennlp.tools.util.DownloadUtil;
import opennlp.uima.AbstractUimaTest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.fail;

@EnabledWhenCDNAvailable(hostname = "dlcdn.apache.org")
class UimaUtilTest extends AbstractUimaTest {

  private static final String DOCUMENT_TEXT =
          "This is a dummy document text for initialization and reconfiguration.";

  private AnalysisEngine ae;
  
  private AnnotationFS featureAnnotation;
  private Type type;

  // SUT
  private CAS cas;

  @BeforeAll
  public static void initEnv() throws IOException {
    // ensure referenced UD models are present in download home
    DownloadUtil.downloadModel("en", ModelType.SENTENCE_DETECTOR, SentenceModel.class);
  }

  @BeforeEach
  public void setUp() {
    String descName = "SentenceDetector.xml";
    try {
      ae = produceAE(descName);
      assertNotNull(ae);
      cas = ae.newCAS();
      cas.setDocumentLanguage("en");
      cas.setDocumentText(DOCUMENT_TEXT);
      ae.process(cas);
      // type that matches the descriptors topic: sentences
      type = AnnotatorUtil.getType(cas.getTypeSystem(), "opennlp.uima.Sentence");
      featureAnnotation = cas.createAnnotation(type, 0, DOCUMENT_TEXT.length());
    } catch (IOException | InvalidXMLException | ResourceInitializationException |
             AnalysisEngineProcessException e) {
      fail(e.getLocalizedMessage() + " for desc " + descName +
              ", cause: " + e.getCause().getLocalizedMessage());
    }
  }

  @AfterEach
  public void tearDown() {
    if (ae != null) {
      ae.destroy();
    }
  }

  @Test
  void testRemoveAnnotations() {
    // prepare
    AnnotationIndex<AnnotationFS> annotationIndex = cas.getAnnotationIndex(type);
    assertNotNull(annotationIndex);
    assertEquals(1, annotationIndex.size());
    // test
    UimaUtil.removeAnnotations(cas, featureAnnotation, type);
    annotationIndex = cas.getAnnotationIndex(type);
    assertNotNull(annotationIndex);
    assertEquals(0, annotationIndex.size());
  }

  @Test
  void testRemoveAnnotationsNoAnnotationsInvalidCas() {
    assertThrows(IllegalArgumentException.class, () ->
            UimaUtil.removeAnnotations(null, featureAnnotation, type));
  }

  @Test
  void testRemoveAnnotationsNoAnnotationsInvalidType() {
    assertThrows(IllegalArgumentException.class, () ->
            UimaUtil.removeAnnotations(cas, featureAnnotation, null));
  }

}
