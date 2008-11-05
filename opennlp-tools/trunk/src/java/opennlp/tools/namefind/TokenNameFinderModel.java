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

package opennlp.tools.namefind;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import opennlp.maxent.io.BinaryGISModelReader;
import opennlp.model.AbstractModel;
import opennlp.model.MaxentModel;
import opennlp.tools.tokenize.TokenizerModel;
import opennlp.tools.util.InvalidFormatException;
import opennlp.tools.util.ModelUtil;
import opennlp.tools.util.featuregen.AdaptiveFeatureGenerator;
import opennlp.tools.util.featuregen.AggregatedFeatureGenerator;
import opennlp.tools.util.featuregen.Factory;
import opennlp.tools.util.featuregen.FactoryResourceManager;

/**
 * The {@link TokenNameFinderModel} is the model used
 * by a learnable {@link TokenNameFinder}.
 * 
 * @see NameFinderME
 */
public class TokenNameFinderModel {
  
  private static final String MAXENT_MODEL_ENTRY_NAME = "nameFinder.bin";
  
  private static final String GENERATOR_DESCRIPTOR_ENTRY_NAME = "generator.xml";
  
  private static Logger logger = 
        Logger.getLogger(TokenNameFinderModel.class.getName());
  
  private AbstractModel nameFinderModel;

  private byte generatorDescriptor[];

  private Map<String, byte[]> resources;
  
  public TokenNameFinderModel(AbstractModel nameFinderModel, 
      InputStream generatorDescriptorIn, Map<String, byte[]> resources) {
    
    if (!isModelValid(nameFinderModel)) {
      throw new IllegalArgumentException("Model not compatible with name finder!");
    }
    
    this.nameFinderModel = nameFinderModel;
    
    // copy descriptor to an byte array
    
    // The resource map must not contain key which are already taken
    // like the name finder maxent model name
    if (resources.containsKey(MAXENT_MODEL_ENTRY_NAME)) {
      throw new IllegalArgumentException();
    }
    
    this.resources = resources;
  }
  
  /**
   * Retrieves the {@link TokenNameFinder} model.
   * 
   * @return
   */
  public AbstractModel getNameFinderModel() {
    return nameFinderModel;
  }
  
  /**
   * Creates the {@link AdaptiveFeatureGenerator}. Usually this
   * is a set of generators contained in the {@link AggregatedFeatureGenerator}.
   * 
   * Note:
   * The generators are created on every call to this method. 
   * 
   * @return
   */
  public AdaptiveFeatureGenerator createFeatureGenerators() {
    
    InputStream descriptorIn = new ByteArrayInputStream(generatorDescriptor);
    
    AdaptiveFeatureGenerator generator = null;
    try {
      generator = Factory.create(descriptorIn, new FactoryResourceManager() {

        public InputStream getResource(String key) {
          byte resource[] = resources.get(key);
          
          return new ByteArrayInputStream(resource);
        }
      });
    } catch (IOException e) {
      logger.log(Level.SEVERE, 
          "Sorry, that reading from memory can go wrong.", e);
    }
    
    return generator;
  }
  
  private static boolean isModelValid(MaxentModel model) {
    
    return ModelUtil.validateOutcomes(model, NameFinderME.START) ||
        ModelUtil.validateOutcomes(model, NameFinderME.OTHER) ||
        ModelUtil.validateOutcomes(model, NameFinderME.START, NameFinderME.OTHER) ||
        ModelUtil.validateOutcomes(model, NameFinderME.START, NameFinderME.CONTINUE) ||
        ModelUtil.validateOutcomes(model, NameFinderME.START, NameFinderME.CONTINUE, 
            NameFinderME.OTHER);
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
    
    ModelUtil.writeModel(nameFinderModel, zip);
    
    zip.closeEntry();
    
    // write descriptor
    ZipEntry descriptorEntry = new ZipEntry(GENERATOR_DESCRIPTOR_ENTRY_NAME);
    zip.putNextEntry(descriptorEntry);
    
    zip.write(generatorDescriptor);
    
    zip.closeEntry();
    
    // write the resources
    // for each resource
    for (String resourceName : resources.keySet()) {
      ZipEntry resource = new ZipEntry(resourceName);
      zip.putNextEntry(resource);
      
      zip.write(resources.get(resourceName));
      
      zip.closeEntry();
    }
    
    zip.close();
  }
  
  /**
   * Writes an {@link ZipInputStream} to an byte array.
   * 
   * The {@link InputStream} remains open after everything is read.
   *  
   * @param in
   * @param entry
   * 
   * @return the byte array which contains the data from the {@link InputStream}
   * 
   * @throws IOException if an error occurs during reading from
   * the {@link InputStream}
   */
  private static byte[] read(InputStream in, ZipEntry entry) throws IOException {
    
    // TODO: Is this cast safe to do ?
    int entrySize = (int) entry.getSize();
    
    if (entrySize == -1) {
      entrySize = 32000;
    }
    
    ByteArrayOutputStream byteArrayOut = new ByteArrayOutputStream(entrySize);
    
    int length;
    byte buffer[] = new byte[1024];
    while ((length = in.read(buffer)) > 0) {
      byteArrayOut.write(buffer, 0, length);
    }
    byteArrayOut.close();
    
    return byteArrayOut.toByteArray();
    
  }
  
  /**
   * Creates a {@link TokenNameFinderModel} from the provided {@link InputStream}.
   * 
   * The {@link InputStream} in remains open after the model is read.
   * 
   * @param in stream to read the model from
   * 
   * @return  the new {@link TokenNameFinderModel} read from the {@link InputStream} in.
   * 
   * @throws IOException
   * @throws InvalidFormatException
   */
  static TokenNameFinderModel create(InputStream in) throws IOException, 
      InvalidFormatException {
    
    final ZipInputStream zip = new ZipInputStream(in);
    
    AbstractModel nameFinderModel = null;
    byte generatorDescriptor[] = null;
    Map<String, byte[]> resources = new HashMap<String, byte[]>();
    
    ZipEntry entry;
    while((entry = zip.getNextEntry()) != null ) {
      if (MAXENT_MODEL_ENTRY_NAME.equals(entry.getName())) {
            
        // read model
        nameFinderModel = new BinaryGISModelReader(
            new DataInputStream(zip)).getModel();
        
        zip.closeEntry();
      } 
      else if (GENERATOR_DESCRIPTOR_ENTRY_NAME.equals(entry.getName())) {
        
        generatorDescriptor = read(zip, entry);
        zip.closeEntry();
      }
      else {
        
        resources.put(entry.getName(), read(zip, entry));
        zip.closeEntry();
      }
    }
    
    return new TokenNameFinderModel(nameFinderModel, null, resources);
  }
}