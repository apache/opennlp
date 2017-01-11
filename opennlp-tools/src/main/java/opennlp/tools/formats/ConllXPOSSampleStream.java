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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;

import opennlp.tools.postag.POSSample;
import opennlp.tools.util.FilterObjectStream;
import opennlp.tools.util.InputStreamFactory;
import opennlp.tools.util.InvalidFormatException;
import opennlp.tools.util.ObjectStream;
import opennlp.tools.util.ParagraphStream;
import opennlp.tools.util.PlainTextByLineStream;

/**
 * Parses the data from the CONLL 06 shared task into POS Samples.
 * <p>
 * More information about the data format can be found here:<br>
 * http://www.cnts.ua.ac.be/conll2006/
 * <p>
 * <b>Note:</b> Do not use this class, internal use only!
 */
public class ConllXPOSSampleStream extends FilterObjectStream<String, POSSample> {

  public ConllXPOSSampleStream(ObjectStream<String> lineStream) {
    super(new ParagraphStream(lineStream));
  }

  public ConllXPOSSampleStream(InputStreamFactory in, Charset charset) throws IOException {
    super(new ParagraphStream(new PlainTextByLineStream(in, charset)));
  }

  public POSSample read() throws IOException {

    // The CONLL-X data has a word per line and each line is tab separated
    // in the following format:
    // ID, FORM, LEMMA, CPOSTAG, POSTAG, ... (max 10 fields)

    // One paragraph contains a whole sentence and, the token
    // and tag will be read from the FORM and POSTAG field.

    String paragraph = samples.read();

    POSSample sample = null;

    if (paragraph != null) {

      // paragraph get lines
      BufferedReader reader = new BufferedReader(new StringReader(paragraph));

      List<String> tokens = new ArrayList<>(100);
      List<String> tags = new ArrayList<>(100);

      String line;
      while ((line = reader.readLine())  != null) {

        final int minNumberOfFields = 5;

        String parts[] = line.split("\t");

        if (parts.length >= minNumberOfFields) {
          tokens.add(parts[1]);
          tags.add(parts[4]);
        }
        else {
          throw new InvalidFormatException("Every non-empty line must have at least " +
              minNumberOfFields + " fields: '" + line + "'!");
        }
      }

      // just skip empty samples and read next sample
      if (tokens.size() == 0)
        sample = read();

      sample = new POSSample(tokens.toArray(new String[tokens.size()]),
          tags.toArray(new String[tags.size()]));
    }

    return sample;
  }
}
