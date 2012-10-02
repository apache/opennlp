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

package opennlp.tools.chunker;

import static org.junit.Assert.*;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;

import opennlp.tools.util.ObjectStream;
import opennlp.tools.util.PlainTextByLineStream;
import opennlp.tools.util.TrainingParameters;
import opennlp.tools.util.model.ModelType;

import org.junit.Test;

/**
 * Tests for the {@link ChunkerFactory} class.
 */
public class ChunkerFactoryTest {

  private static ObjectStream<ChunkSample> createSampleStream()
      throws IOException {
    InputStream in = ChunkerFactoryTest.class.getClassLoader()
        .getResourceAsStream("opennlp/tools/chunker/test.txt");
    Reader sentences = new InputStreamReader(in);

    ChunkSampleStream stream = new ChunkSampleStream(new PlainTextByLineStream(
        sentences));
    return stream;
  }
  
  static ChunkerModel trainModel(ModelType type, ChunkerFactory factory)
      throws IOException {
    return ChunkerME.train("en", createSampleStream(),
        TrainingParameters.defaultParams(), factory);
  }
  
  @Test
  public void testDefaultFactory() throws IOException {

    ChunkerModel model = trainModel(ModelType.MAXENT, new ChunkerFactory());

    ChunkerFactory factory = model.getFactory();
    assertTrue(factory.getContextGenerator() instanceof DefaultChunkerContextGenerator);
    assertTrue(factory.getSequenceValidator() instanceof DefaultChunkerSequenceValidator);

    ByteArrayOutputStream out = new ByteArrayOutputStream();
    model.serialize(out);
    ByteArrayInputStream in = new ByteArrayInputStream(out.toByteArray());

    ChunkerModel fromSerialized = new ChunkerModel(in);

    factory = fromSerialized.getFactory();
    assertTrue(factory.getContextGenerator() instanceof DefaultChunkerContextGenerator);
    assertTrue(factory.getSequenceValidator() instanceof DefaultChunkerSequenceValidator);
  }

  
  @Test
  public void testDummyFactory() throws IOException {

    ChunkerModel model = trainModel(ModelType.MAXENT, new DummyChunkerFactory());

    DummyChunkerFactory factory = (DummyChunkerFactory) model.getFactory();
    assertTrue(factory instanceof DummyChunkerFactory);
    assertTrue(factory.getContextGenerator() instanceof DummyChunkerFactory.DummyContextGenerator);
    assertTrue(factory.getSequenceValidator() instanceof DummyChunkerFactory.DummySequenceValidator);
    
    
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    model.serialize(out);
    ByteArrayInputStream in = new ByteArrayInputStream(out.toByteArray());

    ChunkerModel fromSerialized = new ChunkerModel(in);

    factory = (DummyChunkerFactory)fromSerialized.getFactory();
    assertTrue(factory.getContextGenerator() instanceof DefaultChunkerContextGenerator);
    assertTrue(factory.getSequenceValidator() instanceof DefaultChunkerSequenceValidator);
    
    
    ChunkerME chunker = new ChunkerME(model);
    
    String[] toks1 = { "Rockwell", "said", "the", "agreement", "calls", "for",
        "it", "to", "supply", "200", "additional", "so-called", "shipsets",
        "for", "the", "planes", "." };

    String[] tags1 = { "NNP", "VBD", "DT", "NN", "VBZ", "IN", "PRP", "TO", "VB",
        "CD", "JJ", "JJ", "NNS", "IN", "DT", "NNS", "." };
    
    
    chunker.chunk(toks1, tags1);
    
  }
}
