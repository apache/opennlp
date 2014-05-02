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

import opennlp.tools.cmdline.ArgumentParser;
import opennlp.tools.cmdline.StreamFactoryRegistry;
import opennlp.tools.cmdline.params.DetokenizerParameter;
import opennlp.tools.formats.convert.POSToSentenceSampleStream;
import opennlp.tools.postag.POSSample;
import opennlp.tools.sentdetect.SentenceSample;
import opennlp.tools.util.ObjectStream;

/**
 * <b>Note:</b> Do not use this class, internal use only!
 */
public class ConllXSentenceSampleStreamFactory extends
    DetokenizerSampleStreamFactory<SentenceSample> {

  interface Parameters extends ConllXPOSSampleStreamFactory.Parameters, DetokenizerParameter {
    // TODO: make chunk size configurable
  }

  public static void registerFactory() {
    StreamFactoryRegistry.registerFactory(SentenceSample.class,
        ConllXPOSSampleStreamFactory.CONLLX_FORMAT, new ConllXSentenceSampleStreamFactory(Parameters.class));
  }

  protected <P> ConllXSentenceSampleStreamFactory(Class<P> params) {
    super(params);
  }

  public ObjectStream<SentenceSample> create(String[] args) {
    Parameters params = ArgumentParser.parse(args, Parameters.class);

    ObjectStream<POSSample> posSampleStream = StreamFactoryRegistry.getFactory(POSSample.class,
        ConllXPOSSampleStreamFactory.CONLLX_FORMAT).create(
        ArgumentParser.filter(args, ConllXPOSSampleStreamFactory.Parameters.class));
    return new POSToSentenceSampleStream(createDetokenizer(params), posSampleStream, 30);
  }
}
