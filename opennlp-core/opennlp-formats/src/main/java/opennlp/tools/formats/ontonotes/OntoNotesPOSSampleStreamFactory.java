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

package opennlp.tools.formats.ontonotes;

import opennlp.tools.cmdline.StreamFactoryRegistry;
import opennlp.tools.commons.Internal;
import opennlp.tools.formats.AbstractSampleStreamFactory;
import opennlp.tools.formats.convert.ParseToPOSSampleStream;
import opennlp.tools.parser.Parse;
import opennlp.tools.postag.POSSample;
import opennlp.tools.util.ObjectStream;

/**
 * <b>Note:</b> Do not use this class, internal use only!
 *
 * @see ParseToPOSSampleStream
 */
@Internal
public class OntoNotesPOSSampleStreamFactory
        extends AbstractSampleStreamFactory<POSSample, OntoNotesFormatParameters> {

  private final OntoNotesParseSampleStreamFactory parseSampleStreamFactory =
      new OntoNotesParseSampleStreamFactory();

  protected OntoNotesPOSSampleStreamFactory() {
    super(OntoNotesFormatParameters.class);
  }

  public static void registerFactory() {
    StreamFactoryRegistry.registerFactory(POSSample.class, "ontonotes",
            new OntoNotesPOSSampleStreamFactory());
  }

  @Override
  public ObjectStream<POSSample> create(String[] args) {
    if (args == null) {
      throw new IllegalArgumentException("Passed args must not be null!");
    }
    ObjectStream<Parse> parseSampleStream = parseSampleStreamFactory.create(args);
    return new ParseToPOSSampleStream(parseSampleStream);
  }

}
