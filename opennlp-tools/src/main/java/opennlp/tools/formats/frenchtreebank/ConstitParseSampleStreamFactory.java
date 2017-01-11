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

package opennlp.tools.formats.frenchtreebank;

import opennlp.tools.cmdline.ArgumentParser;
import opennlp.tools.cmdline.StreamFactoryRegistry;
import opennlp.tools.cmdline.params.BasicFormatParams;
import opennlp.tools.formats.AbstractSampleStreamFactory;
import opennlp.tools.formats.DirectorySampleStream;
import opennlp.tools.formats.convert.FileToByteArraySampleStream;
import opennlp.tools.parser.Parse;
import opennlp.tools.util.ObjectStream;

public class ConstitParseSampleStreamFactory extends AbstractSampleStreamFactory<Parse> {

  // TODO: The parameters have an encoding, but the data is in xml
  interface Parameters extends BasicFormatParams {
  }

  private ConstitParseSampleStreamFactory() {
    super(Parameters.class);
  }

  public ObjectStream<Parse> create(String[] args) {

    Parameters params = ArgumentParser.parse(args, Parameters.class);


    return new ConstitParseSampleStream(new FileToByteArraySampleStream(
        new DirectorySampleStream(params.getData(), null, false)));
  }

  public static void registerFactory() {
    StreamFactoryRegistry.registerFactory(Parse.class, "frenchtreebank",
        new ConstitParseSampleStreamFactory());
  }
}
