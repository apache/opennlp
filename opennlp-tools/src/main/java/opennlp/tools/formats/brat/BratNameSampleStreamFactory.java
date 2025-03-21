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

package opennlp.tools.formats.brat;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import opennlp.tools.cmdline.ArgumentParser;
import opennlp.tools.cmdline.ArgumentParser.OptionalParameter;
import opennlp.tools.cmdline.ArgumentParser.ParameterDescription;
import opennlp.tools.cmdline.StreamFactoryRegistry;
import opennlp.tools.cmdline.TerminateToolException;
import opennlp.tools.formats.AbstractSampleStreamFactory;
import opennlp.tools.namefind.NameSample;
import opennlp.tools.sentdetect.NewlineSentenceDetector;
import opennlp.tools.sentdetect.SentenceDetector;
import opennlp.tools.sentdetect.SentenceDetectorME;
import opennlp.tools.sentdetect.SentenceModel;
import opennlp.tools.tokenize.SimpleTokenizer;
import opennlp.tools.tokenize.Tokenizer;
import opennlp.tools.tokenize.TokenizerME;
import opennlp.tools.tokenize.TokenizerModel;
import opennlp.tools.tokenize.WhitespaceTokenizer;
import opennlp.tools.util.ObjectStream;

/**
 * <b>Note:</b> Do not use this class, internal use only!
 *
 * @see BratNameSampleStream
 */
public class BratNameSampleStreamFactory
        extends AbstractSampleStreamFactory<NameSample, BratNameSampleStreamFactory.Parameters> {

  public interface Parameters {
    @ParameterDescription(valueName = "bratDataDir", description = "location of brat data dir")
    File getBratDataDir();

    @ParameterDescription(valueName = "annConfFile")
    File getAnnotationConfig();

    @ParameterDescription(valueName = "modelFile")
    @OptionalParameter
    File getSentenceDetectorModel();

    @ParameterDescription(valueName = "modelFile")
    @OptionalParameter
    File getTokenizerModel();

    @ParameterDescription(valueName = "name")
    @OptionalParameter
    String getRuleBasedTokenizer();

    @ParameterDescription(valueName = "value")
    @OptionalParameter(defaultValue = "false")
    Boolean getRecursive();

    @ParameterDescription(valueName = "names")
    @OptionalParameter
    String getNameTypes();
  }

  protected BratNameSampleStreamFactory() {
    super(Parameters.class);
  }

  public static void registerFactory() {
    StreamFactoryRegistry.registerFactory(NameSample.class, "brat",
            new BratNameSampleStreamFactory());
  }

  /**
   * Checks that non of the passed values are {@code null}.
   *
   * @param objects The objects to check for {@code null}.
   * @return {@code true} if at least one object is {@code null}, {@code false} otherwise.
   */
  private boolean notNull(Object... objects) {
    for (Object obj : objects) {
      if (obj == null)
        return false;
    }
    return true;
  }

  @Override
  public ObjectStream<NameSample> create(String[] args) {
    if (args == null) {
      throw new IllegalArgumentException("Passed args must not be null!");
    }
    Parameters params = ArgumentParser.parse(args, Parameters.class);

    if (notNull(params.getRuleBasedTokenizer(), params.getTokenizerModel())) {
      throw new TerminateToolException(-1, "Either use rule based or statistical tokenizer!");
    }

    AnnotationConfiguration annConfig;
    try {
      annConfig = AnnotationConfiguration.parse(params.getAnnotationConfig());
    } catch (IOException e) {
      throw new TerminateToolException(1, "Failed to parse annotation.conf file!");
    }

    // TODO: Add an optional parameter to search recursive
    // TODO: How to handle the error here ? terminate the tool? not nice if used by API!
    ObjectStream<BratDocument> samples;
    try {
      samples = new BratDocumentStream(annConfig,
          params.getBratDataDir(), params.getRecursive(), null);
    } catch (IOException e) {
      throw new TerminateToolException(-1, e.getMessage());
    }

    SentenceDetector sentDetector;
    if (params.getSentenceDetectorModel() != null) {
      try {
        sentDetector = new SentenceDetectorME(new SentenceModel(params.getSentenceDetectorModel()));
      } catch (IOException e) {
        throw new TerminateToolException(-1, "Failed to load sentence detector model!", e);
      }
    } else {
      sentDetector = new NewlineSentenceDetector();
    }

    Tokenizer tokenizer = WhitespaceTokenizer.INSTANCE;
    if (params.getTokenizerModel() != null) {
      try {
        tokenizer = new TokenizerME(new TokenizerModel(params.getTokenizerModel()));
      } catch (IOException e) {
        throw new TerminateToolException(-1, "Failed to load tokenizer model!", e);
      }
    } else if (params.getRuleBasedTokenizer() != null) {
      String tokenizerName = params.getRuleBasedTokenizer();
      if ("simple".equals(tokenizerName)) {
        tokenizer = SimpleTokenizer.INSTANCE;
      } else if ("whitespace".equals(tokenizerName)) {
        tokenizer = WhitespaceTokenizer.INSTANCE;
      } else {
        throw new TerminateToolException(-1, "Unknown tokenizer: " + tokenizerName);
      }
    }

    Set<String> nameTypes = null;
    if (params.getNameTypes() != null) {
      String[] nameTypesArr = params.getNameTypes().split(",");
      if (nameTypesArr.length > 0) {
        nameTypes = Arrays.stream(nameTypesArr).map(String::trim).collect(Collectors.toSet());
      }
    }

    return new BratNameSampleStream(sentDetector, tokenizer, samples, nameTypes);
  }

}
