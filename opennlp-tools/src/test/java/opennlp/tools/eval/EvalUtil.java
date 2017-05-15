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
import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import org.junit.Assert;

import opennlp.tools.ml.maxent.quasinewton.QNTrainer;
import opennlp.tools.ml.naivebayes.NaiveBayesTrainer;
import opennlp.tools.ml.perceptron.PerceptronTrainer;
import opennlp.tools.util.TrainingParameters;
import opennlp.tools.util.model.ModelUtil;

public class EvalUtil {

  static final double ACCURACY_DELTA = 0.0001d;

  static TrainingParameters createPerceptronParams() {
    TrainingParameters params = ModelUtil.createDefaultTrainingParameters();
    params.put(TrainingParameters.ALGORITHM_PARAM,
        PerceptronTrainer.PERCEPTRON_VALUE);
    params.put(TrainingParameters.CUTOFF_PARAM, 0);
    return params;
  }

  static TrainingParameters createMaxentQnParams() {
    TrainingParameters params = ModelUtil.createDefaultTrainingParameters();
    params.put(TrainingParameters.ALGORITHM_PARAM,
        QNTrainer.MAXENT_QN_VALUE);
    params.put(TrainingParameters.CUTOFF_PARAM, 0);
    return params;
  }

  static TrainingParameters createNaiveBayesParams() {
    TrainingParameters params = ModelUtil.createDefaultTrainingParameters();
    params.put(TrainingParameters.ALGORITHM_PARAM,
        NaiveBayesTrainer.NAIVE_BAYES_VALUE);
    params.put(TrainingParameters.CUTOFF_PARAM, 5);
    return params;
  }

  public static File getOpennlpDataDir() {
    return new File(System.getProperty("OPENNLP_DATA_DIR"));
  }

  static MessageDigest createDigest() {
    try {
      return MessageDigest.getInstance("MD5");
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }

  static void verifyFileChecksum(Path file, BigInteger checksum) throws IOException {
    MessageDigest digest = createDigest();

    try (InputStream in = Files.newInputStream(file)) {
      byte[] buf = new byte[65536];
      int len;
      while ((len = in.read(buf)) > 0) {
        digest.update(buf, 0, len);
      }
    }

    Assert.assertEquals(checksum, new BigInteger(1, digest.digest()));
  }
}
