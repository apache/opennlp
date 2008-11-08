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


package opennlp.tools.tokenize;

import java.io.DataInputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Properties;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import opennlp.maxent.io.BinaryGISModelReader;
import opennlp.model.AbstractModel;
import opennlp.model.MaxentModel;
import opennlp.tools.util.InvalidFormatException;
import opennlp.tools.util.ModelUtil;

/**
 * The {@link TokenizerModel} is the model used
 * by a learnable {@link Tokenizer}.
 *
 * @see TokenizerME
 */
public final class TokenizerModel {

  private static final String MAXENT_MODEL_ENTRY_NAME = "token.bin";
  private static final String PROPERTIES_ENTRY_NAME = "tokenizer.xml";
  
  private static final String USE_ALPHA_NUMERIC_OPTIMIZATION = 
      "useAlphaNumericOptimization";
  
  private final AbstractModel model;
  
  private final boolean useAlphaNumericOptimization;
  
  /**
   * Initializes the current instance.
   * 
   * @param tokenizerMaxentModel
   * @param useAlphaNumericOptimization
   */
  public TokenizerModel(AbstractModel tokenizerMaxentModel, 
      boolean useAlphaNumericOptimization) {
    
    if (tokenizerMaxentModel == null)
        throw new IllegalArgumentException("tokenizerMaxentModel param must not bet null!");
    
    if (!isModelCompatible(tokenizerMaxentModel))
        throw new IllegalArgumentException("The maxent model is not compatible!");
    
    this.model = tokenizerMaxentModel;
    this.useAlphaNumericOptimization = useAlphaNumericOptimization;
  }
  
  private static boolean isModelCompatible(MaxentModel model) {
    return ModelUtil.validateOutcomes(model, TokenizerME.SPLIT, TokenizerME.NO_SPLIT);
  }
  
  public MaxentModel getMaxentModel() {
    return model;
  }
  
  public boolean useAlphaNumericOptimization() {
    return useAlphaNumericOptimization;
  }
  
  /**
   * Writes the {@link TokenizerModel} to the given {@link OutputStream}.
   * 
   * After the serialization is finished the provided 
   * {@link OutputStream} is closed.
   * 
   * @param out the stream in which the model is written
   * 
   * @throws IOException if something goes wrong writing the in the
   * provided {@link OutputStream}.
   */
  public void serialize(OutputStream out) throws IOException {
    final ZipOutputStream zip = new ZipOutputStream(out);
    
    // write model
    ZipEntry modelEntry = new ZipEntry(MAXENT_MODEL_ENTRY_NAME);
    zip.putNextEntry(modelEntry);
    
    ModelUtil.writeModel(model, zip);
    
    zip.closeEntry();
    
    Properties properties = new Properties();
    properties.setProperty(USE_ALPHA_NUMERIC_OPTIMIZATION,
        Boolean.toString(useAlphaNumericOptimization()));
    
    ZipEntry propertiesEntry = new ZipEntry(PROPERTIES_ENTRY_NAME);
    zip.putNextEntry(propertiesEntry);
    
    properties.store(zip, "This file contains the tokenizer properties.");
    
    zip.closeEntry();
    zip.close();
  }
  
  /**
   * Creates a {@link TokenizerModel} from the provided {@link InputStream}.
   * 
   * The {@link InputStream} in remains open after the model is read.
   * 
   * @param in stream to read the model from
   * 
   * @return the new {@link TokenizerModel} read from the {@link InputStream} in.
   * 
   * @throws IOException
   * @throws InvalidFormatException
   */
  public static TokenizerModel create(InputStream in) throws IOException, 
      InvalidFormatException {
    
    final ZipInputStream zip = new ZipInputStream(in);
    
    AbstractModel model = null;
    Properties properties = null;
    
    ZipEntry entry;
    while((entry = zip.getNextEntry()) != null ) {
      
      if (MAXENT_MODEL_ENTRY_NAME.equals(entry.getName())) {
        
        // read model
        model = new BinaryGISModelReader(
            new DataInputStream(zip)).getModel();
        
        zip.closeEntry();
      }
      else if (PROPERTIES_ENTRY_NAME.equals(entry.getName())) {
        
        // read properties
        properties = new Properties();
        properties.load(zip);
        
        zip.closeEntry();
      }
      else {
        throw new InvalidFormatException("Model contains unkown resource!");
      }
    }
    
    zip.close();
    
    if (model == null || properties == null) {
      throw new InvalidFormatException("Token model is incomplete!");
    }
    
    String useAlphaNumericOptimizationString = 
        properties.getProperty(USE_ALPHA_NUMERIC_OPTIMIZATION);
    
    if (useAlphaNumericOptimizationString == null) {
      throw new InvalidFormatException("The seAlphaNumericOptimization parameter " +
      		"cannot be found!");
    }
    
    if (!isModelCompatible(model)) {
      throw new InvalidFormatException("The maxent model is not compatible with the tokenizer!");
    }
    
    return new TokenizerModel(model, 
        Boolean.parseBoolean(useAlphaNumericOptimizationString));
  }
  
  public static void main(String[] args) throws IOException {
    if (args.length < 2){
      System.err.println("TokenizerModel [-alphaNumericOptimization] packageName modelName");
      System.exit(1);
    }
    
    int ai = 0;
    
    boolean alphaNumericOptimization = false;
    
    if ("-alphaNumericOptimization".equals(args[ai])) {
      alphaNumericOptimization = true;
      ai++;
    }
    
    String packageName = args[ai++];
    String modelName = args[ai];
    
    AbstractModel model = new BinaryGISModelReader(new DataInputStream(
        new FileInputStream(modelName))).getModel();
    
    TokenizerModel packageModel = new TokenizerModel(model, alphaNumericOptimization);
    
    OutputStream out = null;
    try {
      out = new FileOutputStream(packageName);
      packageModel.serialize(out);
    }
    finally {
      if (out != null)
        out.close();
    }
  }
}