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

package opennlp.tools.util.model;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Map;

import opennlp.tools.ml.BeamSearch;
import opennlp.tools.postag.POSModel;
import opennlp.tools.util.Version;

public class POSModelSerializer implements ArtifactSerializer<POSModel> {

  public POSModel create(InputStream in) throws IOException {
    POSModel posModel = new POSModel(new UncloseableInputStream(in));

    // The 1.6.x models write the non-default beam size into the model itself.
    // In 1.5.x the parser configured the beam size when the model was loaded,
    // this is not possible anymore with the new APIs
    Version version = posModel.getVersion();
    if (version.getMajor() == 1 && version.getMinor() == 5) {
      if (posModel.getManifestProperty(BeamSearch.BEAM_SIZE_PARAMETER) == null) {
        Map<String, String> manifestInfoEntries = new HashMap<>();

        // The version in the model must be correct or otherwise version
        // dependent code branches in other places fail
        manifestInfoEntries.put("OpenNLP-Version", "1.5.0");

        posModel = new POSModel(posModel.getLanguage(), posModel.getPosModel(), 10,
            manifestInfoEntries, posModel.getFactory());
      }
    }

    return posModel;
  }

  public void serialize(POSModel artifact, OutputStream out)
      throws IOException {
    artifact.serialize(out);
  }
}
