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

package opennlp.tools.formats.convert;

import opennlp.tools.cmdline.ArgumentParser;
import opennlp.tools.cmdline.StreamFactoryRegistry;
import opennlp.tools.formats.LanguageSampleStreamFactory;
import opennlp.tools.formats.ParseSampleStreamFactory;
import opennlp.tools.parser.Parse;
import opennlp.tools.postag.POSSample;
import opennlp.tools.util.ObjectStream;

/**
 * <b>Note:</b> Do not use this class, internal use only!
 */
public class ParseToPOSSampleStreamFactory extends LanguageSampleStreamFactory<POSSample> {

  private ParseToPOSSampleStreamFactory() {
    super(ParseSampleStreamFactory.Parameters.class);
  }

  public ObjectStream<POSSample> create(String[] args) {

    ParseSampleStreamFactory.Parameters params =
        ArgumentParser.parse(args, ParseSampleStreamFactory.Parameters.class);

    ObjectStream<Parse> parseSampleStream = StreamFactoryRegistry.getFactory(Parse.class,
        StreamFactoryRegistry.DEFAULT_FORMAT).create(
        ArgumentParser.filter(args, ParseSampleStreamFactory.Parameters.class));

    return new ParseToPOSSampleStream(parseSampleStream);
  }

  public static void registerFactory() {
    StreamFactoryRegistry.registerFactory(POSSample.class,
        "parse", new ParseToPOSSampleStreamFactory());
  }
}
