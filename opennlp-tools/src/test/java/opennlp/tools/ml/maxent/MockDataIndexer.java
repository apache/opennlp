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

import java.io.IOException;
import java.util.Map;

import opennlp.tools.ml.model.DataIndexer;
import opennlp.tools.ml.model.Event;
import opennlp.tools.util.ObjectStream;
import opennlp.tools.util.TrainingParameters;

public class MockDataIndexer implements DataIndexer {

  @Override
  public int[][] getContexts() {
    return new int[0][0];
  }

  @Override
  public int[] getNumTimesEventsSeen() {
    return new int[0];
  }

  @Override
  public int[] getOutcomeList() {
    return new int[0];
  }

  @Override
  public String[] getPredLabels() {
    return new String[0];
  }

  @Override
  public int[] getPredCounts() {
    return new int[0];
  }

  @Override
  public String[] getOutcomeLabels() {
    // TODO Auto-generated method stub
    return new String[0];
  }

  @Override
  public float[][] getValues() {
    return new float[0][0];
  }

  @Override
  public int getNumEvents() {
    return 0;
  }

  @Override
  public void init(TrainingParameters trainParams,
      Map<String, String> reportMap) {
  }

  @Override
  public void index(ObjectStream<Event> eventStream) throws IOException {
  }

}
