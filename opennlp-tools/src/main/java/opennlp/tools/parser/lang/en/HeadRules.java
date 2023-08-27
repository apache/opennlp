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

package opennlp.tools.parser.lang.en;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;
import java.util.Stack;
import java.util.StringTokenizer;

import opennlp.tools.parser.Constituent;
import opennlp.tools.parser.GapLabeler;
import opennlp.tools.parser.Parse;
import opennlp.tools.parser.chunking.Parser;
import opennlp.tools.util.model.ArtifactSerializer;
import opennlp.tools.util.model.SerializableArtifact;

/**
 * Class for storing the English {@link opennlp.tools.parser.HeadRules} associated with parsing.
 */
public class HeadRules implements opennlp.tools.parser.HeadRules, GapLabeler, SerializableArtifact {

  public static class HeadRulesSerializer implements ArtifactSerializer<HeadRules> {

    public HeadRules create(InputStream in) throws IOException {
      return new HeadRules(new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8)));
    }

    public void serialize(opennlp.tools.parser.lang.en.HeadRules artifact, OutputStream out)
        throws IOException {
      artifact.serialize(new OutputStreamWriter(out, StandardCharsets.UTF_8));
    }
  }

  private static class HeadRule {
    public boolean leftToRight;
    public String[] tags;

    public HeadRule(boolean l2r, String[] tags) {
      leftToRight = l2r;

      for (String tag : tags) {
        Objects.requireNonNull(tag, "tags must not contain null values");
      }

      this.tags = tags;
    }

    @Override
    public int hashCode() {
      return Objects.hash(leftToRight, Arrays.hashCode(tags));
    }

    @Override
    public boolean equals(Object obj) {
      if (obj == this) {
        return true;
      }

      if (obj instanceof HeadRule rule) {

        return rule.leftToRight == leftToRight &&
            Arrays.equals(rule.tags, tags);
      }

      return false;
    }
  }

  private Map<String, HeadRule> headRules;
  private final Set<String> punctSet;

  /**
   * Creates a new set of head rules based on the specified reader.
   *
   * @param rulesReader A {@link Reader} for a head rules file.
   *
   * @throws IOException Thrown  f the head rules reader can not be read.
   */
  public HeadRules(Reader rulesReader) throws IOException {
    BufferedReader in = new BufferedReader(rulesReader);
    readHeadRules(in);

    punctSet = new HashSet<>();
    punctSet.add(".");
    punctSet.add(",");
    punctSet.add("``");
    punctSet.add("''");
    //punctSet.add(":");
  }

  @Override
  public Set<String> getPunctuationTags() {
    return punctSet;
  }

  @Override
  public Parse getHead(Parse[] constituents, String type) {
    if (Parser.TOK_NODE.equals(constituents[0].getType())) {
      return null;
    }
    HeadRule hr;
    if (type.equals("NP") || type.equals("NX")) {
      String[] tags1 = { "NN", "NNP", "NNPS", "NNS", "NX", "JJR", "POS" };
      for (int ci = constituents.length - 1; ci >= 0; ci--) {
        for (int ti = tags1.length - 1; ti >= 0; ti--) {
          if (constituents[ci].getType().equals(tags1[ti])) {
            return constituents[ci].getHead();
          }
        }
      }
      for (Parse constituent : constituents) {
        if (constituent.getType().equals("NP")) {
          return constituent.getHead();
        }
      }
      String[] tags2 = { "$", "ADJP", "PRN" };
      for (int ci = constituents.length - 1; ci >= 0; ci--) {
        for (int ti = tags2.length - 1; ti >= 0; ti--) {
          if (constituents[ci].getType().equals(tags2[ti])) {
            return constituents[ci].getHead();
          }
        }
      }
      String[] tags3 = { "JJ", "JJS", "RB", "QP" };
      for (int ci = constituents.length - 1; ci >= 0; ci--) {
        for (int ti = tags3.length - 1; ti >= 0; ti--) {
          if (constituents[ci].getType().equals(tags3[ti])) {
            return constituents[ci].getHead();
          }
        }
      }
      return constituents[constituents.length - 1].getHead();
    }
    else if ((hr = headRules.get(type)) != null) {
      String[] tags = hr.tags;
      int cl = constituents.length;
      int tl = tags.length;
      if (hr.leftToRight) {
        for (String tag : tags) {
          for (Parse constituent : constituents) {
            if (constituent.getType().equals(tag)) {
              return constituent.getHead();
            }
          }
        }
        return constituents[0].getHead();
      }
      else {
        for (String tag : tags) {
          for (int ci = cl - 1; ci >= 0; ci--) {
            if (constituents[ci].getType().equals(tag)) {
              return constituents[ci].getHead();
            }
          }
        }
        return constituents[cl - 1].getHead();
      }
    }
    return constituents[constituents.length - 1].getHead();
  }

  private void readHeadRules(BufferedReader str) throws IOException {
    String line;
    headRules = new HashMap<>(30);
    while ((line = str.readLine()) != null) {
      StringTokenizer st = new StringTokenizer(line);
      String num = st.nextToken();
      String type = st.nextToken();
      String dir = st.nextToken();
      String[] tags = new String[Integer.parseInt(num) - 2];
      int ti = 0;
      while (st.hasMoreTokens()) {
        tags[ti] = st.nextToken();
        ti++;
      }
      headRules.put(type, new HeadRule(dir.equals("1"), tags));
    }
  }

  @Override
  public void labelGaps(Stack<Constituent> stack) {
    if (stack.size() > 4) {
      //Constituent con0 = (Constituent) stack.get(stack.size()-1);
      Constituent con1 = stack.get(stack.size() - 2);
      Constituent con2 = stack.get(stack.size() - 3);
      Constituent con3 = stack.get(stack.size() - 4);
      Constituent con4 = stack.get(stack.size() - 5);
      // System.err.println("con0="+con0.label+" con1="+con1.label+" con2="
      // +con2.label+" con3="+con3.label+" con4="+con4.label);
      //subject extraction
      if (con1.getLabel().equals("NP") && con2.getLabel().equals("S") && con3.getLabel().equals("SBAR")) {
        con1.setLabel(con1.getLabel() + "-G");
        con2.setLabel(con2.getLabel() + "-G");
        con3.setLabel(con3.getLabel() + "-G");
      }
      //object extraction
      else if (con1.getLabel().equals("NP") && con2.getLabel().equals("VP")
          && con3.getLabel().equals("S") && con4.getLabel().equals("SBAR")) {
        con1.setLabel(con1.getLabel() + "-G");
        con2.setLabel(con2.getLabel() + "-G");
        con3.setLabel(con3.getLabel() + "-G");
        con4.setLabel(con4.getLabel() + "-G");
      }
    }
  }

  /**
   * Serializes the head rules via a {@link Writer} in a format suitable for loading
   * the head rules again. The encoding must be taken into account while
   * working with the writer and reader.
   * <p>
   * Once the entries have been written, the {@code writer} is flushed.
   * <p>
   * Note:
   * The {@code writer} remains open after this method returns.
   *
   * @param writer The {@link Writer} to write the head rules to.
   * @throws IOException Thrown if IO errors occurred during write operation.
   */
  public void serialize(Writer writer) throws IOException {

    for (Entry<String, HeadRule> entry : headRules.entrySet()) {
      String type = entry.getKey();
      HeadRule headRule = entry.getValue();

      // write num of tags
      writer.write(Integer.toString(headRule.tags.length + 2));
      writer.write(' ');

      // write type
      writer.write(type);
      writer.write(' ');

      // write l2r true == 1
      if (headRule.leftToRight)
        writer.write("1");
      else
        writer.write("0");

      // write tags
      for (String tag : headRule.tags) {
        writer.write(' ');
        writer.write(tag);
      }

      writer.write('\n');
    }

    writer.flush();
  }

  @Override
  public int hashCode() {
    return Objects.hash(headRules, punctSet);
  }

  @Override
  public boolean equals(Object obj) {
    if (obj == this) {
      return true;
    }

    if (obj instanceof HeadRules rules) {

      return rules.headRules.equals(headRules)
          && rules.punctSet.equals(punctSet);
    }

    return false;
  }

  @Override
  public Class<?> getArtifactSerializerClass() {
    return HeadRulesSerializer.class;
  }
}
