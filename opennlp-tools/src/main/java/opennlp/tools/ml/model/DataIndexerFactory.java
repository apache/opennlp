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

package opennlp.tools.ml.model;

import java.util.HashMap;
import java.util.Map;

import opennlp.tools.ml.AbstractEventTrainer;
import opennlp.tools.util.TrainingParameters;
import opennlp.tools.util.ext.ExtensionLoader;

public class DataIndexerFactory {

  public static DataIndexer getDataIndexer(TrainingParameters parameters, Map<String, String> reportMap) {
    // The default is currently a 2-Pass data index.  Is this what we really want?
    String indexerParam = parameters.getStringParameter(AbstractEventTrainer.DATA_INDEXER_PARAM,
        AbstractEventTrainer.DATA_INDEXER_TWO_PASS_VALUE);

    // allow the user to pass in a report map.  If the don't, create one.
    if (reportMap == null) {
      reportMap = new HashMap<>();
    }

    DataIndexer indexer;
    switch (indexerParam) {
      case AbstractEventTrainer.DATA_INDEXER_ONE_PASS_VALUE:
        indexer = new OnePassDataIndexer();
        break;

      case AbstractEventTrainer.DATA_INDEXER_TWO_PASS_VALUE:
        indexer = new TwoPassDataIndexer();
        break;

      case AbstractEventTrainer.DATA_INDEXER_ONE_PASS_REAL_VALUE:
        indexer = new OnePassRealValueDataIndexer();
        break;

      default:
        // if the user passes in a class name for the indexer, try to instantiate the class.
        indexer = ExtensionLoader.instantiateExtension(DataIndexer.class, indexerParam);
    }

    indexer.init(parameters, reportMap);

    return indexer;
  }
}
