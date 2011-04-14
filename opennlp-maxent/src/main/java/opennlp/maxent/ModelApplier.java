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

package opennlp.maxent;

import java.io.File;
import java.io.FileReader;
import java.text.DecimalFormat;

import opennlp.model.Event;
import opennlp.model.EventStream;
import opennlp.model.GenericModelReader;
import opennlp.model.MaxentModel;
import opennlp.model.RealValueFileEventStream;

/**
 * Test the model on some input.
 */
public class ModelApplier {
  MaxentModel _model;
  ContextGenerator _cg = new BasicContextGenerator(",");
  int counter = 1;

  // The format for printing percentages
  public static final DecimalFormat ROUNDED_FORMAT = new DecimalFormat("0.000");

  public ModelApplier(MaxentModel m) {
    _model = m;
  }

  private void eval(Event event) {
    eval(event, false);
  }

  private void eval(Event event, boolean real) {

    String outcome = event.getOutcome(); // Is ignored
    String[] context = event.getContext();

    double[] ocs;
    if (!real) {
      ocs = _model.eval(context);
    } else {
      float[] values = RealValueFileEventStream.parseContexts(context);
      ocs = _model.eval(context, values);
    }

    int numOutcomes = ocs.length;
    DoubleStringPair[] result = new DoubleStringPair[numOutcomes];
    for (int i=0; i<numOutcomes; i++)
      result[i] = new DoubleStringPair(ocs[i], _model.getOutcome(i));

    java.util.Arrays.sort(result);

    // Print the most likely outcome first, down to the least likely.
    for (int i=numOutcomes-1; i>=0; i--)
      System.out.print(result[i].stringValue + " " + result[i].doubleValue + " ");
    System.out.println();

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

    if (args.length == 0) {
      usage();
    }

    if (args.length > 0) {
      while (args[ai].startsWith("-")) {
        if (args[ai].equals("-real")) {
          real = true;
        } else if (args[ai].equals("-perceptron")) {
          type = "perceptron";
        } else {
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

      try {
        EventStream es = new BasicEventStream(new PlainTextByLineDataStream(
            new FileReader(new File(dataFileName))), ",");

        while (es.hasNext())
          predictor.eval(es.next(), real);

        return;
      } catch (Exception e) {
        System.out.println("Unable to read from specified file: "
            + modelFileName);
        System.out.println();
        e.printStackTrace();
      }
    }
  }
}
