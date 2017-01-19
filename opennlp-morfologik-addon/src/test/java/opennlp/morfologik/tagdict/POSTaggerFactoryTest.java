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

package opennlp.morfologik.tagdict;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;

import org.junit.Assert;
import org.junit.Test;

import opennlp.morfologik.builder.POSDictionayBuilderTest;
import opennlp.tools.cmdline.CmdLineUtil;
import opennlp.tools.postag.POSModel;
import opennlp.tools.postag.POSSample;
import opennlp.tools.postag.POSTaggerFactory;
import opennlp.tools.postag.POSTaggerME;
import opennlp.tools.postag.TagDictionary;
import opennlp.tools.postag.WordTagSampleStream;
import opennlp.tools.util.MarkableFileInputStreamFactory;
import opennlp.tools.util.ObjectStream;
import opennlp.tools.util.PlainTextByLineStream;
import opennlp.tools.util.TrainingParameters;
import opennlp.tools.util.model.ModelType;

/**
 * Tests for the {@link POSTaggerFactory} class.
 */
public class POSTaggerFactoryTest {

  private static ObjectStream<POSSample> createSampleStream()
      throws IOException {
    MarkableFileInputStreamFactory sampleDataIn = new MarkableFileInputStreamFactory(
        new File(POSTaggerFactory.class.getResource("/AnnotatedSentences.txt").getFile()));

    ObjectStream<String> lineStream = null;
    try {
      lineStream = new PlainTextByLineStream(sampleDataIn, "UTF-8");
    } catch (IOException ex) {
      CmdLineUtil.handleCreateObjectStreamError(ex);
    }

    return new WordTagSampleStream(lineStream);
  }

  private static POSModel trainPOSModel(ModelType type, POSTaggerFactory factory)
      throws IOException {
    return POSTaggerME.train("en", createSampleStream(),
        TrainingParameters.defaultParams(), factory);
  }

  @Test
  public void testPOSTaggerWithCustomFactory() throws Exception {

    Path dictionary = POSDictionayBuilderTest.createMorfologikDictionary();
    POSTaggerFactory inFactory = new MorfologikPOSTaggerFactory();
    TagDictionary inDict = inFactory.createTagDictionary(dictionary.toFile());
    inFactory.setTagDictionary(inDict);

    POSModel posModel = trainPOSModel(ModelType.MAXENT, inFactory);

    POSTaggerFactory factory = posModel.getFactory();
    Assert.assertTrue(factory.getTagDictionary() instanceof MorfologikTagDictionary);

    factory = null;

    ByteArrayOutputStream out = new ByteArrayOutputStream();
    posModel.serialize(out);
    ByteArrayInputStream in = new ByteArrayInputStream(out.toByteArray());

    POSModel fromSerialized = new POSModel(in);

    factory = fromSerialized.getFactory();
    Assert.assertTrue(factory.getTagDictionary() instanceof MorfologikTagDictionary);

    Assert.assertEquals(2, factory.getTagDictionary().getTags("casa").length);
  }

}