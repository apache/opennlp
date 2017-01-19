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
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;

import morfologik.stemming.DictionaryMetadata;

import org.junit.Assert;
import org.junit.Test;

import opennlp.morfologik.lemmatizer.MorfologikLemmatizer;

public class POSDictionayBuilderTest {

  public static Path createMorfologikDictionary() throws Exception {
    Path tabFilePath = File.createTempFile(
        POSDictionayBuilderTest.class.getName(), ".txt").toPath();
    Path infoFilePath = DictionaryMetadata.getExpectedMetadataLocation(tabFilePath);

    Files.copy(POSDictionayBuilderTest.class.getResourceAsStream(
        "/dictionaryWithLemma.txt"), tabFilePath, StandardCopyOption.REPLACE_EXISTING);
    Files.copy(POSDictionayBuilderTest.class.getResourceAsStream(
        "/dictionaryWithLemma.info"), infoFilePath, StandardCopyOption.REPLACE_EXISTING);

    MorfologikDictionayBuilder builder = new MorfologikDictionayBuilder();

    return builder.build(tabFilePath);
  }

  public static void main(String[] args) throws Exception {

    // Part 1: compile a FSA lemma dictionary
    // we need the tabular dictionary. It is mandatory to have info
    //  file with same name, but .info extension
    Path textLemmaDictionary = Paths.get(
        "/Users/wcolen/git/opennlp/opennlp-morfologik-addon/src/test/resources/dictionaryWithLemma.txt");

    // this will build a binary dictionary located in compiledLemmaDictionary
    Path compiledLemmaDictionary = new MorfologikDictionayBuilder().build(textLemmaDictionary);

    // Part 2: load a MorfologikLemmatizer and use it
    MorfologikLemmatizer lemmatizer = new MorfologikLemmatizer(compiledLemmaDictionary);

    String[] toks = {"casa", "casa"};
    String[] tags = {"NOUN", "V"};

    String[] lemmas = lemmatizer.lemmatize(toks, tags);
    System.out.println(Arrays.toString(lemmas)); // outputs [casa, casar]
  }

  @Test
  public void testBuildDictionary() throws Exception {
    Path output = createMorfologikDictionary();
    MorfologikLemmatizer ml = new MorfologikLemmatizer(output);
    Assert.assertNotNull(ml);
  }

}
