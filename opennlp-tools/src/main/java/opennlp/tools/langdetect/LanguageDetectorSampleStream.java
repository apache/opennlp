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

package opennlp.tools.langdetect;

import java.io.IOException;

import opennlp.tools.util.FilterObjectStream;
import opennlp.tools.util.ObjectStream;

/**
 * This class reads in string encoded training samples, parses them and
 * outputs {@link LanguageSample} objects.
 * <p>
 * Format:<br>
 * Each line contains one sample document.<br>
 * The language is the first string in the line followed by a tab and the document content.<br>
 * Sample line: category-string tab-char document line-break-char(s)<br>
 */
public class LanguageDetectorSampleStream
    extends FilterObjectStream<String, LanguageSample> {

  public LanguageDetectorSampleStream(ObjectStream<String> samples) {
    super(samples);
  }

  public LanguageSample read() throws IOException {
    String sampleString;
    while ((sampleString = samples.read()) != null) {
      int tabIndex = sampleString.indexOf("\t");
      if (tabIndex > 0) {
        String lang = sampleString.substring(0, tabIndex);
        String context = sampleString.substring(tabIndex + 1);

        return new LanguageSample(new Language(lang), context);
      }
    }

    return null;
  }
}
