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
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import org.junit.Assert;

import opennlp.tools.ml.maxent.quasinewton.QNTrainer;
import opennlp.tools.ml.naivebayes.NaiveBayesTrainer;
import opennlp.tools.ml.perceptron.PerceptronTrainer;
import opennlp.tools.util.ObjectStream;
import opennlp.tools.util.StringUtil;
import opennlp.tools.util.TrainingParameters;
import opennlp.tools.util.model.ModelUtil;

public abstract class AbstractEvalTest {

  public static final double ACCURACY_DELTA = 0.0001d;
  public static final String HASH_ALGORITHM = "MD5";

  public static void verifyTrainingData(ObjectStream<?> samples, BigInteger checksum) throws Exception {

    MessageDigest digest = MessageDigest.getInstance(HASH_ALGORITHM);

    Object sample;
    while ((sample = samples.read()) != null) {
      digest.update(sample.toString().getBytes(StandardCharsets.UTF_8));
    }

    samples.close();

    Assert.assertEquals(checksum, new BigInteger(1, digest.digest()));

  }
  
  public static void verifyFileChecksum(Path file, BigInteger checksum) throws Exception {

    MessageDigest digest = MessageDigest.getInstance(HASH_ALGORITHM);

    try (InputStream in = Files.newInputStream(file)) {
      byte[] buf = new byte[65536];
      int len;
      while ((len = in.read(buf)) > 0) {
        digest.update(buf, 0, len);
      }
    }

    Assert.assertEquals(checksum, new BigInteger(1, digest.digest()));
  }
  
  public static void verifyDirectoryChecksum(Path path, String extension, BigInteger checksum)
      throws Exception {

    MessageDigest digest = MessageDigest.getInstance(HASH_ALGORITHM);
    
    final List<Path> paths = Files.walk(path)
        .filter(Files::isRegularFile)
        .filter(p -> p.toString().endsWith(extension))
        .collect(Collectors.toList());

    // Ensure the paths are in a consistent order when
    // verifying the file checksums.
    Collections.sort(paths);
    
    for (Path p : paths) {
      try (InputStream in = Files.newInputStream(p)) {
        byte[] buf = new byte[65536];
        int len;
        while ((len = in.read(buf)) > 0) {
          digest.update(buf, 0, len);
        }
      }
    }

    Assert.assertEquals(checksum, new BigInteger(1, digest.digest()));
  }    

  public static File getOpennlpDataDir() throws FileNotFoundException {
    final String dataDirectory = System.getProperty("OPENNLP_DATA_DIR");
    if (StringUtil.isEmpty(dataDirectory)) {
      throw new IllegalArgumentException("The OPENNLP_DATA_DIR is not set.");
    }
    final File file = new File(System.getProperty("OPENNLP_DATA_DIR"));
    if (!file.exists()) {
      throw new FileNotFoundException("The OPENNLP_DATA_DIR path of " + dataDirectory + " was not found.");
    }
    return file;
  }

  public TrainingParameters createPerceptronParams() {
    TrainingParameters params = ModelUtil.createDefaultTrainingParameters();
    params.put(TrainingParameters.ALGORITHM_PARAM,
        PerceptronTrainer.PERCEPTRON_VALUE);
    params.put(TrainingParameters.CUTOFF_PARAM, 0);
    return params;
  }

  public TrainingParameters createMaxentQnParams() {
    TrainingParameters params = ModelUtil.createDefaultTrainingParameters();
    params.put(TrainingParameters.ALGORITHM_PARAM,
        QNTrainer.MAXENT_QN_VALUE);
    params.put(TrainingParameters.CUTOFF_PARAM, 0);
    return params;
  }

  public TrainingParameters createNaiveBayesParams() {
    TrainingParameters params = ModelUtil.createDefaultTrainingParameters();
    params.put(TrainingParameters.ALGORITHM_PARAM,
        NaiveBayesTrainer.NAIVE_BAYES_VALUE);
    params.put(TrainingParameters.CUTOFF_PARAM, 5);
    return params;
  }

}
