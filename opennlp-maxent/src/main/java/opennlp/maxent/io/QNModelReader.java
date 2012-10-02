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

import java.io.File;
import java.io.IOException;

import opennlp.maxent.quasinewton.QNModel;
import opennlp.model.AbstractModel;
import opennlp.model.AbstractModelReader;
import opennlp.model.Context;
import opennlp.model.DataReader;

public class QNModelReader extends AbstractModelReader {

  public QNModelReader(DataReader dataReader) {
    super(dataReader);
  }

  public QNModelReader(File file) throws IOException {
    super(file);
  }

  @Override
  public void checkModelType() throws IOException {
    String modelType = readUTF();
    if (!modelType.equals("QN"))
      System.out.println("Error: attempting to load a " + modelType
          + " model as a MAXENT_QN model." + " You should expect problems.");
  }

  @Override
  public AbstractModel constructModel() throws IOException {
	String[] predNames = getPredicates();
    String[] outcomeNames = getOutcomes();
    Context[] params = getParameters();
    double[] parameters = getDoubleArrayParams();
    return new QNModel(predNames, outcomeNames, params, parameters);
  }
  
  private double[] getDoubleArrayParams() throws IOException {
    int numDouble = readInt();
    double[] doubleArray = new double[numDouble];
    for (int i=0; i < numDouble; i++) 
      doubleArray[i] = readDouble();
    return doubleArray;
  }

  private int[] getIntArrayParams() throws IOException {
    int numInt = readInt();
    int[] intArray = new int[numInt];
    for (int i=0; i < numInt; i++) 
    	intArray[i] = readInt();
    return intArray;
  }
  
  protected Context[] getParameters() throws java.io.IOException {
	int numContext = readInt();
	Context[] params = new Context[numContext];
	
	for (int i = 0; i < numContext; i++) {
	  int[] outcomePattern = getIntArrayParams();
	  double[] parameters = getDoubleArrayParams();
	  params[i] = new Context(outcomePattern, parameters);
	}
	return params;
  }
}