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
package opennlp.maxent.quasinewton;

import opennlp.model.AbstractModel;
import opennlp.model.Context;
import opennlp.model.EvalParameters;
import opennlp.model.UniformPrior;

public class QNModel extends AbstractModel {
  private static final double SMOOTHING_VALUE = 0.1;
  private double[] parameters;
  // FROM trainer
  public QNModel(LogLikelihoodFunction monitor, double[] parameters) {
	super(null, monitor.getPredLabels(), monitor.getOutcomeLabels());
	
    int[][] outcomePatterns = monitor.getOutcomePatterns();
    Context[] params = new Context[monitor.getPredLabels().length];
    for (int ci = 0; ci < params.length; ci++) {
      int[] outcomePattern = outcomePatterns[ci];
      double[] alpha = new double[outcomePattern.length];
      for (int oi = 0; oi < outcomePattern.length; oi++) {
        alpha[oi] = parameters[ci + (outcomePattern[oi] * monitor.getPredLabels().length)];
      }
      params[ci] = new Context(outcomePattern, alpha);
    }
    this.evalParams = new EvalParameters(params, monitor.getOutcomeLabels().length);
    this.prior = new UniformPrior();
    this.modelType = ModelType.MaxentQn;
    
    this.parameters = parameters;
  }
  
  // FROM model reader
  public QNModel(String[] predNames, String[] outcomeNames, Context[] params, double[] parameters) {
	 super(params, predNames, outcomeNames);
	 this.prior = new UniformPrior();
	 this.modelType = ModelType.MaxentQn;
	 
	 this.parameters = parameters;
  }

  public double[] eval(String[] context) {
    return eval(context, new double[evalParams.getNumOutcomes()]);
  }
  
  private int getPredIndex(String predicate) {
	return pmap.get(predicate);
  }

  public double[] eval(String[] context, double[] probs) {
    return eval(context, null, probs);
  }
  
  public double[] eval(String[] context, float[] values) {
	  return eval(context, values, new double[evalParams.getNumOutcomes()]);
  }
  
  // TODO need implments for handlling with "probs".
  private double[] eval(String[] context, float[] values, double[] probs) {
    double[] result = new double[outcomeNames.length];
    double[] table = new double[outcomeNames.length + 1];  
    for (int pi = 0; pi < context.length; pi++) {
      int predIdx = getPredIndex(context[pi]);
     
      for (int oi = 0; oi < outcomeNames.length; oi++) {
        int paraIdx = oi * pmap.size() + predIdx;
        
        double predValue = 1.0;
        if (values != null) predValue = values[pi];
        if (paraIdx < 0) {
        	table[oi] += predValue * SMOOTHING_VALUE;
        } else {
        	table[oi] += predValue * parameters[paraIdx];
        }
        
      }
    }
    
    for (int oi = 0; oi < outcomeNames.length; oi++) {
    	table[oi] = Math.exp(table[oi]);
    	table[outcomeNames.length] += table[oi];
    }
    for (int oi = 0; oi < outcomeNames.length; oi++) {
    	result[oi] = table[oi] / table[outcomeNames.length];
    }
    return result;
//    double[] table = new double[outcomeNames.length];
//    Arrays.fill(table, 1.0 / outcomeNames.length);
//    return table;
  }

  public int getNumOutcomes() {
    return this.outcomeNames.length;
  }
  
  public double[] getParameters() {
	  return this.parameters;
  }
  
  public boolean equals(Object obj) {
    if (!(obj instanceof QNModel))
      return false;
    
    QNModel objModel = (QNModel) obj;
    if (this.outcomeNames.length != objModel.outcomeNames.length)
      return false;
    for (int i = 0; i < this.outcomeNames.length; i++) {
      if (!this.outcomeNames[i].equals(objModel.outcomeNames[i]))
        return false;
    }
    
    if (this.pmap.size() != objModel.pmap.size())
      return false;
    String[] pmapArray = new String[pmap.size()];
    pmap.toArray(pmapArray);
    for (int i = 0; i < this.pmap.size(); i++) {
      if (i != objModel.pmap.get(pmapArray[i]))
        return false;
    }
    
    // compare evalParameters
    Context[] contextComparing = objModel.evalParams.getParams();
    if (this.evalParams.getParams().length != contextComparing.length)
      return false;
    for (int i = 0; i < this.evalParams.getParams().length; i++) {
      if (this.evalParams.getParams()[i].getOutcomes().length != contextComparing[i].getOutcomes().length)
        return false;
      for (int j = 0; i < this.evalParams.getParams()[i].getOutcomes().length; i++) {
    	  if (this.evalParams.getParams()[i].getOutcomes()[j] != contextComparing[i].getOutcomes()[j])
    	    return false;
      }
      
      if (this.evalParams.getParams()[i].getParameters().length != contextComparing[i].getParameters().length)
        return false;
      for (int j = 0; i < this.evalParams.getParams()[i].getParameters().length; i++) {
    	  if (this.evalParams.getParams()[i].getParameters()[j] != contextComparing[i].getParameters()[j])
    	    return false;
      }
    }   
    return true;
  }
}