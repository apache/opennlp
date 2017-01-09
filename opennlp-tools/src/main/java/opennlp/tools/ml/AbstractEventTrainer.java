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

package opennlp.tools.ml;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import opennlp.tools.ml.model.AbstractDataIndexer;
import opennlp.tools.ml.model.DataIndexer;
import opennlp.tools.ml.model.Event;
import opennlp.tools.ml.model.HashSumEventStream;
import opennlp.tools.ml.model.MaxentModel;
import opennlp.tools.ml.model.OnePassDataIndexer;
import opennlp.tools.ml.model.TwoPassDataIndexer;
import opennlp.tools.util.ObjectStream;

public abstract class AbstractEventTrainer extends AbstractTrainer implements EventTrainer {

  public static final String DATA_INDEXER_PARAM = "DataIndexer";
  public static final String DATA_INDEXER_ONE_PASS_VALUE = "OnePass";
  public static final String DATA_INDEXER_TWO_PASS_VALUE = "TwoPass";

  public AbstractEventTrainer() {
  }

  @Override
  public boolean isValid() {
    if (!super.isValid()) {
      return false;
    }

    String dataIndexer = parameters.getStringParam(DATA_INDEXER_PARAM,
        DATA_INDEXER_TWO_PASS_VALUE);

    if (dataIndexer != null) {
      if (!(DATA_INDEXER_ONE_PASS_VALUE.equals(dataIndexer) || DATA_INDEXER_TWO_PASS_VALUE
          .equals(dataIndexer))) {
        return false;
      }
    }
    // TODO: Check data indexing ...

    return true;
  }

  public abstract boolean isSortAndMerge();

  public DataIndexer getDataIndexer(ObjectStream<Event> events) throws IOException {

    String dataIndexerName = parameters.getStringParam(DATA_INDEXER_PARAM,
        DATA_INDEXER_TWO_PASS_VALUE);
    Map<String,String> indexParams = new HashMap<String, String>();
    indexParams.put(AbstractDataIndexer.CUTOFF_PARAM, Integer.toString(getCutoff()));
    indexParams.put(AbstractDataIndexer.SORT_PARAM, Boolean.toString(isSortAndMerge()));

    DataIndexer indexer;

    if (DATA_INDEXER_ONE_PASS_VALUE.equals(dataIndexerName)) {
      indexer = new OnePassDataIndexer();
    } else if (DATA_INDEXER_TWO_PASS_VALUE.equals(dataIndexerName)) {
      indexer = new TwoPassDataIndexer();
    } else {
      throw new IllegalStateException("Unexpected data indexer name: "
          + dataIndexerName);
    }
    indexer.init(indexParams, parameters.getReportMap());
    indexer.index(events);
    return indexer;
  }

  public abstract MaxentModel doTrain(DataIndexer indexer) throws IOException;

  public final MaxentModel train(DataIndexer indexer) throws IOException {
    if (!isValid()) {
      throw new IllegalArgumentException("trainParams are not valid!");
    }
    MaxentModel model = doTrain(indexer);
    parameters.addToReport(AbstractTrainer.TRAINER_TYPE_PARAM, EventTrainer.EVENT_VALUE);
    return model;
  }

  public final MaxentModel train(ObjectStream<Event> events) throws IOException {

    if (!isValid()) {
      throw new IllegalArgumentException("trainParams are not valid!");
    }

    HashSumEventStream hses = new HashSumEventStream(events);
    DataIndexer indexer = getDataIndexer(hses);

    return train(indexer);
  }
}
