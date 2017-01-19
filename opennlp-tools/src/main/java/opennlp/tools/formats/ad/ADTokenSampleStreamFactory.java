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

package opennlp.tools.formats.ad;

import opennlp.tools.cmdline.ArgumentParser;
import opennlp.tools.cmdline.StreamFactoryRegistry;
import opennlp.tools.cmdline.params.DetokenizerParameter;
import opennlp.tools.formats.DetokenizerSampleStreamFactory;
import opennlp.tools.formats.convert.NameToTokenSampleStream;
import opennlp.tools.namefind.NameSample;
import opennlp.tools.tokenize.TokenSample;
import opennlp.tools.util.ObjectStream;

/**
 * <b>Note:</b> Do not use this class, internal use only!
 */
public class ADTokenSampleStreamFactory extends
    DetokenizerSampleStreamFactory<TokenSample> {

  interface Parameters extends ADNameSampleStreamFactory.Parameters, DetokenizerParameter {
  }

  public static void registerFactory() {
    StreamFactoryRegistry.registerFactory(TokenSample.class, "ad",
        new ADTokenSampleStreamFactory(Parameters.class));
  }

  protected <P> ADTokenSampleStreamFactory(Class<P> params) {
    super(params);
  }

  public ObjectStream<TokenSample> create(String[] args) {
    Parameters params = ArgumentParser.parse(args, Parameters.class);

    ObjectStream<NameSample> samples = StreamFactoryRegistry.getFactory(
        NameSample.class, "ad").create(
            ArgumentParser.filter(args, ADNameSampleStreamFactory.Parameters.class));
    return new NameToTokenSampleStream(createDetokenizer(params), samples);
  }
}
