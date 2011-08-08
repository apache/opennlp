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

package opennlp.tools.doccat;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;

import opennlp.model.AbstractModel;
import opennlp.tools.util.InvalidFormatException;
import opennlp.tools.util.model.BaseModel;

public class DoccatModel extends BaseModel {
  
  private static final String COMPONENT_NAME = "DocumentCategorizerME";
  private static final String DOCCAT_MODEL_ENTRY_NAME = "doccat.model";
  
  protected DoccatModel(String languageCode, AbstractModel doccatModel,
      Map<String, String> manifestInfoEntries) {
    super(COMPONENT_NAME, languageCode, manifestInfoEntries);
    
    artifactMap.put(DOCCAT_MODEL_ENTRY_NAME, doccatModel);
    
    checkArtifactMap();
  }
  
  public DoccatModel(String languageCode, AbstractModel doccatModel) {
    this(languageCode, doccatModel, null);
  }
  
  public DoccatModel(InputStream in) throws IOException, InvalidFormatException {
    super(COMPONENT_NAME, in);
  }

  @Override
  protected void validateArtifactMap() throws InvalidFormatException {
    super.validateArtifactMap();

    if (!(artifactMap.get(DOCCAT_MODEL_ENTRY_NAME) instanceof AbstractModel)) {
      throw new InvalidFormatException("Doccat model is incomplete!");
    }
  }

  public AbstractModel getChunkerModel() {
    return (AbstractModel) artifactMap.get(DOCCAT_MODEL_ENTRY_NAME);
  }
}
