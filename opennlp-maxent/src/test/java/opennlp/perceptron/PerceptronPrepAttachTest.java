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

package opennlp.perceptron;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import junit.framework.TestCase;
import opennlp.model.AbstractModel;
import opennlp.model.Event;
import opennlp.model.EventStream;
import opennlp.model.ListEventStream;
import opennlp.model.TwoPassDataIndexer;

/**
 * Test for perceptron training and use with the ppa data.
 */
public class PerceptronPrepAttachTest extends TestCase {

  private static List<Event> readPpaFile(String filename) throws IOException {

    List<Event> events = new ArrayList<Event>();

    BufferedReader in = new BufferedReader(new FileReader(filename));
    String line;

    while ((line = in.readLine()) != null) {
      String[] items = line.split("\\s+");
      String label = items[5];
      String[] context = { "verb=" + items[1], "noun=" + items[2],
          "prep=" + items[3], "prep_obj=" + items[4] };
      events.add(new Event(label, context));
    }
    in.close();
    
    return events;
  }
  
  public void testPerceptronOnPrepAttachData() throws IOException {
    List<Event> trainingEvents = readPpaFile("src/test/resources/data/ppa/training");

    EventStream trainingStream = new ListEventStream(trainingEvents);

    AbstractModel model = 
      new PerceptronTrainer().trainModel(5000, new TwoPassDataIndexer(trainingStream, 1, false), 1);

    List<Event> devEvents = readPpaFile("src/test/resources/data/ppa/devset");

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

    assertEquals(0.7613270611537509, accuracy, .00001);
  }
}
