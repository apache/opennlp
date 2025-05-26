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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import opennlp.tools.formats.ResourceAsStreamFactory;
import opennlp.tools.util.ObjectStream;
import opennlp.tools.util.PlainTextByLineStream;
import opennlp.tools.util.TrainingParameters;
import opennlp.tools.util.model.ModelType;

/**
 * Tests for the {@link ChunkerFactory} class.
 */
public class ChunkerFactoryTest {

  private static ObjectStream<ChunkSample> createSampleStream()
      throws IOException {
    ResourceAsStreamFactory in = new ResourceAsStreamFactory(
        ChunkerFactoryTest.class, "/opennlp/tools/chunker/test.txt");

    return new ChunkSampleStream(
        new PlainTextByLineStream(in, StandardCharsets.UTF_8));
  }

  private static ChunkerModel trainModel(ModelType type, ChunkerFactory factory)
      throws IOException {
    return ChunkerME.train("eng", createSampleStream(),
        TrainingParameters.defaultParams(), factory);
  }

  @Test
  void testDefaultFactory() throws IOException {

    ChunkerModel model = trainModel(ModelType.MAXENT, new ChunkerFactory());

    ChunkerFactory factory = model.getFactory();
    Assertions.assertInstanceOf(DefaultChunkerContextGenerator.class, factory.getContextGenerator());
    Assertions.assertInstanceOf(DefaultChunkerSequenceValidator.class, factory.getSequenceValidator());

    ByteArrayOutputStream out = new ByteArrayOutputStream();
    model.serialize(out);
    ByteArrayInputStream in = new ByteArrayInputStream(out.toByteArray());

    ChunkerModel fromSerialized = new ChunkerModel(in);

    factory = fromSerialized.getFactory();
    Assertions.assertInstanceOf(DefaultChunkerContextGenerator.class, factory.getContextGenerator());
    Assertions.assertInstanceOf(DefaultChunkerSequenceValidator.class, factory.getSequenceValidator());
  }


  @Test
  void testDummyFactory() throws IOException {

    ChunkerModel model = trainModel(ModelType.MAXENT, new DummyChunkerFactory());

    DummyChunkerFactory factory = (DummyChunkerFactory) model.getFactory();
    Assertions.assertInstanceOf(DummyChunkerFactory.DummyContextGenerator.class,
            factory.getContextGenerator());
    Assertions.assertInstanceOf(DummyChunkerFactory.DummySequenceValidator.class,
            factory.getSequenceValidator());


    ByteArrayOutputStream out = new ByteArrayOutputStream();
    model.serialize(out);
    ByteArrayInputStream in = new ByteArrayInputStream(out.toByteArray());

    ChunkerModel fromSerialized = new ChunkerModel(in);

    factory = (DummyChunkerFactory) fromSerialized.getFactory();
    Assertions.assertInstanceOf(DefaultChunkerContextGenerator.class, factory.getContextGenerator());
    Assertions.assertInstanceOf(DefaultChunkerSequenceValidator.class, factory.getSequenceValidator());


    ChunkerME chunker = new ChunkerME(model);

    String[] toks1 = {"Rockwell", "said", "the", "agreement", "calls", "for",
        "it", "to", "supply", "200", "additional", "so-called", "shipsets",
        "for", "the", "planes", "."};

    String[] tags1 = {"NNP", "VBD", "DT", "NN", "VBZ", "IN", "PRP", "TO", "VB",
        "CD", "JJ", "JJ", "NNS", "IN", "DT", "NNS", "."};


    chunker.chunk(toks1, tags1);

  }
}
