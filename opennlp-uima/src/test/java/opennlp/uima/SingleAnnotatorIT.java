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
package opennlp.uima;

import java.io.IOException;

import org.apache.uima.analysis_engine.AnalysisEngine;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.cas.CAS;
import org.apache.uima.resource.ResourceConfigurationException;
import org.apache.uima.resource.ResourceInitializationException;
import org.apache.uima.util.InvalidXMLException;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import opennlp.tools.EnabledWhenCDNAvailable;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Test for initialization of the opennlp.uima Annotators classes.
 */
@EnabledWhenCDNAvailable(hostname = "opennlp.sourceforge.net")
public class SingleAnnotatorIT extends AbstractIT {

  private static final String DOCUMENT_TEXT =
          "This is a dummy document text for initialization and reconfiguration.";

  @ParameterizedTest
  @ValueSource(strings = {
      "Chunker.xml", "DateNameFinder.xml", "DictionaryNameFinder.xml",
      "LocationNameFinder.xml", "MoneyNameFinder.xml", "OrganizationNameFinder.xml",
      "Parser.xml", "PercentageNameFinder.xml", "PersonNameFinder.xml", "PosTagger.xml",
      "SentenceDetector.xml", "SimpleTokenizer.xml", "Tokenizer.xml", "TimeNameFinder.xml",
      "WhitespaceTokenizer.xml"
  })
  public void testInitializationExecutionAndReconfigure(String descName) {
    AnalysisEngine ae = null;
    try {
      ae = produceAE(descName);
      assertNotNull(ae);
      CAS cas = ae.newCAS();
      cas.setDocumentLanguage("en");
      cas.setDocumentText(DOCUMENT_TEXT);
      ae.process(cas);
      ae.reconfigure();
      /*
      CasIterator casIterator = ae.processAndOutputNewCASes(cas);
      while (casIterator.hasNext()) {
        CAS outCas = casIterator.next();

        //dump the document text and annotations for this segment
        System.out.println("********* NEW SEGMENT *********");
        System.out.println(outCas.getDocumentText());
        printAnnotations(outCas, System.out);
        //release the CAS (important)
        outCas.release();
      }
       */
    } catch (IOException | InvalidXMLException | AnalysisEngineProcessException |
             ResourceConfigurationException | ResourceInitializationException e) {
      fail(e.getLocalizedMessage() + " for desc " + descName +
              ", cause: " + e.getCause().getLocalizedMessage());
    } finally {
      if (ae != null) {
        ae.destroy();
      }
    }
  }
}
