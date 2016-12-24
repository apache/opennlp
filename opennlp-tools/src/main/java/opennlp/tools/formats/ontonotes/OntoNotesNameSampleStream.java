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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import opennlp.tools.namefind.NameSample;
import opennlp.tools.tokenize.WhitespaceTokenizer;
import opennlp.tools.util.FilterObjectStream;
import opennlp.tools.util.ObjectStream;
import opennlp.tools.util.Span;
import opennlp.tools.util.StringUtil;

/**
 * Name Sample Stream parser for the OntoNotes 4.0 corpus.
 */
public class OntoNotesNameSampleStream extends
    FilterObjectStream<String, NameSample> {

  private final Map<String, String> tokenConversionMap;

  private List<NameSample> nameSamples = new LinkedList<>();

  public OntoNotesNameSampleStream(ObjectStream<String> samples) {
    super(samples);

    Map<String, String> tokenConversionMap = new HashMap<>();
    tokenConversionMap.put("-LRB-", "(");
    tokenConversionMap.put("-RRB-", ")");
    tokenConversionMap.put("-LSB-", "[");
    tokenConversionMap.put("-RSB-", "]");
    tokenConversionMap.put("-LCB-", "{");
    tokenConversionMap.put("-RCB-", "}");
    tokenConversionMap.put("-AMP-", "&");
    this.tokenConversionMap = Collections.unmodifiableMap(tokenConversionMap);
  }

  private String convertToken(String token) {

    StringBuilder convertedToken = new StringBuilder(token);

    int startTagEndIndex = convertedToken.indexOf(">");

    if (token.contains("=\"") && startTagEndIndex != -1) {
      convertedToken.delete(0, startTagEndIndex + 1);
    }

    int endTagBeginIndex = convertedToken.indexOf("<");
    int endTagEndIndex = convertedToken.indexOf(">");

    if (endTagBeginIndex != -1 && endTagEndIndex != -1) {
      convertedToken.delete(endTagBeginIndex, endTagEndIndex + 1);
    }

    String cleanedToken = convertedToken.toString();

    if (tokenConversionMap.get(cleanedToken) != null) {
      cleanedToken = tokenConversionMap.get(cleanedToken);
    }

    return cleanedToken;
  }

  public NameSample read() throws IOException {

    if (nameSamples.isEmpty()) {
      String doc = samples.read();

      if (doc != null) {
        BufferedReader docIn = new BufferedReader(new StringReader(doc));

        boolean clearAdaptiveData = true;

        String line;
        while ((line = docIn.readLine()) != null) {

          if (line.startsWith("<DOC")) {
            continue;
          }

          if (line.equals("</DOC>")) {
            break;
          }

          String tokens[] = WhitespaceTokenizer.INSTANCE.tokenize(line);

          List<Span> entities = new LinkedList<>();
          List<String> cleanedTokens = new ArrayList<>(tokens.length);

          int tokenIndex = 0;
          int entityBeginIndex = -1;
          String entityType = null;
          boolean insideStartEnmaxTag = false;
          for (String token : tokens) {

            // Split here, next part of tag is in new token
            if (token.startsWith("<ENAMEX")) {
              insideStartEnmaxTag = true;
              continue;
            }

            if (insideStartEnmaxTag) {

              String typeBegin = "TYPE=\"";

              if (token.startsWith(typeBegin)) {

                int typeEnd = token.indexOf("\"", typeBegin.length());

                entityType = StringUtil.toLowerCase(token.substring(typeBegin.length(), typeEnd));
              }

              if (token.contains(">")) {
                entityBeginIndex = tokenIndex;
                insideStartEnmaxTag = false;
              } else {
                continue;
              }
            }

            if (token.endsWith("</ENAMEX>")) {
              entities.add(new Span(entityBeginIndex, tokenIndex + 1,
                  entityType));
              entityBeginIndex = -1;
            }

            cleanedTokens.add(convertToken(token));
            tokenIndex++;
          }

          nameSamples.add(new NameSample(cleanedTokens
              .toArray(new String[cleanedTokens.size()]), entities
              .toArray(new Span[entities.size()]), clearAdaptiveData));

          clearAdaptiveData = false;
        }
      }
    }

    if (!nameSamples.isEmpty()) {
      return nameSamples.remove(0);
    } else {
      return null;
    }
  }
}