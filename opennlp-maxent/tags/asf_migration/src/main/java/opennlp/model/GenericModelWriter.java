/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 * 
 *   http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package opennlp.model;

import java.io.BufferedWriter;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.util.zip.GZIPOutputStream;

import opennlp.maxent.io.BinaryGISModelWriter;
import opennlp.maxent.io.PlainTextGISModelWriter;
import opennlp.model.AbstractModel.ModelType;
import opennlp.perceptron.BinaryPerceptronModelWriter;
import opennlp.perceptron.PlainTextPerceptronModelWriter;

public class GenericModelWriter extends AbstractModelWriter {

  private AbstractModelWriter delegateWriter;
  
  public GenericModelWriter(AbstractModel model, File file) throws IOException {
    String filename = file.getName();
    OutputStream os;
    // handle the zipped/not zipped distinction
    if (filename.endsWith(".gz")) {
      os = new GZIPOutputStream(new FileOutputStream(file));
      filename = filename.substring(0,filename.length()-3);
    }
    else {
      os = new FileOutputStream(file);
    }
    
    // handle the different formats
    if (filename.endsWith(".bin")) {
      init(model,new DataOutputStream(os));
    }
    else {  // filename ends with ".txt"
      init(model,new BufferedWriter(new OutputStreamWriter(os)));
    }
  }
  
  public GenericModelWriter(AbstractModel model, DataOutputStream dos) {
    init(model,dos);
  }
  
  private void init(AbstractModel model, DataOutputStream dos) {
    if (model.getModelType() == ModelType.Perceptron) {
      delegateWriter = new BinaryPerceptronModelWriter(model,dos);
    }
    else if (model.getModelType() == ModelType.Maxent) {
      delegateWriter = new BinaryGISModelWriter(model,dos);
    }
  }
  
  private void init(AbstractModel model, BufferedWriter bw) {
    if (model.getModelType() == ModelType.Perceptron) {
      delegateWriter = new PlainTextPerceptronModelWriter(model,bw);
    }
    else if (model.getModelType() == ModelType.Maxent) {
      delegateWriter = new PlainTextGISModelWriter(model,bw);
    }
  }

  @Override
  public void close() throws IOException {
    delegateWriter.close();
  }

  @Override
  public void persist() throws IOException {
    delegateWriter.persist();
  }

  @Override
  public void writeDouble(double d) throws IOException {
    delegateWriter.writeDouble(d);
  }

  @Override
  public void writeInt(int i) throws IOException {
    delegateWriter.writeInt(i);
  }

  @Override
  public void writeUTF(String s) throws IOException {
    delegateWriter.writeUTF(s);
  }
  
}
