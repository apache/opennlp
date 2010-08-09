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

package opennlp.maxent;

import java.io.File;
import java.io.FileReader;
import java.text.DecimalFormat;

import opennlp.model.Event;
import opennlp.maxent.BasicContextGenerator;
import opennlp.maxent.ContextGenerator;
import opennlp.maxent.DataStream;
import opennlp.model.EventStream;
import opennlp.maxent.PlainTextByLineDataStream;
import opennlp.model.GenericModelReader;
import opennlp.model.MaxentModel;
import opennlp.model.RealValueFileEventStream;

/**
 * Test the model on some input.
 *
 * @author  Jason Baldridge
 * @version $Revision: 1.2 $, $Date: 2010-08-09 18:58:42 $
 */
public class ModelApplier {
  MaxentModel _model;
  ContextGenerator _cg = new BasicContextGenerator(",");
  int counter = 1;

  // The format for printing percentages
  public static final DecimalFormat ROUNDED_FORMAT = new DecimalFormat("0.000");

  public ModelApplier (MaxentModel m) {
    _model = m;
  }
    
  private void eval (Event event) {
    eval(event,false);
  }
    
  private void eval (Event event, boolean real) {

    String outcome = event.getOutcome();
    String[] context = event.getContext();

    double[] ocs;
    if (!real) {
      ocs = _model.eval(context);
    } else {
      float[] values = RealValueFileEventStream.parseContexts(context);
      ocs = _model.eval(context,values);
    }

    int best = 0;
    for (int i = 1; i<ocs.length; i++)
      if (ocs[i] > ocs[best]) best = i;

    String predictedLabel = _model.getOutcome(best);
    String madeError = "+";
    if (predictedLabel.equals(outcome)) 
      madeError = "";

    System.out.println(counter + "\t0:"+outcome+"\t0:" + _model.getOutcome(best) + "\t"+madeError+"\t" + ROUNDED_FORMAT.format(ocs[best]));
    counter++;
			 
  }
    
  private static void usage() {
    System.err.println("java ModelApplier [-real] modelFile dataFile");
    System.exit(1);
  }

  /**
   * Main method. Call as follows:
   * <p>
   * java ModelApplier modelFile dataFile
   */
  public static void main(String[] args) {
    String dataFileName, modelFileName;
    boolean real = false;
    String type = "maxent";
    int ai = 0;
    if (args.length > 0) {
      while (args[ai].startsWith("-")) {
	if (args[ai].equals("-real")) {
	  real = true;
	}
	else if (args[ai].equals("-perceptron")) {
	  type = "perceptron";
	}
	else {
	  usage();
	}
	ai++;
      }      
      modelFileName = args[ai++];
      dataFileName = args[ai++];

      ModelApplier predictor = null;
      try {
	MaxentModel m = new GenericModelReader(new File(modelFileName)).getModel();
	predictor = new ModelApplier(m);
      } catch (Exception e) {
	e.printStackTrace();
	System.exit(0);
      }

      System.out.println("=== Predictions on test data ===\n");
      System.out.println(" inst#     actual  predicted error prediction");
      try {
	EventStream es = 
	  new BasicEventStream(new PlainTextByLineDataStream(
							     new FileReader(new File(dataFileName))), ",");
	  
	while (es.hasNext()) {
	  predictor.eval(es.next(),real);
	}
		
	return;
      }
      catch (Exception e) {
	System.out.println("Unable to read from specified file: "+modelFileName);
	System.out.println();
	e.printStackTrace();
      }
    }
  }
}
