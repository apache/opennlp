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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import opennlp.tools.formats.brat.SegmenterObjectStream;
import opennlp.tools.util.ObjectStream;

/**
 * Reads a plain text file and return each line as a <code>String</code> object.
 */
public class DocumentToLineStream extends SegmenterObjectStream<String, String> {

  public DocumentToLineStream(ObjectStream<String> samples) {
    super(samples);
  }

  @Override
  protected List<String> read(String sample) throws IOException {
    List<String> lines = Arrays.asList(sample.split("\n"));

    // documents must be empty line terminated
    if (!lines.get(lines.size() - 1).trim().isEmpty()) {
      lines = new ArrayList<>(lines);
      lines.add("");
    }

    return lines;
  }
}

