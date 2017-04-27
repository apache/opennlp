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

package opennlp.tools.convert;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.List;

import org.apache.commons.io.FileUtils;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import opennlp.tools.formats.DirectorySampleStream;
import opennlp.tools.formats.convert.FileToStringSampleStream;

public class FileToStringSampleStreamTest {

  @Rule
  public TemporaryFolder directory = new TemporaryFolder();

  @Test
  public void readFileTest() throws IOException {

    final String sentence1 = "This is a sentence.";
    final String sentence2 = "This is another sentence.";
  
    List<String> sentences = Arrays.asList(sentence1, sentence2);
    
    DirectorySampleStream directorySampleStream =
        new DirectorySampleStream(directory.getRoot(), null, false);
      
    File tempFile1 = directory.newFile();
    FileUtils.writeStringToFile(tempFile1, sentence1);
    
    File tempFile2 = directory.newFile();
    FileUtils.writeStringToFile(tempFile2, sentence2);
    
    try (FileToStringSampleStream stream =
        new FileToStringSampleStream(directorySampleStream, Charset.defaultCharset())) {

      String read = stream.read();
      Assert.assertTrue(sentences.contains(read));

      read = stream.read();
      Assert.assertTrue(sentences.contains(read));
    }
  }

}
