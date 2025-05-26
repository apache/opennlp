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
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Paths;

import org.apache.uima.UIMAFramework;
import org.apache.uima.analysis_engine.AnalysisEngine;
import org.apache.uima.resource.ExternalResourceDescription;
import org.apache.uima.resource.ResourceInitializationException;
import org.apache.uima.resource.ResourceSpecifier;
import org.apache.uima.resource.metadata.ResourceManagerConfiguration;
import org.apache.uima.util.InvalidXMLException;
import org.apache.uima.util.XMLInputSource;

public abstract class AbstractUimaTest extends AbstractTest {
  
  protected AnalysisEngine produceAE(String descName)
          throws IOException, InvalidXMLException, ResourceInitializationException {
    File descFile = new File(PATH_DESCRIPTORS + "/" + descName);
    XMLInputSource in = new XMLInputSource(descFile);
    ResourceSpecifier specifier = UIMAFramework.getXMLParser().parseResourceSpecifier(in);
    adaptModelURL(specifier);
    return UIMAFramework.produceAnalysisEngine(specifier);
  }

  /*
   * Dynamically resolves the model URL for the test environment
   * and reconfigures the resource specification accordingly.
   *
   * Note:
   * In the xml test-descriptors files only stub urls exist.
   * Therefore, the actual 'url' has to be set at runtime
   * and used to compose a valid 'file' URL for the resource
   * specification object ('resourceSpec').
   */
  private void adaptModelURL(ResourceSpecifier specifier) {
    ResourceManagerConfiguration config = (ResourceManagerConfiguration)
            specifier.getAttributeValue("resourceManagerConfiguration");
    ExternalResourceDescription[] resources = config.getExternalResources();
    for (ExternalResourceDescription modelDesc : resources) {
      ResourceSpecifier resourceSpec = modelDesc.getResourceSpecifier();
      String genericValue = resourceSpec.getAttributeValue(FILE_URL).toString();
      String modelName = genericValue.split(":")[1]; // always right of 'file:' -> idx 1
      try {
        if ("dictionary.dic".equals(modelName)) {
          URL fileURL = Paths.get(TARGET_DIR, modelName).toUri().toURL();
          resourceSpec.setAttributeValue(FILE_URL, fileURL.toExternalForm());
        } else {
          URL modelURL = OPENNLP_DIR.resolve(modelName).toUri().toURL();
          resourceSpec.setAttributeValue(FILE_URL, modelURL.toExternalForm());
        }
      } catch (MalformedURLException e) {
        throw new RuntimeException(e);
      }
    }
  }
}
