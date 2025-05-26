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

package opennlp.tools.ml.naivebayes;

import java.util.ArrayList;
import java.util.List;

import opennlp.tools.ml.model.Event;
import opennlp.tools.util.ObjectStream;
import opennlp.tools.util.ObjectStreamUtils;

public class AbstractNaiveBayesTest {

  protected ObjectStream<Event> createTrainingStream() {
    List<Event> trainingEvents = new ArrayList<>();

    String label1 = "politics";
    String[] context1 = {"bow=the", "bow=united", "bow=nations"};
    trainingEvents.add(new Event(label1, context1));

    String label2 = "politics";
    String[] context2 = {"bow=the", "bow=united", "bow=states", "bow=and"};
    trainingEvents.add(new Event(label2, context2));

    String label3 = "sports";
    String[] context3 = {"bow=manchester", "bow=united"};
    trainingEvents.add(new Event(label3, context3));

    String label4 = "sports";
    String[] context4 = {"bow=manchester", "bow=and", "bow=barca"};
    trainingEvents.add(new Event(label4, context4));

    return ObjectStreamUtils.createObjectStream(trainingEvents);
  }
}
