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
package opennlp.maxent.io;

import java.io.IOException;

import opennlp.maxent.quasinewton.QNModel;
import opennlp.model.AbstractModel;
import opennlp.model.AbstractModelWriter;
import opennlp.model.Context;
import opennlp.model.IndexHashTable;

public abstract class QNModelWriter extends AbstractModelWriter {
  protected String[] outcomeNames;
  protected String[] predNames;
  protected Context[] params;
  protected double[] predParams;
  //protected EvalParameters evalParam;
  
  protected IndexHashTable<String> pmap;
  protected double[] parameters;
  
  @SuppressWarnings("unchecked")
  public QNModelWriter(AbstractModel model) {
    Object[] data = model.getDataStructures();
    params = (Context[]) data[0];
    pmap = (IndexHashTable<String>) data[1];
    outcomeNames = (String[]) data[2];
    
    QNModel qnModel = (QNModel) model;
    parameters = qnModel.getParameters();
  }
  
  @Override
  public void persist() throws IOException {
    // the type of model (QN)
    writeUTF("QN");
    
    // predNames
    predNames = new String[pmap.size()];
    pmap.toArray(predNames);
    writeInt(predNames.length);
    for (int i = 0; i < predNames.length; i++)
      writeUTF(predNames[i]);
 
    // outcomeNames
    writeInt(outcomeNames.length);
    for (int i = 0; i < outcomeNames.length; i++)
      writeUTF(outcomeNames[i]);
    
    // parameters
    writeInt(params.length);
    for (Context currContext : params) {
    	writeInt(currContext.getOutcomes().length);
    	for (int i = 0; i < currContext.getOutcomes().length; i++) {
    		writeInt(currContext.getOutcomes()[i]);
    	}
    	writeInt(currContext.getParameters().length);
    	for (int i = 0; i < currContext.getParameters().length; i++) {
    		writeDouble(currContext.getParameters()[i]);
    	}
    }
    
    // parameters 2
    writeInt(parameters.length);
    for (int i = 0; i < parameters.length; i++)
      writeDouble(parameters[i]);
    close();
  }
}

