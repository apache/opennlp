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

import java.io.IOException;

import opennlp.tools.parser.Parse;
import opennlp.tools.util.FilterObjectStream;
import opennlp.tools.util.ObjectStream;

// Should be possible with this one, to train the parser and pos tagger!
public class OntoNotesParseSampleStream extends FilterObjectStream<String, Parse> {

  public OntoNotesParseSampleStream(ObjectStream<String> samples) {
    super(samples);
  }

  public Parse read() throws IOException {

    StringBuilder parseString = new StringBuilder();

    while (true) {
      String parse = samples.read();

      if (parse != null) {
        parse = parse.trim();
      }

      if (parse == null || parse.isEmpty()) {
        if (parseString.length() > 0) {
          return Parse.parseParse(parseString.toString());
        }
        else {
          return null;
        }
      }

      parseString.append(parse).append(" ");
    }
  }
}
