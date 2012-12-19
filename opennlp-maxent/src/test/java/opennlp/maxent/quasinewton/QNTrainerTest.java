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
package opennlp.maxent.quasinewton;

import static opennlp.PrepAttachDataUtil.createTrainingStream;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

import opennlp.model.AbstractModel;
import opennlp.model.BinaryFileDataReader;
import opennlp.model.DataIndexer;
import opennlp.model.Event;
import opennlp.model.GenericModelReader;
import opennlp.model.GenericModelWriter;
import opennlp.model.MaxentModel;
import opennlp.model.OnePassRealValueDataIndexer;
import opennlp.model.RealValueFileEventStream;
import opennlp.model.TwoPassDataIndexer;
import opennlp.perceptron.PerceptronPrepAttachTest;

import org.junit.Test;

public class QNTrainerTest {
  @Test
  public void testTrainModelReturnsAQNModel() throws Exception {
    // given
    RealValueFileEventStream rvfes1 = new RealValueFileEventStream("src/test/resources/data/opennlp/maxent/real-valued-weights-training-data.txt");  
    DataIndexer testDataIndexer = new OnePassRealValueDataIndexer(rvfes1,1);
    // when
    QNModel trainedModel = new QNTrainer(false).trainModel(testDataIndexer);
    // then
    assertNotNull(trainedModel);
  }

  @Test
  public void testInTinyDevSet() throws Exception {
    // given
    RealValueFileEventStream rvfes1 = new RealValueFileEventStream("src/test/resources/data/opennlp/maxent/real-valued-weights-training-data.txt");  
    DataIndexer testDataIndexer = new OnePassRealValueDataIndexer(rvfes1,1);
    // when
    QNModel trainedModel = new QNTrainer(15, true).trainModel(testDataIndexer);
    String[] features2Classify = new String[] {"feature2","feature3", "feature3", "feature3","feature3", "feature3", "feature3","feature3", "feature3", "feature3","feature3", "feature3"};
    double[] eval = trainedModel.eval(features2Classify);
    // then
    assertNotNull(eval);
  }

  @Test
  public void testInBigDevSet() throws IOException {
    QNModel trainedModel = new QNTrainer(10, 1000, true).trainModel(new TwoPassDataIndexer(createTrainingStream()));
    // then
    testModel(trainedModel);
  }
  
  @Test
  public void testModel() throws IOException {
	    // given
	    RealValueFileEventStream rvfes1 = new RealValueFileEventStream("src/test/resources/data/opennlp/maxent/real-valued-weights-training-data.txt");  
	    DataIndexer testDataIndexer = new OnePassRealValueDataIndexer(rvfes1,1);
	    // when
	    QNModel trainedModel = new QNTrainer(15, true).trainModel(testDataIndexer);
	    
	    assertTrue(trainedModel.equals(trainedModel));  
	    assertFalse(trainedModel.equals(null));
  }
  
  @Test
  public void testSerdeModel() throws IOException {
	    // given
	    RealValueFileEventStream rvfes1 = new RealValueFileEventStream("src/test/resources/data/opennlp/maxent/real-valued-weights-training-data.txt");  
	    DataIndexer testDataIndexer = new OnePassRealValueDataIndexer(rvfes1,1);
	    // when
	   // QNModel trainedModel = new QNTrainer(5, 500, true).trainModel(new TwoPassDataIndexer(createTrainingStream()));
	    QNModel trainedModel = new QNTrainer(5, 700, true).trainModel(testDataIndexer);
	    
	    ByteArrayOutputStream modelBytes = new ByteArrayOutputStream();
	    GenericModelWriter modelWriter = new GenericModelWriter(trainedModel, new DataOutputStream(modelBytes));
	    modelWriter.persist();
	    modelWriter.close();
	    
	    GenericModelReader modelReader = new GenericModelReader(new BinaryFileDataReader(
	        new ByteArrayInputStream(modelBytes.toByteArray())));
	    AbstractModel readModel = modelReader.getModel();
	    QNModel deserModel = (QNModel) readModel;
	    
	    assertTrue(trainedModel.equals(deserModel)); 
	    
	    String[] features2Classify = new String[] {"feature2","feature3", "feature3", "feature3","feature3", "feature3", "feature3","feature3", "feature3", "feature3","feature3", "feature3"};
	    double[] eval01 = trainedModel.eval(features2Classify);
	    double[] eval02 = deserModel.eval(features2Classify);
	    
	    assertEquals(eval01.length, eval02.length);
	    for (int i = 0; i < eval01.length; i++) {
	    	assertEquals(eval01[i], eval02[i], 0.00000001);
	    }
  }

  public static void testModel(MaxentModel model) throws IOException {
    List<Event> devEvents = readPpaFile("devset");

    int total = 0;
    int correct = 0;
    for (Event ev: devEvents) {
      String targetLabel = ev.getOutcome();
      double[] ocs = model.eval(ev.getContext());

      int best = 0;
      for (int i=1; i<ocs.length; i++)
        if (ocs[i] > ocs[best])
          best = i;
      String predictedLabel = model.getOutcome(best);

      if (targetLabel.equals(predictedLabel))
        correct++;
      total++;
    }

    double accuracy = correct/(double)total;
    System.out.println("Accuracy on PPA devset: (" + correct + "/" + total + ") " + accuracy);
  }

  private static List<Event> readPpaFile(String filename) throws IOException {

    List<Event> events = new ArrayList<Event>();

    InputStream in = PerceptronPrepAttachTest.class.getResourceAsStream("/data/ppa/" +
      filename);

    try {
      BufferedReader reader = new BufferedReader(new InputStreamReader(in, "UTF-8"));
      String line;
      while ((line = reader.readLine()) != null) {
        String[] items = line.split("\\s+");
        String label = items[5];
        String[] context = { "verb=" + items[1], "noun=" + items[2],
          "prep=" + items[3], "prep_obj=" + items[4] };
        events.add(new Event(label, context));
      }
    } finally {
      in.close();
    }
    return events;
  }
}
