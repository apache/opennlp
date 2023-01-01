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

package opennlp.morfologik.builder;

import java.io.File;
import java.nio.file.Path;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import opennlp.morfologik.AbstractMorfologikTest;
import opennlp.morfologik.lemmatizer.MorfologikLemmatizer;

/**
 * Tests for the {@link MorfologikDictionaryBuilder} class.
 */
public class MorfologikDictionaryBuilderTest extends AbstractMorfologikTest {

  @Test
  public void testMultithreading() throws Exception {
    // Part 1: compile a FSA lemma dictionary
    // we need the tabular dictionary. It is mandatory to have info
    //  file with same name, but .info extension

    // this will build a binary dictionary located in compiledLemmaDictionary

    final Path rawLemmaDictionary =
        new File(getResource("/dictionaryWithLemma.txt").getFile()).toPath();
    Path compiledLemmaDictionary = new MorfologikDictionaryBuilder().build(rawLemmaDictionary);
    // Part 2: load a MorfologikLemmatizer and use it
    MorfologikLemmatizer lemmatizer = new MorfologikLemmatizer(compiledLemmaDictionary);

    String[] toks = {"casa", "casa"};
    String[] tags = {"NOUN", "V"};

    Runnable runnable = () -> {
      String[] lemmas = lemmatizer.lemmatize(toks, tags);
      Assertions.assertEquals("casa", lemmas[0]);
      Assertions.assertEquals("casar", lemmas[1]);
    };
    ExecutorService executorService = Executors.newFixedThreadPool(2);
    for (int i = 0; i < 1000; i++) {
      executorService.execute(runnable);
    }
    executorService.shutdown();
    executorService.awaitTermination(1, TimeUnit.SECONDS);
  }

  @Test
  public void testBuildDictionary() throws Exception {
    Path output = createMorfologikDictionary();
    MorfologikLemmatizer ml = new MorfologikLemmatizer(output);
    Assertions.assertNotNull(ml);
    output.toFile().deleteOnExit();
  }

}
