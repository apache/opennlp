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

import java.io.File;
import java.io.FileReader;

import opennlp.maxent.BasicEventStream;
import opennlp.maxent.GIS;
import opennlp.maxent.PlainTextByLineDataStream;
import opennlp.maxent.RealBasicEventStream;
import opennlp.maxent.io.GISModelWriter;
import opennlp.maxent.io.SuffixSensitiveGISModelWriter;
import opennlp.model.AbstractModel;
import opennlp.model.EventStream;
import opennlp.model.OnePassDataIndexer;
import opennlp.model.OnePassRealValueDataIndexer;
import opennlp.perceptron.PerceptronTrainer;

/**
 * Main class which calls the GIS procedure after building the EventStream
 * from the data.
 */
public class CreateModel {

    // some parameters if you want to play around with the smoothing option
    // for model training.  This can improve model accuracy, though training
    // will potentially take longer and use more memory.  Model size will also
    // be larger.  Initial testing indicates improvements for models built on
    // small data sets and few outcomes, but performance degradation for those
    // with large data sets and lots of outcomes.
    public static boolean USE_SMOOTHING = false;
    public static double SMOOTHING_OBSERVATION = 0.1;
    
    private static void usage() {
      System.err.println("java CreateModel [-real] dataFile");
      System.exit(1);
    }
    
    /**
     * Main method. Call as follows:
     * <p>
     * java CreateModel dataFile
     */
    public static void main (String[] args) {
      int ai = 0;
      boolean real = false;
      String type = "maxent";
      if(args.length == 0) {
        usage();
      }
      while (args[ai].startsWith("-")) {
        if (args[ai].equals("-real")) {
          real = true;
        }
        else if (args[ai].equals("-perceptron")) {
          type = "perceptron";
        }
        else {
          System.err.println("Unknown option: "+args[ai]);
          usage();
        }
        ai++;
      }
      String dataFileName = new String(args[ai]);
      String modelFileName =
        dataFileName.substring(0,dataFileName.lastIndexOf('.'))
        + "Model.txt";
      try {
        FileReader datafr = new FileReader(new File(dataFileName));
        EventStream es;
        if (!real) { 
          es = new BasicEventStream(new PlainTextByLineDataStream(datafr));
        }
        else {
          es = new RealBasicEventStream(new PlainTextByLineDataStream(datafr));
        }
        GIS.SMOOTHING_OBSERVATION = SMOOTHING_OBSERVATION;
        AbstractModel model;
        if (type.equals("maxent")) {
        
          if (!real) {
            model = GIS.trainModel(es,USE_SMOOTHING);
          }
          else {
            model = GIS.trainModel(100, new OnePassRealValueDataIndexer(es,0), USE_SMOOTHING);
          }
        }
        else if (type.equals("perceptron")){ 
          System.err.println("Perceptron training");
          model = new PerceptronTrainer().trainModel(10, new OnePassDataIndexer(es,0),0);
        }
        else {
          System.err.println("Unknown model type: "+type);
          model = null;
        }
        
        File outputFile = new File(modelFileName);
        GISModelWriter writer =  new SuffixSensitiveGISModelWriter(model, outputFile);
        writer.persist();
      } catch (Exception e) {
        System.out.print("Unable to create model due to exception: ");
        System.out.println(e);
        e.printStackTrace();
      }
    }

}
