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
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import opennlp.tools.formats.DirectorySampleStream;
import opennlp.tools.formats.convert.FileToStringSampleStream;
import opennlp.tools.formats.ontonotes.OntoNotesNameSampleStream;
import opennlp.tools.namefind.NameSample;
import opennlp.tools.namefind.NameSampleTypeFilter;
import opennlp.tools.namefind.TokenNameFinderCrossValidator;
import opennlp.tools.namefind.TokenNameFinderFactory;
import opennlp.tools.util.ObjectStream;
import opennlp.tools.util.TrainingParameters;
import opennlp.tools.util.model.ModelUtil;

public class OntoNotes4NameFinderEval {

  private static ObjectStream<NameSample> createNameSampleStream() throws IOException {
    ObjectStream<File> documentStream = new DirectorySampleStream(new File(
        EvalUtil.getOpennlpDataDir(), "ontonotes4/data/files/data/english"),
        file -> {
          if (file.isFile()) {
            return file.getName().endsWith(".name");
          }

          return file.isDirectory();
        }, true);

    return new OntoNotesNameSampleStream(new FileToStringSampleStream(
        documentStream, StandardCharsets.UTF_8));
  }

  private static void crossEval(TrainingParameters params, String type, double expectedScore)
      throws IOException {
    try (ObjectStream<NameSample> samples = createNameSampleStream()) {

      TokenNameFinderCrossValidator cv = new TokenNameFinderCrossValidator("en", null,
          params, new TokenNameFinderFactory());

      ObjectStream<NameSample> filteredSamples;
      if (type != null) {
        filteredSamples = new NameSampleTypeFilter(new String[] {type}, samples);
      }
      else {
        filteredSamples = samples;
      }

      cv.evaluate(filteredSamples, 10);

      Assert.assertEquals(expectedScore, cv.getFMeasure().getFMeasure(), 0.001d);
    }
  }

  @BeforeClass
  public static void verifyTrainingData() throws IOException {
    MessageDigest digest;
    try {
      digest = MessageDigest.getInstance("MD5");
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }

    try (ObjectStream<NameSample> samples = createNameSampleStream()) {
      NameSample sample;
      while ((sample = samples.read()) != null) {
        digest.update(sample.toString().getBytes(StandardCharsets.UTF_8));
      }

      Assert.assertEquals(new BigInteger("168206908604555450993491898907821588182"),
          new BigInteger(1, digest.digest()));
    }
  }

  @Test
  public void evalEnglishPersonNameFinder() throws IOException {
    TrainingParameters params = ModelUtil.createDefaultTrainingParameters();
    crossEval(params, "person", 0.8286204642039883d);
  }

  @Test
  public void evalEnglishDateNameFinder() throws IOException {
    TrainingParameters params = ModelUtil.createDefaultTrainingParameters();
    crossEval(params, "date", 0.8065329969459567);
  }

  @Test
  public void evalAllTypesNameFinder() throws IOException {
    TrainingParameters params = ModelUtil.createDefaultTrainingParameters();
    crossEval(params, null, 0.8061722553169423d);
  }
}
