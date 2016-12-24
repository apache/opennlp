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
import java.util.ArrayList;
import java.util.List;

import opennlp.tools.util.FilterObjectStream;
import opennlp.tools.util.InvalidFormatException;
import opennlp.tools.util.ObjectStream;

class DocumentSplitterStream extends FilterObjectStream<String, String> {

  private static final String DOC_START_ELEMENT = "<DOC>";
  private static final String DOC_END_ELEMENT = "</DOC>";

  private List<String> docs = new ArrayList<>();

  DocumentSplitterStream(ObjectStream<String> samples) {
    super(samples);
  }

  public String read() throws IOException {

    if (docs.isEmpty()) {
      String newDocs = samples.read();

      if (newDocs != null) {
        int docStartOffset = 0;

        while (true) {
          int startDocElement = newDocs.indexOf(DOC_START_ELEMENT, docStartOffset);
          int endDocElement = newDocs.indexOf(DOC_END_ELEMENT, docStartOffset);

          if (startDocElement != -1 && endDocElement != -1) {

            if (startDocElement < endDocElement) {
              docs.add(newDocs.substring(startDocElement, endDocElement + DOC_END_ELEMENT.length()));
              docStartOffset = endDocElement + DOC_END_ELEMENT.length();
            }
            else {
              throw new InvalidFormatException("<DOC> element is not closed!");
            }
          }
          else if (startDocElement != endDocElement) {
            throw new InvalidFormatException("Missing <DOC> or </DOC> element!");
          }
          else {
            break;
          }
        }
      }
    }

    if (docs.size() > 0) {
      return docs.remove(0);
    }
    else {
      return null;
    }
  }
}
