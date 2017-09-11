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

package opennlp.tools.formats.nkjp;

import java.io.IOException;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import opennlp.tools.sentdetect.SentenceSample;
import opennlp.tools.util.ObjectStream;
import opennlp.tools.util.Span;

public class NKJPSentenceSampleStream implements ObjectStream<SentenceSample> {
  private final NKJPSegmentationDocument segments;

  private final NKJPTextDocument text;

  private Iterator<Map.Entry<String, Map<String, NKJPSegmentationDocument.Pointer>>> segmentIt;

  NKJPSentenceSampleStream(NKJPSegmentationDocument segments, NKJPTextDocument text) {
    this.segments = segments;
    this.text = text;
    reset();
  }

  @Override
  public SentenceSample read() throws IOException {
    StringBuilder sentencesString = new StringBuilder();
    List<Span> sentenceSpans = new LinkedList<>();
    Map<String, String> paragraphs = text.getParagraphs();

    while (segmentIt.hasNext()) {
      Map.Entry<String, Map<String, NKJPSegmentationDocument.Pointer>> segment = segmentIt.next();
      int start = 0;
      int end = 0;
      boolean started = false;
      String lastParagraphId = "";
      String currentParagraph = "";

      for (String s : segment.getValue().keySet()) {
        NKJPSegmentationDocument.Pointer currentPointer = segment.getValue().get(s);
        Span currentSpan = currentPointer.toSpan();

        if (!started) {
          start = currentSpan.getStart();
          started = true;
          lastParagraphId = currentPointer.id;
          currentParagraph = paragraphs.get(currentPointer.id);
        }

        if (!lastParagraphId.equals(currentPointer.id)) {
          int new_start = sentencesString.length();
          sentencesString.append(currentParagraph.substring(start, end));
          int new_end = sentencesString.length();
          sentenceSpans.add(new Span(new_start, new_end));
          sentencesString.append(' ');

          start = currentSpan.getStart();
          end = currentSpan.getEnd();
          lastParagraphId = currentPointer.id;
          currentParagraph = paragraphs.get(currentPointer.id);
        } else {
          end = currentSpan.getEnd();
        }
      }

      int new_start = sentencesString.length();
      sentencesString.append(currentParagraph.substring(start, end));
      int new_end = sentencesString.length();
      sentenceSpans.add(new Span(new_start, new_end));
      sentencesString.append(' ');
    }

    // end of stream is reached, indicate that with null return value
    if (sentenceSpans.size() == 0) {
      return null;
    }

    return new SentenceSample(sentencesString.toString(),
      sentenceSpans.toArray(new Span[sentenceSpans.size()]));
  }

  @Override
  public void reset() {
    segmentIt = segments.getSegments().entrySet().iterator();
  }

}
