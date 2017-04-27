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

package opennlp.tools.cmdline;

import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import org.junit.Assert;
import org.junit.Test;

import opennlp.tools.cmdline.namefind.TokenNameFinderTool;
import opennlp.tools.namefind.NameFinderME;
import opennlp.tools.namefind.NameSample;
import opennlp.tools.namefind.NameSampleDataStream;
import opennlp.tools.namefind.TokenNameFinderFactory;
import opennlp.tools.namefind.TokenNameFinderModel;
import opennlp.tools.util.MockInputStreamFactory;
import opennlp.tools.util.ObjectStream;
import opennlp.tools.util.PlainTextByLineStream;
import opennlp.tools.util.TrainingParameters;

public class TokenNameFinderToolTest {

  @Test
  public void run() throws IOException {

    File model1 = trainModel();

    String[] args = new String[]{model1.getAbsolutePath()};
    
    final String in = "It is Stefanie Schmidt.\n\nNothing in this sentence.";
    InputStream stream = new ByteArrayInputStream(in.getBytes(StandardCharsets.UTF_8));
    
    System.setIn(stream);
    
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    PrintStream ps = new PrintStream(baos);
    System.setOut(ps);

    TokenNameFinderTool tool = new TokenNameFinderTool();
    tool.run(args);
    
    final String content = new String(baos.toByteArray(), StandardCharsets.UTF_8);
    Assert.assertTrue(content.contains("It is <START:person> Stefanie Schmidt. <END>"));
    
  }
  
  @Test(expected = TerminateToolException.class)
  public void invalidModel() {

    String[] args = new String[]{"invalidmodel.bin"};

    TokenNameFinderTool tool = new TokenNameFinderTool();
    tool.run(args);

  }
  
  @Test()
  public void usage() {

    String[] args = new String[]{};
    
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    PrintStream ps = new PrintStream(baos);
    System.setOut(ps);

    TokenNameFinderTool tool = new TokenNameFinderTool();
    tool.run(args);

    final String content = new String(baos.toByteArray(), StandardCharsets.UTF_8);
    Assert.assertEquals(tool.getHelp(), content.trim());
    
  }
  
  private File trainModel() throws IOException {

    ObjectStream<String> lineStream =
        new PlainTextByLineStream(new MockInputStreamFactory(
            new File("opennlp/tools/namefind/AnnotatedSentencesWithTypes.txt")),
            StandardCharsets.ISO_8859_1);

    TrainingParameters params = new TrainingParameters();
    params.put(TrainingParameters.ITERATIONS_PARAM, 70);
    params.put(TrainingParameters.CUTOFF_PARAM, 1);
    
    TokenNameFinderModel model;

    TokenNameFinderFactory nameFinderFactory = new TokenNameFinderFactory();

    try (ObjectStream<NameSample> sampleStream = new NameSampleDataStream(lineStream)) {
      model = NameFinderME.train("en", null, sampleStream, params,
          nameFinderFactory);
    }
    
    File modelFile = File.createTempFile("model", ".bin");
    
    try (BufferedOutputStream modelOut =
             new BufferedOutputStream(new FileOutputStream(modelFile))) {
      model.serialize(modelOut);
    }
    
    return modelFile;
  }
  
}
