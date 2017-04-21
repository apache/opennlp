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
import java.io.InputStreamReader;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import opennlp.tools.formats.DirectorySampleStream;
import opennlp.tools.formats.convert.FileToStringSampleStream;
import opennlp.tools.formats.ontonotes.DocumentToLineStream;
import opennlp.tools.formats.ontonotes.OntoNotesParseSampleStream;
import opennlp.tools.parser.HeadRules;
import opennlp.tools.parser.Parse;
import opennlp.tools.parser.ParserCrossValidator;
import opennlp.tools.parser.ParserType;
import opennlp.tools.parser.lang.en.HeadRulesTest;
import opennlp.tools.util.ObjectStream;
import opennlp.tools.util.TrainingParameters;
import opennlp.tools.util.model.ModelUtil;

public class OntoNotes4ParserEval {

  private static ObjectStream<Parse> createParseSampleStream() throws IOException {
    ObjectStream<File> documentStream = new DirectorySampleStream(new File(
        EvalUtil.getOpennlpDataDir(), "ontonotes4/data/files/data/english"),
        file -> {
          if (file.isFile()) {
            return file.getName().endsWith(".parse");
          }

          return file.isDirectory();
        }, true);

    return new OntoNotesParseSampleStream(
        new DocumentToLineStream(new FileToStringSampleStream(
            documentStream, StandardCharsets.UTF_8)));
  }

  private static void crossEval(TrainingParameters params, HeadRules rules, double expectedScore)
      throws IOException {
    try (ObjectStream<Parse> samples = createParseSampleStream()) {
      ParserCrossValidator cv = new ParserCrossValidator("en", params, rules, ParserType.CHUNKING);
      cv.evaluate(samples, 10);

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

    try (ObjectStream<Parse> samples = createParseSampleStream()) {
      Parse sample;
      while ((sample = samples.read()) != null) {
        digest.update(sample.toString().getBytes(StandardCharsets.UTF_8));
      }

      Assert.assertEquals(new BigInteger("83833369887442127665956850482411800415"),
          new BigInteger(1, digest.digest()));
    }
  }

  @Test
  public void evalEnglishMaxent() throws IOException {

    HeadRules headRules;
    try (InputStream headRulesIn =
             HeadRulesTest.class.getResourceAsStream("/opennlp/tools/parser/en_head_rules")) {
      headRules = new opennlp.tools.parser.lang.en.HeadRules(
          new InputStreamReader(headRulesIn, "UTF-8"));
    }

    TrainingParameters params = ModelUtil.createDefaultTrainingParameters();
    params.put("build.Threads", 4);
    params.put("tagger.Threads", 4);
    params.put("chunker.Threads", 4);
    params.put("check.Threads", 4);


    crossEval(params, headRules, 0.937987617163142d);
  }
}
