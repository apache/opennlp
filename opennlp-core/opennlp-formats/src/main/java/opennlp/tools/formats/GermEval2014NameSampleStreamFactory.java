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

import java.io.IOException;

import opennlp.tools.cmdline.ArgumentParser.ParameterDescription;
import opennlp.tools.cmdline.StreamFactoryRegistry;
import opennlp.tools.cmdline.TerminateToolException;
import opennlp.tools.cmdline.params.BasicFormatParams;
import opennlp.tools.commons.Internal;
import opennlp.tools.formats.GermEval2014NameSampleStream.NerLayer;
import opennlp.tools.namefind.NameSample;
import opennlp.tools.util.ObjectStream;

/**
 * <b>Note:</b>
 * Do not use this class, internal use only!
 *
 * @see GermEval2014NameSampleStream
 */
@Internal
public class GermEval2014NameSampleStreamFactory extends
    LanguageSampleStreamFactory<NameSample, GermEval2014NameSampleStreamFactory.Parameters> {

  public interface Parameters extends BasicFormatParams {
    @ParameterDescription(valueName = "per,loc,org,misc")
    String getTypes();

    @ParameterDescription(valueName = "outer|inner", description = "NER annotation layer to use. " +
        "Use 'outer' for top-level entities or 'inner' for nested/embedded entities.")
    String getLayer();
  }

  public static void registerFactory() {
    StreamFactoryRegistry.registerFactory(NameSample.class,
        "germeval2014", new GermEval2014NameSampleStreamFactory(Parameters.class));
  }

  protected GermEval2014NameSampleStreamFactory(final Class<Parameters> params) {
    super(params);
  }

  @Override
  public ObjectStream<NameSample> create(final String[] args) {

    final Parameters params = validateBasicFormatParameters(args, Parameters.class);

    language = "deu";

    int typesToGenerate = 0;

    if (params.getTypes().contains("per")) {
      typesToGenerate = typesToGenerate |
          GermEval2014NameSampleStream.GENERATE_PERSON_ENTITIES;
    }
    if (params.getTypes().contains("org")) {
      typesToGenerate = typesToGenerate |
          GermEval2014NameSampleStream.GENERATE_ORGANIZATION_ENTITIES;
    }
    if (params.getTypes().contains("loc")) {
      typesToGenerate = typesToGenerate |
          GermEval2014NameSampleStream.GENERATE_LOCATION_ENTITIES;
    }
    if (params.getTypes().contains("misc")) {
      typesToGenerate = typesToGenerate |
          GermEval2014NameSampleStream.GENERATE_MISC_ENTITIES;
    }

    final NerLayer layer;
    final String layerParam = params.getLayer();
    if (layerParam == null || "outer".equals(layerParam)) {
      layer = NerLayer.OUTER;
    } else if ("inner".equals(layerParam)) {
      layer = NerLayer.INNER;
    } else {
      throw new TerminateToolException(1, "Unsupported layer: " + layerParam
          + ". Use 'outer' or 'inner'.");
    }

    try {
      return new GermEval2014NameSampleStream(
          FormatUtil.createInputStreamFactory(params.getData()), typesToGenerate, layer);
    } catch (final IOException e) {
      throw new TerminateToolException(-1,
          "IO Error while creating an Input Stream: " + e.getMessage(), e);
    }
  }
}
