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

package opennlp.tools.formats.muc;

import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;

import opennlp.tools.namefind.NameSample;
import opennlp.tools.tokenize.Tokenizer;
import opennlp.tools.util.FilterObjectStream;
import opennlp.tools.util.ObjectStream;

public class MucNameSampleStream extends FilterObjectStream<String, NameSample> {

  private final Tokenizer tokenizer;

  private List<NameSample> storedSamples = new ArrayList<>();

  protected MucNameSampleStream(Tokenizer tokenizer, ObjectStream<String> samples) {
    super(samples);
    this.tokenizer = tokenizer;
  }

  public NameSample read() throws IOException {
    if (storedSamples.isEmpty()) {

      String document = samples.read();

      if (document != null) {

        // Note: This is a hack to fix invalid formating in
        // some MUC files ...
        document = document.replace(">>", ">");

        new SgmlParser().parse(new StringReader(document),
            new MucNameContentHandler(tokenizer, storedSamples));
      }
    }

    if (storedSamples.size() > 0) {
      return storedSamples.remove(0);
    }
    else {
      return null;
    }
  }
}
