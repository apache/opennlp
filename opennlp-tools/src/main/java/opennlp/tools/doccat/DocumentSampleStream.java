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

package opennlp.tools.doccat;

import java.io.IOException;

import opennlp.tools.tokenize.WhitespaceTokenizer;
import opennlp.tools.util.FilterObjectStream;
import opennlp.tools.util.ObjectStream;

/**
 * This class reads in string encoded training samples, parses them and
 * outputs {@link DocumentSample} objects.
 * <p>
 * Format:<br>
 * Each line contains one sample document.<br>
 * The category is the first string in the line followed by a tab and whitespace
 * separated document tokens.<br>
 * Sample line: category-string tab-char whitespace-separated-tokens line-break-char(s)<br>
 */
public class DocumentSampleStream extends FilterObjectStream<String, DocumentSample> {

  public DocumentSampleStream(ObjectStream<String> samples) {
    super(samples);
  }

  public DocumentSample read() throws IOException {
    String sampleString = samples.read();

    if (sampleString != null) {

      // Whitespace tokenize entire string
      String tokens[] = WhitespaceTokenizer.INSTANCE.tokenize(sampleString);

      DocumentSample sample;

      if (tokens.length > 1) {
        String category = tokens[0];
        String docTokens[] = new String[tokens.length - 1];
        System.arraycopy(tokens, 1, docTokens, 0, tokens.length - 1);

        sample = new DocumentSample(category, docTokens);
      }
      else {
        throw new IOException("Empty lines, or lines with only a category string are not allowed!");
      }

      return sample;
    }

    return null;
  }
}
