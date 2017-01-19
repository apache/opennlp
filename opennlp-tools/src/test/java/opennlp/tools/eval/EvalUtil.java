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

package opennlp.tools.eval;

import java.io.File;

import opennlp.tools.ml.maxent.quasinewton.QNTrainer;
import opennlp.tools.ml.naivebayes.NaiveBayesTrainer;
import opennlp.tools.ml.perceptron.PerceptronTrainer;
import opennlp.tools.util.TrainingParameters;
import opennlp.tools.util.model.ModelUtil;

public class EvalUtil {

  static TrainingParameters createPerceptronParams() {
    TrainingParameters params = ModelUtil.createDefaultTrainingParameters();
    params.put(TrainingParameters.ALGORITHM_PARAM,
        PerceptronTrainer.PERCEPTRON_VALUE);
    params.put(TrainingParameters.CUTOFF_PARAM, "0");
    return params;
  }

  static TrainingParameters createMaxentQnParams() {
    TrainingParameters params = ModelUtil.createDefaultTrainingParameters();
    params.put(TrainingParameters.ALGORITHM_PARAM,
        QNTrainer.MAXENT_QN_VALUE);
    params.put(TrainingParameters.CUTOFF_PARAM, "0");
    return params;
  }

  static TrainingParameters createNaiveBayesParams() {
    TrainingParameters params = ModelUtil.createDefaultTrainingParameters();
    params.put(TrainingParameters.ALGORITHM_PARAM,
        NaiveBayesTrainer.NAIVE_BAYES_VALUE);
    params.put(TrainingParameters.CUTOFF_PARAM, "5");
    return params;
  }

  public static File getOpennlpDataDir() {
    return new File(System.getProperty("OPENNLP_DATA_DIR"));
  }
}
