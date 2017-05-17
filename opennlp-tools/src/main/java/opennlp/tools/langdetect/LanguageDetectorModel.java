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

package opennlp.tools.langdetect;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Map;

import opennlp.tools.ml.model.AbstractModel;
import opennlp.tools.ml.model.MaxentModel;
import opennlp.tools.util.BaseToolFactory;
import opennlp.tools.util.InvalidFormatException;
import opennlp.tools.util.model.BaseModel;

/**
 * A model for language detection
 */
public class LanguageDetectorModel extends BaseModel {

  private static final String COMPONENT_NAME = "LanguageDetectorME";
  private static final String LANGDETECT_MODEL_ENTRY_NAME = "langdetect.model";

  public LanguageDetectorModel(MaxentModel langdetectModel,
                               Map<String, String> manifestInfoEntries,
                               LanguageDetectorFactory factory) {
    super(COMPONENT_NAME, "und", manifestInfoEntries, factory);

    artifactMap.put(LANGDETECT_MODEL_ENTRY_NAME, langdetectModel);
    checkArtifactMap();
  }

  public LanguageDetectorModel(InputStream in) throws IOException {
    super(COMPONENT_NAME, in);
  }

  public LanguageDetectorModel(File modelFile) throws IOException {
    super(COMPONENT_NAME, modelFile);
  }

  public LanguageDetectorModel(URL modelURL) throws IOException {
    super(COMPONENT_NAME, modelURL);
  }

  @Override
  protected void validateArtifactMap() throws InvalidFormatException {
    super.validateArtifactMap();

    if (!(artifactMap.get(LANGDETECT_MODEL_ENTRY_NAME) instanceof AbstractModel)) {
      throw new InvalidFormatException("Language detector model is incomplete!");
    }
  }

  public LanguageDetectorFactory getFactory() {
    return (LanguageDetectorFactory) this.toolFactory;
  }

  @Override
  protected Class<? extends BaseToolFactory> getDefaultFactory() {
    return LanguageDetectorFactory.class;
  }

  public MaxentModel getMaxentModel() {
    return (MaxentModel) artifactMap.get(LANGDETECT_MODEL_ENTRY_NAME);
  }
}
