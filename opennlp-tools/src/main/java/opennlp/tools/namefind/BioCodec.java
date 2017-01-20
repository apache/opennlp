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

package opennlp.tools.namefind;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import opennlp.tools.util.SequenceCodec;
import opennlp.tools.util.Span;

public class BioCodec implements SequenceCodec<String> {

  public static final String START = "start";
  public static final String CONTINUE = "cont";
  public static final String OTHER = "other";

  private static final Pattern typedOutcomePattern = Pattern.compile("(.+)-\\w+");

  static String extractNameType(String outcome) {
    Matcher matcher = typedOutcomePattern.matcher(outcome);
    if (matcher.matches()) {
      return matcher.group(1);
    }

    return null;
  }

  public Span[] decode(List<String> c) {
    int start = -1;
    int end = -1;
    List<Span> spans = new ArrayList<>(c.size());
    for (int li = 0; li < c.size(); li++) {
      String chunkTag = c.get(li);
      if (chunkTag.endsWith(BioCodec.START)) {
        if (start != -1) {
          spans.add(new Span(start, end, extractNameType(c.get(li - 1))));
        }

        start = li;
        end = li + 1;

      }
      else if (chunkTag.endsWith(BioCodec.CONTINUE)) {
        end = li + 1;
      }
      else if (chunkTag.endsWith(BioCodec.OTHER)) {
        if (start != -1) {
          spans.add(new Span(start, end, extractNameType(c.get(li - 1))));
          start = -1;
          end = -1;
        }
      }
    }

    if (start != -1) {
      spans.add(new Span(start, end, extractNameType(c.get(c.size() - 1))));
    }

    return spans.toArray(new Span[spans.size()]);
  }

  public String[] encode(Span names[], int length) {
    String[] outcomes = new String[length];
    for (int i = 0; i < outcomes.length; i++) {
      outcomes[i] = BioCodec.OTHER;
    }
    for (Span name : names) {
      if (name.getType() == null) {
        outcomes[name.getStart()] = "default" + "-" + BioCodec.START;
      }
      else {
        outcomes[name.getStart()] = name.getType() + "-" + BioCodec.START;
      }
      // now iterate from begin + 1 till end
      for (int i = name.getStart() + 1; i < name.getEnd(); i++) {
        if (name.getType() == null) {
          outcomes[i] = "default" + "-" + BioCodec.CONTINUE;
        }
        else {
          outcomes[i] = name.getType() + "-" + BioCodec.CONTINUE;
        }
      }
    }

    return outcomes;
  }

  public NameFinderSequenceValidator createSequenceValidator() {
    return new NameFinderSequenceValidator();
  }

  @Override
  public boolean areOutcomesCompatible(String[] outcomes) {
    // We should have *optionally* one outcome named "other", some named xyz-start and sometimes
    // they have a pair xyz-cont. We should not have any other outcome
    // To validate the model we check if we have one outcome named "other", at least
    // one outcome with suffix start. After that we check if all outcomes that ends with
    // "cont" have a pair that ends with "start".
    List<String> start = new ArrayList<>();
    List<String> cont = new ArrayList<>();

    for (int i = 0; i < outcomes.length; i++) {
      String outcome = outcomes[i];
      if (outcome.endsWith(NameFinderME.START)) {
        start.add(outcome.substring(0, outcome.length()
            - NameFinderME.START.length()));
      } else if (outcome.endsWith(NameFinderME.CONTINUE)) {
        cont.add(outcome.substring(0, outcome.length()
            - NameFinderME.CONTINUE.length()));
      } else if (!outcome.equals(NameFinderME.OTHER)) {
        // got unexpected outcome
        return false;
      }
    }

    if (start.size() == 0) {
      return false;
    } else {
      for (String contPreffix : cont) {
        if (!start.contains(contPreffix)) {
          return false;
        }
      }
    }

    return true;
  }
}
