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

package opennlp.bratann;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;

import opennlp.tools.namefind.TokenNameFinder;
import opennlp.tools.sentdetect.SentenceDetector;
import opennlp.tools.tokenize.Tokenizer;
import opennlp.tools.util.Span;

@Path("/ner")
public class NameFinderResource {

  private SentenceDetector sentDetect = NameFinderAnnService.sentenceDetector;
  private Tokenizer tokenizer = NameFinderAnnService.tokenizer;
  private TokenNameFinder nameFinders[] = NameFinderAnnService.nameFinders;

  private static int findNextNonWhitespaceChar(CharSequence s, int beginOffset, int endOffset) {
    for (int i = beginOffset; i < endOffset; i++) {
      if (!Character.isSpaceChar(s.charAt(i))) {
        return i;
      }
    }
    return -1;
  }

  @POST
  @Consumes(MediaType.TEXT_PLAIN)
  @Produces(MediaType.APPLICATION_JSON)
  public Map<String, NameAnn> findNames(@QueryParam("model") String modelName, String text) {
    Span[] sentenceSpans = sentDetect.sentPosDetect(text);
    Map<String, NameAnn> map = new HashMap<>();

    int indexCounter = 0;

    for (Span sentenceSpan : sentenceSpans) {

      String sentenceText = sentenceSpan.getCoveredText(text).toString();

      // offset of sentence gets lost here!
      Span[] tokenSpans = tokenizer.tokenizePos(sentenceText);

      String tokens[] = Span.spansToStrings(tokenSpans, sentenceText);

      for (TokenNameFinder nameFinder : nameFinders) {
        Span names[] = nameFinder.find(tokens);

        for (Span name : names) {

          int beginOffset = tokenSpans[name.getStart()].getStart() + sentenceSpan.getStart();
          int endOffset = tokenSpans[name.getEnd() - 1].getEnd() + sentenceSpan.getStart();

          // create a list of new line indexes
          List<Integer> newLineIndexes = new ArrayList<>();

          // TODO: Code needs to handle case that there are multiple new lines
          // in a row

          boolean inNewLineSequence = false;
          for (int ci = beginOffset; ci < endOffset; ci++) {
            if (text.charAt(ci) == '\n' || text.charAt(ci) == '\r') {
              if (!inNewLineSequence) {
                newLineIndexes.add(ci);
              }
              inNewLineSequence = true;
            } else {
              inNewLineSequence = false;
            }
          }

          List<String> textSegments = new ArrayList<>();
          List<int[]> spanSegments = new ArrayList<>();

          int segmentBegin = beginOffset;

          for (int newLineOffset : newLineIndexes) {
            // create segment from begin to offset
            textSegments.add(text.substring(segmentBegin, newLineOffset));
            spanSegments.add(new int[] {segmentBegin, newLineOffset});

            segmentBegin = findNextNonWhitespaceChar(text, newLineOffset + 1,
                endOffset);

            if (segmentBegin == -1) {
              break;
            }
          }

          // create left over segment
          if (segmentBegin != -1) {
            textSegments.add(text.substring(segmentBegin, endOffset));
            spanSegments.add(new int[] {segmentBegin, endOffset});
          }

          NameAnn ann = new NameAnn();
          ann.texts = textSegments.toArray(new String[textSegments.size()]);
          ann.offsets = spanSegments.toArray(new int[spanSegments.size()][]);
          ann.type = name.getType();

          map.put(Integer.toString(indexCounter++), ann);
        }
      }
    }
    return map;
  }

  public static class NameAnn {
    int[][] offsets;
    String[] texts;
    String type;
  }
}
