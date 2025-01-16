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

import opennlp.tools.cmdline.StreamFactoryRegistry;
import opennlp.tools.cmdline.params.BasicFormatParams;
import opennlp.tools.langdetect.LanguageDetectorSampleStream;
import opennlp.tools.langdetect.LanguageSample;
import opennlp.tools.util.ObjectStream;

/**
 * Factory producing OpenNLP {@link LanguageDetectorSampleStream lang detector sample streams}.
 */
public class LanguageDetectorSampleStreamFactory extends
        AbstractSampleStreamFactory<LanguageSample, LanguageDetectorSampleStreamFactory.Parameters> {

  public interface Parameters extends BasicFormatParams {
  }

  public static void registerFactory() {
    StreamFactoryRegistry.registerFactory(LanguageSample.class,
            StreamFactoryRegistry.DEFAULT_FORMAT,
            new LanguageDetectorSampleStreamFactory(Parameters.class));
  }

  protected LanguageDetectorSampleStreamFactory(Class<Parameters> params) {
    super(params);
  }

  @Override
  public ObjectStream<LanguageSample> create(String[] args) {
    ObjectStream<String> lineStream = readData(args, Parameters.class);
    return new LanguageDetectorSampleStream(lineStream);
  }
}
