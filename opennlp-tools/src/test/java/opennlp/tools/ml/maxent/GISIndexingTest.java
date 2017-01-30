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

package opennlp.tools.ml.maxent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Assert;
import org.junit.Test;

import opennlp.tools.ml.AbstractEventTrainer;
import opennlp.tools.ml.EventTrainer;
import opennlp.tools.ml.TrainerFactory;
import opennlp.tools.ml.maxent.quasinewton.QNTrainer;
import opennlp.tools.ml.model.AbstractDataIndexer;
import opennlp.tools.ml.model.DataIndexer;
import opennlp.tools.ml.model.DataIndexerFactory;
import opennlp.tools.ml.model.Event;
import opennlp.tools.util.ObjectStream;
import opennlp.tools.util.ObjectStreamUtils;
import opennlp.tools.util.TrainingParameters;

public class GISIndexingTest {

  private static String[][] cntx = new String[][]{
    {"dog","cat","mouse"},
    {"text", "print", "mouse"},
    {"dog", "pig", "cat", "mouse"}
  };
  private static String[] outputs = new String[]{"A","B","A"};

  private ObjectStream<Event> createEventStream() {
    List<Event> events = new ArrayList<>();
    for (int i = 0; i < cntx.length; i++) {
      events.add(new Event(outputs[i], cntx[i]));
    }
    return ObjectStreamUtils.createObjectStream(events);
  }
  
  /*
   * Test the GIS.trainModel(ObjectStream<Event> eventStream) method
   */
  @Test
  public void testGISTrainSignature1() throws Exception {
    ObjectStream<Event> eventStream = createEventStream();
    Assert.assertNotNull(GIS.trainModel(eventStream));
    eventStream.close();
  }

  /*
   * Test the GIS.trainModel(ObjectStream<Event> eventStream,boolean smoothing) method
   */
  @Test
  public void testGISTrainSignature2() throws Exception {
    ObjectStream<Event> eventStream = createEventStream();
    Assert.assertNotNull(GIS.trainModel(eventStream,true));
    eventStream.close();
  }
  
  /*
   * Test the GIS.trainModel(ObjectStream<Event> eventStream, int iterations, int cutoff) method
   */
  @Test
  public void testGISTrainSignature3() throws Exception {
    ObjectStream<Event> eventStream = createEventStream();
    Assert.assertNotNull(GIS.trainModel(eventStream,10,1));
    eventStream.close();
  }
 
  /*
   * Test the GIS.trainModel(ObjectStream<Event> eventStream, int iterations, int cutoff, double sigma) method
   */
  @Test
  public void testGISTrainSignature4() throws Exception {
    ObjectStream<Event> eventStream = createEventStream();
    Assert.assertNotNull(GIS.trainModel(eventStream,10,1,0.01));
    eventStream.close();
  }
  
  /*
   * Test the GIS.trainModel((ObjectStream<Event> eventStream, int iterations, int cutoff, 
   * boolean smoothing, boolean printMessagesWhileTraining)) method
   */
  @Test
  public void testGISTrainSignature5() throws Exception {
    ObjectStream<Event> eventStream = createEventStream();
    Assert.assertNotNull(GIS.trainModel(eventStream,10,1,false,false));
    eventStream.close();
  }
  
  @Test
  public void testIndexingWithTrainingParameters() throws Exception {
    ObjectStream<Event> eventStream = createEventStream();
    
    TrainingParameters parameters = TrainingParameters.defaultParams();
    // by default we are using GIS/EventTrainer/Cutoff of 5/100 iterations
    parameters.put(TrainingParameters.ITERATIONS_PARAM, "10");
    parameters.put(AbstractEventTrainer.DATA_INDEXER_PARAM, AbstractEventTrainer.DATA_INDEXER_ONE_PASS_VALUE);
    parameters.put(AbstractEventTrainer.CUTOFF_PARAM, "1");
    // note: setting the SORT_PARAM to true is the default, so it is not really needed
    parameters.put(AbstractDataIndexer.SORT_PARAM, "true");

    // guarantee that you have a GIS trainer...
    EventTrainer trainer =
        TrainerFactory.getEventTrainer(parameters.getSettings(), new HashMap<>());
    Assert.assertEquals("opennlp.tools.ml.maxent.GISTrainer", trainer.getClass().getName());
    AbstractEventTrainer aeTrainer = (AbstractEventTrainer)trainer;
    // guarantee that you have a OnePassDataIndexer ...
    DataIndexer di = aeTrainer.getDataIndexer(eventStream);
    Assert.assertEquals("opennlp.tools.ml.model.OnePassDataIndexer", di.getClass().getName());
    Assert.assertEquals(3, di.getNumEvents());
    Assert.assertEquals(2, di.getOutcomeLabels().length);
    Assert.assertEquals(6, di.getPredLabels().length);

    // change the parameters and try again...

    eventStream.reset();
 
    parameters.put(TrainingParameters.ALGORITHM_PARAM, QNTrainer.MAXENT_QN_VALUE);
    parameters.put(AbstractEventTrainer.DATA_INDEXER_PARAM, AbstractEventTrainer.DATA_INDEXER_TWO_PASS_VALUE);
    parameters.put(AbstractEventTrainer.CUTOFF_PARAM, "2");
    
    trainer = TrainerFactory.getEventTrainer(parameters.getSettings(), new HashMap<>());
    Assert.assertEquals("opennlp.tools.ml.maxent.quasinewton.QNTrainer", trainer.getClass().getName());
    aeTrainer = (AbstractEventTrainer)trainer;
    di = aeTrainer.getDataIndexer(eventStream);
    Assert.assertEquals("opennlp.tools.ml.model.TwoPassDataIndexer", di.getClass().getName());
    
    eventStream.close();
  }
  
  @Test
  public void testIndexingFactory() throws Exception {
    Map<String,String> myReportMap = new HashMap<>();
    ObjectStream<Event> eventStream = createEventStream();

    // set the cutoff to 1 for this test.
    TrainingParameters parameters = new TrainingParameters();
    parameters.put(AbstractDataIndexer.CUTOFF_PARAM, "1");
    
    // test with a 1 pass data indexer...
    parameters.put(AbstractEventTrainer.DATA_INDEXER_PARAM, AbstractEventTrainer.DATA_INDEXER_ONE_PASS_VALUE);
    DataIndexer di = DataIndexerFactory.getDataIndexer(parameters, myReportMap);
    Assert.assertEquals("opennlp.tools.ml.model.OnePassDataIndexer", di.getClass().getName());
    di.index(eventStream);
    Assert.assertEquals(3, di.getNumEvents());
    Assert.assertEquals(2, di.getOutcomeLabels().length);
    Assert.assertEquals(6, di.getPredLabels().length);

    eventStream.reset();
    
    // test with a 2-pass data indexer...
    parameters.put(AbstractEventTrainer.DATA_INDEXER_PARAM, AbstractEventTrainer.DATA_INDEXER_TWO_PASS_VALUE);
    di = DataIndexerFactory.getDataIndexer(parameters, myReportMap);
    Assert.assertEquals("opennlp.tools.ml.model.TwoPassDataIndexer", di.getClass().getName());
    di.index(eventStream);
    Assert.assertEquals(3, di.getNumEvents());
    Assert.assertEquals(2, di.getOutcomeLabels().length);
    Assert.assertEquals(6, di.getPredLabels().length);

    // the rest of the test doesn't actually index, so we can close the eventstream.
    eventStream.close();
    
    // test with a 1-pass Real value dataIndexer
    parameters.put(AbstractEventTrainer.DATA_INDEXER_PARAM, 
        AbstractEventTrainer.DATA_INDEXER_ONE_PASS_REAL_VALUE);
    di = DataIndexerFactory.getDataIndexer(parameters, myReportMap);
    Assert.assertEquals("opennlp.tools.ml.model.OnePassRealValueDataIndexer", di.getClass().getName());
    
    
    // test with an UNRegistered MockIndexer
    parameters.put(AbstractEventTrainer.DATA_INDEXER_PARAM, "opennlp.tools.ml.maxent.MockDataIndexer");    
    di = DataIndexerFactory.getDataIndexer(parameters, myReportMap);
    Assert.assertEquals("opennlp.tools.ml.maxent.MockDataIndexer", di.getClass().getName());
  }
}
