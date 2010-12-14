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

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import opennlp.maxent.GISModel;
import opennlp.maxent.io.BinaryGISModelReader;
import opennlp.model.AbstractModel;
import opennlp.model.MaxentModel;
import opennlp.tools.util.InvalidFormatException;
import opennlp.tools.util.ModelUtil;

/**
 * The {@link ChunkerModel} is the model used
 * by a learnable {@link Chunker}.
 * 
 * @see ChunkerME
 */
public class ChunkerModel {

  private static final String CHUNKER_MODEL_ENTRY_NAME = "chunker.bin";
  
  private AbstractModel chunkerModel;
  
  public ChunkerModel(AbstractModel chunkerModel) {
    this.chunkerModel = chunkerModel;
  }
  
  public MaxentModel getMaxentChunkerModel() {
    return chunkerModel;
  }
  
  /**
   * .
   * 
   * After the serialization is finished the provided 
   * {@link OutputStream} is closed.
   * 
   * @param out
   * @throws IOException
   */
  public void serialize(OutputStream out) throws IOException {
    ZipOutputStream zip = new ZipOutputStream(out);
    
    zip.putNextEntry(new ZipEntry(CHUNKER_MODEL_ENTRY_NAME));
    ModelUtil.writeModel(chunkerModel, zip);
    zip.closeEntry();
    
    zip.close();
  }
  
  /**
   * .
   * 
   * The {@link InputStream} in remains open after the model is read.
   * 
   * @param in
   * @return
   * @throws IOException
   * @throws InvalidFormatException
   */
  public static ChunkerModel create(InputStream in) throws IOException, InvalidFormatException {
    ZipInputStream zip = new ZipInputStream(in);
    
    ZipEntry chunkerModelEntry = zip.getNextEntry();
    
    if (chunkerModelEntry == null || 
        !CHUNKER_MODEL_ENTRY_NAME.equals(chunkerModelEntry.getName()))
      throw new InvalidFormatException("Could not find maxent chunker model!");
    
    AbstractModel chunkerModel = new BinaryGISModelReader(
        new DataInputStream(zip)).getModel();
    
    return new ChunkerModel(chunkerModel);
  }
}