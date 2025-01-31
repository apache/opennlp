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

package opennlp.tools.parser;

import java.io.IOException;

import opennlp.tools.util.FilterObjectStream;
import opennlp.tools.util.ObjectStream;

/**
 * @see Parse
 * @see FilterObjectStream
 */
public class ParseSampleStream extends FilterObjectStream<String, Parse> {

  /**
   * Initializes a {@link ParseSampleStream instance}.
   *
   * @param in A plain text {@link ObjectStream stream} used as input.
   */
  public ParseSampleStream(ObjectStream<String> in) {
    super(in);
  }

  @Override
  public Parse read() throws IOException {

    String parse = samples.read();

    if (parse != null) {
      if (!parse.trim().isEmpty()) {
        return Parse.parseParse(parse);
      }
      else {
        return read();
      }
    }
    else {
      return null;
    }
  }
}
