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
import java.io.InputStream;

import org.apache.uima.UimaContext;
import org.apache.uima.analysis_engine.AnalysisEngine;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.cas.CAS;
import org.apache.uima.cas.Feature;
import org.apache.uima.cas.Type;
import org.apache.uima.resource.ResourceInitializationException;
import org.apache.uima.util.InvalidXMLException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import opennlp.tools.EnabledWhenCDNAvailable;
import opennlp.tools.models.ModelType;
import opennlp.tools.sentdetect.SentenceModel;
import opennlp.tools.util.DownloadUtil;
import opennlp.uima.AbstractUimaTest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.fail;

@EnabledWhenCDNAvailable(hostname = "dlcdn.apache.org")
class AnnotatorUtilTest extends AbstractUimaTest {

  private static final String DOCUMENT_TEXT =
          "This is a dummy document text for initialization and reconfiguration.";

  private AnalysisEngine ae;
  
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
      // note: no actual need to process the CAS here!
      
    } catch (IOException | InvalidXMLException | ResourceInitializationException e) {
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
  void testGetType() {
    try {
      Type t = AnnotatorUtil.getType(cas.getTypeSystem(), "opennlp.uima.Sentence");
      assertNotNull(t);
    } catch (AnalysisEngineProcessException e) {
      fail(e.getCause().getLocalizedMessage());
    }
  }

  @Test
  void testGetTypeWithInvalidTypeSystem() {
    assertThrows(IllegalArgumentException.class, () ->
            AnnotatorUtil.getType(null, "opennlp.uima.Sentence"));
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {" ", "\t", "\n"})
  void testGetTypeWithEmptyTypeName(String typeName) {
    assertThrows(OpenNlpAnnotatorProcessException.class, () ->
            AnnotatorUtil.getType(cas.getTypeSystem(), typeName));
  }

  @Test
  void testGetRequiredFeature() {
    try {
      final Type t = AnnotatorUtil.getRequiredTypeParameter(ae.getUimaContext(),
              cas.getTypeSystem(), UimaUtil.SENTENCE_TYPE_PARAMETER);
      Feature f = AnnotatorUtil.getRequiredFeature(t, "sofa");
      assertNotNull(f);
      assertEquals("sofa", f.getShortName());
    } catch (AnalysisEngineProcessException e) {
      fail(e.getLocalizedMessage());
    }
  }

  @Test
  void testGetFeatureWithInvalidType() {
    assertThrows(IllegalArgumentException.class, () ->
            AnnotatorUtil.getRequiredFeature(null, "opennlp.uima.Sentence"));
  }

  @Test
  void testGetRequiredFeatureWithInvalidFeatureName() throws AnalysisEngineProcessException {
    final Type t = AnnotatorUtil.getRequiredTypeParameter(ae.getUimaContext(),
            cas.getTypeSystem(), UimaUtil.SENTENCE_TYPE_PARAMETER);
    assertThrows(OpenNlpAnnotatorProcessException.class, () ->
            AnnotatorUtil.getRequiredFeature(t, "xyz"));
  }

  @Test
  void testGetOptionalFeatureParameter() {
    UimaContext ctx = ae.getUimaContext();
    try {
      final Type t = AnnotatorUtil.getRequiredTypeParameter(ctx, cas.getTypeSystem(),
              UimaUtil.SENTENCE_TYPE_PARAMETER);
      Feature f = AnnotatorUtil.getOptionalFeatureParameter(ctx, t,
              UimaUtil.PROBABILITY_FEATURE_PARAMETER, CAS.TYPE_NAME_DOUBLE);
      assertNotNull(f);
      assertEquals("prob", f.getShortName());
    } catch (AnalysisEngineProcessException e) {
      fail(e.getLocalizedMessage());
    }
  }

  @Test
  void testGetOptionalFeatureParameterWithInvalidFeatureName() {
    UimaContext ctx = ae.getUimaContext();
    try {
      final Type t = AnnotatorUtil.getRequiredTypeParameter(ctx, cas.getTypeSystem(),
              UimaUtil.SENTENCE_TYPE_PARAMETER);
      Feature f = AnnotatorUtil.getOptionalFeatureParameter(ctx, t,
              "xyz", CAS.TYPE_NAME_DOUBLE);
      assertNull(f);
    } catch (AnalysisEngineProcessException e) {
      fail(e.getLocalizedMessage());
    }
  }

  @Test
  void testGetOptionalBooleanParameterWithMismatchingName() {
    assertThrows(ResourceInitializationException.class, () ->
            AnnotatorUtil.getOptionalBooleanParameter(
                    ae.getUimaContext(), UimaUtil.SENTENCE_TYPE_PARAMETER));
  }

  @Test
  void testGetOptionalFloatParameterWithMismatchingName() {
    assertThrows(ResourceInitializationException.class, () ->
            AnnotatorUtil.getRequiredFloatParameter(
                    ae.getUimaContext(), UimaUtil.SENTENCE_TYPE_PARAMETER));
  }

  @Test
  void testGetOptionalIntegerParameterWithMismatchingName() {
    assertThrows(ResourceInitializationException.class, () ->
            AnnotatorUtil.getOptionalIntegerParameter(
                    ae.getUimaContext(), UimaUtil.SENTENCE_TYPE_PARAMETER));
  }
  
  @Test
  void testGetOptionalStringArrayParameterWithMismatchingName() {
    assertThrows(ResourceInitializationException.class, () ->
            AnnotatorUtil.getOptionalStringArrayParameter(
                    ae.getUimaContext(), UimaUtil.SENTENCE_TYPE_PARAMETER));
  }

  @Test
  void testGetRequiredBooleanParameterWithInvalidName() {
    assertThrows(ResourceInitializationException.class, () ->
            AnnotatorUtil.getRequiredBooleanParameter(
                    ae.getUimaContext(), "xyz"));
  }

  @Test
  void testGetRequiredFloatParameterWithInvalidName() {
    assertThrows(ResourceInitializationException.class, () ->
            AnnotatorUtil.getRequiredFloatParameter(
                    ae.getUimaContext(), "xyz"));
  }

  @Test
  void testGetRequiredIntegerParameterWithInvalidName() {
    assertThrows(ResourceInitializationException.class, () ->
            AnnotatorUtil.getRequiredIntegerParameter(
                    ae.getUimaContext(), "xyz"));
  }

  /*
   * This test won't pass as OpenNLP's resource-like classes do not implement:
   * 'org.apache.uima.resource.DataSource', conflict: ResourceManager_impl.class -> line 517
   */
  @Test
  @Disabled
  void testGetOptionalResourceAsStream() {
    try (InputStream in = AnnotatorUtil.getOptionalResourceAsStream(
            ae.getUimaContext(), "opennlp.uima.ModelName")) {
      assertNotNull(in);
    } catch (ResourceInitializationException | IOException e) {
      fail(e.getLocalizedMessage());
    }
  }
}
