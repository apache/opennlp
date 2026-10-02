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

package opennlp.tools.formats.conllu;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

import opennlp.tools.cmdline.ArgumentParser;
import opennlp.tools.cmdline.StreamFactoryRegistry;
import opennlp.tools.cmdline.TerminateToolException;
import opennlp.tools.cmdline.params.BasicFormatParams;
import opennlp.tools.commons.Internal;
import opennlp.tools.depparse.DependencySample;
import opennlp.tools.formats.AbstractSampleStreamFactory;
import opennlp.tools.formats.FormatUtil;
import opennlp.tools.util.ObjectStream;

/**
 * <b>Note:</b> Do not use this class, internal use only!
 *
 * @see DependencySample
 * @see ConlluDependencySampleStream
 */
@Internal
public class ConlluDependencySampleStreamFactory extends
        AbstractSampleStreamFactory<DependencySample, ConlluDependencySampleStreamFactory.Parameters> {

  public static final String CONLLU_FORMAT = "conllu";

  public interface Parameters extends BasicFormatParams {
    /** {@inheritDoc} */
    @Override
    @ArgumentParser.ParameterDescription(valueName = "charsetName",
        description = "CoNLL-U data must use UTF-8")
    @ArgumentParser.OptionalParameter(defaultValue = "UTF-8")
    Charset getEncoding();

    @ArgumentParser.ParameterDescription(valueName = "tagset",
        description = "u|x u for unified tags and x for language-specific part-of-speech tags")
    @ArgumentParser.OptionalParameter(defaultValue = "u")
    String getTagset();
  }

  public static void registerFactory() {
    StreamFactoryRegistry.registerFactory(DependencySample.class,
        StreamFactoryRegistry.DEFAULT_FORMAT, new ConlluDependencySampleStreamFactory(Parameters.class));
    StreamFactoryRegistry.registerFactory(DependencySample.class,
        CONLLU_FORMAT, new ConlluDependencySampleStreamFactory(Parameters.class));
  }

  protected ConlluDependencySampleStreamFactory(Class<Parameters> params) {
    super(params);
  }

  @Override
  public ObjectStream<DependencySample> create(String[] args) {
    Parameters params = validateBasicFormatParameters(args, Parameters.class);

    if (!StandardCharsets.UTF_8.equals(params.getEncoding())) {
      throw new TerminateToolException(-1, "CoNLL-U data must use UTF-8");
    }

    ConlluTagset tagset = switch (params.getTagset()) {
      case "u" -> ConlluTagset.U;
      case "x" -> ConlluTagset.X;
      default -> throw new TerminateToolException(-1, "Unknown tagset parameter: " + params.getTagset());
    };

    try {
      return new ConlluDependencySampleStream(FormatUtil.createInputStreamFactory(params.getData()), tagset);
    } catch (IOException e) {
      throw new TerminateToolException(-1,
              "IO Error while creating an Input Stream: " + e.getMessage(), e);
    }
  }
}
