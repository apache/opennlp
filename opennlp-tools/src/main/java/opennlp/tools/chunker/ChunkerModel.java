/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreemnets.  See the NOTICE file distributed with
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


package opennlp.tools.chunker;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;

import opennlp.model.AbstractModel;
import opennlp.model.BinaryFileDataReader;
import opennlp.model.GenericModelReader;
import opennlp.tools.util.InvalidFormatException;
import opennlp.tools.util.model.BaseModel;

/**
 * The {@link ChunkerModel} is the model used
 * by a learnable {@link Chunker}.
 *
 * @see ChunkerME
 */
public class ChunkerModel extends BaseModel {

  private static final String COMPONENT_NAME = "ChunkerME";
  private static final String CHUNKER_MODEL_ENTRY_NAME = "chunker.model";

  public ChunkerModel(String languageCode, AbstractModel chunkerModel, Map<String, String> manifestInfoEntries) {

    super(COMPONENT_NAME, languageCode, manifestInfoEntries);

    artifactMap.put(CHUNKER_MODEL_ENTRY_NAME, chunkerModel);
    
    checkArtifactMap();
  }
  
  public ChunkerModel(String languageCode, AbstractModel chunkerModel) {
    this(languageCode, chunkerModel, null);
  }
  
  public ChunkerModel(InputStream in) throws IOException, InvalidFormatException {
    super(COMPONENT_NAME, in);
  }

  @Override
  protected void validateArtifactMap() throws InvalidFormatException {
    super.validateArtifactMap();

    if (!(artifactMap.get(CHUNKER_MODEL_ENTRY_NAME) instanceof AbstractModel)) {
      throw new InvalidFormatException("Chunker model is incomplete!");
    }
  }

  public AbstractModel getChunkerModel() {
    return (AbstractModel) artifactMap.get(CHUNKER_MODEL_ENTRY_NAME);
  }
  
  public static void main(String[] args) throws FileNotFoundException, IOException {
    
    if (args.length != 4){
      System.err.println("ChunkerModel -lang code packageName modelName");
      System.exit(1);
    }

    String lang = args[1];
    String packageName = args[2];
    String modelName = args[3];

    AbstractModel chunkerModel = new GenericModelReader(
        new BinaryFileDataReader(new FileInputStream(modelName))).getModel();

    ChunkerModel packageModel = new ChunkerModel(lang, chunkerModel);
    packageModel.serialize(new FileOutputStream(packageName));
  }
}
