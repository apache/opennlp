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

import java.io.File;
import java.io.IOException;

import org.apache.uima.UIMAFramework;
import org.apache.uima.analysis_engine.AnalysisEngine;
import org.apache.uima.cas.CAS;
import org.apache.uima.pear.util.FileUtil;
import org.apache.uima.resource.ResourceInitializationException;
import org.apache.uima.resource.ResourceSpecifier;
import org.apache.uima.util.InvalidXMLException;
import org.apache.uima.util.XMLInputSource;

import org.junit.Assert;

/**
 * Test for initialization of the opennlp.uima Annotators
 */
public class AnnotatorsInitializationTest {

  private static final String PATHNAME = "src/test/resources/test-descriptors/";

  // TODO: This test requires the SourceForge models, or other models to run,
  // but they are missing due to license issues since the project was migrated to Apache
  //@Test
  public void testInitializationExecutionAndReconfigure() {
    File f = new File(PATHNAME);
    for (String descName : f.list(new FileUtil.ExtFilenameFilter("xml"))) {
      if (!descName.equals("TypeSystem.xml")) {
        try {
          AnalysisEngine ae = produceAE(descName);
          CAS cas = ae.newCAS();
          cas.setDocumentText("this is a dummy document text for initialization and reconfiguration");
          ae.process(cas);
          ae.reconfigure();
        } catch (Exception e) {
          Assert.fail(e.getLocalizedMessage() + " for desc " + descName);
        }
      }
    }
  }

  private AnalysisEngine produceAE(String descName)
      throws IOException, InvalidXMLException, ResourceInitializationException {
    File descFile = new File(PATHNAME + descName);
    XMLInputSource in = new XMLInputSource(descFile);
    ResourceSpecifier specifier = UIMAFramework.getXMLParser().parseResourceSpecifier(in);
    return UIMAFramework.produceAnalysisEngine(specifier);
  }
}
