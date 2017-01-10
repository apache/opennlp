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
import java.util.List;

import org.junit.Assert;
import org.junit.Test;

import opennlp.tools.ml.model.Event;
import opennlp.tools.util.ObjectStream;
import opennlp.tools.util.ObjectStreamUtils;

public class GISTestIndexing {

  static String[][] cntx = new String[][]{
    {"dog","cat","mouse"},
    {"text", "print", "mouse"},
    {"dog", "pig", "cat", "mouse"}
  };
  static String[] outputs = new String[]{"A","B","A"};

  /*
   * Test the GIS.trainModel(ObjectStream<Event> eventStream) method
   */
  @Test
  public void testGISTrainSignature1() throws Exception {
    List<Event> events = new ArrayList<>();
    for (int i = 0; i < cntx.length; i++) {
      events.add(new Event(outputs[i], cntx[i]));
    }
    ObjectStream<Event> eventStream = ObjectStreamUtils.createObjectStream(events);
    Assert.assertNotNull(GIS.trainModel(eventStream));
    eventStream.close();
  }

  /*
   * Test the GIS.trainModel(ObjectStream<Event> eventStream,boolean smoothing) method
   */
  @Test
  public void testGISTrainSignature2() throws Exception {
    List<Event> events = new ArrayList<>();
    for (int i = 0; i < cntx.length; i++) {
      events.add(new Event(outputs[i], cntx[i]));
    }
    ObjectStream<Event> eventStream = ObjectStreamUtils.createObjectStream(events);
    Assert.assertNotNull(GIS.trainModel(eventStream,true));
    eventStream.close();
  }
  
  /*
   * Test the GIS.trainModel(ObjectStream<Event> eventStream, int iterations, int cutoff) method
   */
  @Test
  public void testGISTrainSignature3() throws Exception {
    List<Event> events = new ArrayList<>();
    for (int i = 0;i < cntx.length;i++) {
      events.add(new Event(outputs[i], cntx[i]));
    }
    ObjectStream<Event> eventStream = ObjectStreamUtils.createObjectStream(events);
    Assert.assertNotNull(GIS.trainModel(eventStream,10,1));
    eventStream.close();
  }
 
  /*
   * Test the GIS.trainModel(ObjectStream<Event> eventStream, int iterations, int cutoff, double sigma) method
   */
  @Test
  public void testGISTrainSignature4() throws Exception {
    List<Event> events = new ArrayList<>();
    for (int i = 0;i < cntx.length;i++) {
      events.add(new Event(outputs[i], cntx[i]));
    }
    ObjectStream<Event> eventStream = ObjectStreamUtils.createObjectStream(events);
    Assert.assertNotNull(GIS.trainModel(eventStream,10,1,0.01));
    eventStream.close();
  }
  
  /*
   * Test the GIS.trainModel((ObjectStream<Event> eventStream, int iterations, int cutoff, 
   * boolean smoothing, boolean printMessagesWhileTraining)) method
   */
  @Test
  public void testGISTrainSignature5() throws Exception {
    List<Event> events = new ArrayList<>();
    for (int i = 0;i < cntx.length;i++) {
      events.add(new Event(outputs[i], cntx[i]));
    }
    ObjectStream<Event> eventStream = ObjectStreamUtils.createObjectStream(events);
    Assert.assertNotNull(GIS.trainModel(eventStream,10,1,false,false));
    eventStream.close();
  }
}
