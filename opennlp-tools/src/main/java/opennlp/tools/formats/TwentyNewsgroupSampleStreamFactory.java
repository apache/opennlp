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

package opennlp.tools.formats;

import java.io.File;
import java.io.IOException;

import opennlp.tools.cmdline.ArgumentParser;
import opennlp.tools.cmdline.StreamFactoryRegistry;
import opennlp.tools.cmdline.TerminateToolException;
import opennlp.tools.cmdline.params.EncodingParameter;
import opennlp.tools.doccat.DocumentSample;
import opennlp.tools.tokenize.SimpleTokenizer;
import opennlp.tools.tokenize.Tokenizer;
import opennlp.tools.tokenize.TokenizerME;
import opennlp.tools.tokenize.TokenizerModel;
import opennlp.tools.tokenize.WhitespaceTokenizer;
import opennlp.tools.util.ObjectStream;

public class TwentyNewsgroupSampleStreamFactory extends AbstractSampleStreamFactory<DocumentSample> {

  public static void registerFactory() {
    StreamFactoryRegistry.registerFactory(DocumentSample.class,
        "20newsgroup",
        new TwentyNewsgroupSampleStreamFactory(TwentyNewsgroupSampleStreamFactory.Parameters.class));
  }

  protected <P> TwentyNewsgroupSampleStreamFactory(Class<P> params) {
    super(params);
  }

  @Override
  public ObjectStream<DocumentSample> create(String[] args) {

    TwentyNewsgroupSampleStreamFactory.Parameters params =
        ArgumentParser.parse(args, TwentyNewsgroupSampleStreamFactory.Parameters.class);

    Tokenizer tokenizer = WhitespaceTokenizer.INSTANCE;

    if (params.getTokenizerModel() != null) {
      try {
        tokenizer = new TokenizerME(new TokenizerModel(params.getTokenizerModel()));
      } catch (IOException e) {
        throw new TerminateToolException(-1, "Failed to load tokenizer model!", e);
      }
    }
    else if (params.getRuleBasedTokenizer() != null) {
      String tokenizerName = params.getRuleBasedTokenizer();

      if ("simple".equals(tokenizerName)) {
        tokenizer = SimpleTokenizer.INSTANCE;
      }
      else if ("whitespace".equals(tokenizerName)) {
        tokenizer = WhitespaceTokenizer.INSTANCE;
      }
      else {
        throw new TerminateToolException(-1, "Unkown tokenizer: " + tokenizerName);
      }
    }

    try {
      return new TwentyNewsgroupSampleStream(
          tokenizer, params.getDataDir().toPath());
    } catch (IOException e) {
      throw new TerminateToolException(-1, "IO error while opening sample data: " + e.getMessage(), e);
    }
  }

  interface Parameters extends EncodingParameter {
    @ArgumentParser.ParameterDescription(valueName = "dataDir",
        description = "dir containing the 20newsgroup folders")
    File getDataDir();

    @ArgumentParser.ParameterDescription(valueName = "modelFile")
    @ArgumentParser.OptionalParameter
    File getTokenizerModel();

    @ArgumentParser.ParameterDescription(valueName = "name")
    @ArgumentParser.OptionalParameter
    String getRuleBasedTokenizer();
  }
}
