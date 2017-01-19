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


package opennlp.tools.sentdetect;

import java.io.IOException;
import java.util.LinkedList;
import java.util.List;

import opennlp.tools.util.FilterObjectStream;
import opennlp.tools.util.ObjectStream;
import opennlp.tools.util.Span;

/**
 * This class is a stream filter which reads a sentence by line samples from
 * a <code>Reader</code> and converts them into {@link SentenceSample} objects.
 * An empty line indicates the begin of a new document.
 */
public class SentenceSampleStream extends FilterObjectStream<String, SentenceSample> {

  public SentenceSampleStream(ObjectStream<String> sentences) {
    super(new EmptyLinePreprocessorStream(sentences));
  }

  public static String replaceNewLineEscapeTags(String s) {
    return s.replace("<LF>", "\n").replace("<CR>", "\r");
  }

  public SentenceSample read() throws IOException {

    StringBuilder sentencesString = new StringBuilder();
    List<Span> sentenceSpans = new LinkedList<>();

    String sentence;
    while ((sentence = samples.read()) != null && !sentence.equals("")) {
      int begin = sentencesString.length();
      sentence = sentence.trim();
      sentence = replaceNewLineEscapeTags(sentence);
      sentencesString.append(sentence);
      int end = sentencesString.length();
      sentenceSpans.add(new Span(begin, end));
      sentencesString.append(' ');
    }

    if (sentenceSpans.size() > 0) {
      return new SentenceSample(sentencesString.toString(),
          sentenceSpans.toArray(new Span[sentenceSpans.size()]));
    }
    return null;
  }
}
