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

package opennlp.tools.parser.lang.es;

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
import java.util.Objects;
import java.util.Set;
import java.util.Stack;
import java.util.StringTokenizer;

import opennlp.tools.parser.Constituent;
import opennlp.tools.parser.GapLabeler;
import opennlp.tools.parser.HeadRules;
import opennlp.tools.parser.Parse;
import opennlp.tools.parser.chunking.Parser;
import opennlp.tools.util.model.ArtifactSerializer;
import opennlp.tools.util.model.SerializableArtifact;

/**
 * Class for storing the Ancora Spanish head rules associated with parsing. In this class
 * headrules for noun phrases are specified. The rest of the rules are
 * in opennlp-tools/lang/es/parser/es-head-rules
 *
 * NOTE: This class has been adapted from opennlp.tools.parser.lang.en.HeadRules
 *
 * The main change is the constituents search direction in the first for loop.
 *
 * Note also the change in the return of the getHead() method:
 * In the lang.en.HeadRules class: return constituents[ci].getHead();
 * Now: return constituents[ci];
 *
 * Other changes include removal of deprecated methods.
 *
 */
public class AncoraSpanishHeadRules implements HeadRules, GapLabeler, SerializableArtifact {

  public static class HeadRulesSerializer implements ArtifactSerializer<AncoraSpanishHeadRules> {

    public AncoraSpanishHeadRules create(InputStream in) throws IOException {
      return new AncoraSpanishHeadRules(new BufferedReader(
          new InputStreamReader(in, StandardCharsets.UTF_8)));
    }

    public void serialize(opennlp.tools.parser.lang.es.AncoraSpanishHeadRules artifact, OutputStream out)
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
        Objects.requireNonNull(tag, "tags must not contain null values!");
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

      if (obj instanceof HeadRule) {
        HeadRule rule = (HeadRule) obj;

        return (rule.leftToRight == leftToRight) &&
            Arrays.equals(rule.tags, tags);
      }

      return false;
    }
  }

  private Map<String, HeadRule> headRules;
  private Set<String> punctSet;



  /**
   * Creates a new set of head rules based on the specified reader.
   *
   * @param rulesReader the head rules reader.
   *
   * @throws IOException if the head rules reader can not be read.
   */
  public AncoraSpanishHeadRules(Reader rulesReader) throws IOException {
    BufferedReader in = new BufferedReader(rulesReader);
    readHeadRules(in);

    punctSet = new HashSet<>();
    punctSet.add(".");
    punctSet.add(",");
    punctSet.add("``");
    punctSet.add("''");
    //punctSet.add(":");
  }

  public Set<String> getPunctuationTags() {
    return punctSet;
  }

  public Parse getHead(Parse[] constituents, String type) {
    if (Parser.TOK_NODE.equals(constituents[0].getType())) {
      return null;
    }
    HeadRule hr;
    if (type.equals("SN") || type.equals("GRUP.NOM")) {
      String[] tags1 = {"AQA.*","AQC.*","GRUP\\.A","S\\.A","NC.*S.*", "NP.*","NC.*P.*", "GRUP\\.NOM"};

      for (int i = 0; i < constituents.length; i++) {
        for (int t = tags1.length - 1; t >= 0; t--) {
          if (constituents[i].getType().matches(tags1[t])) {
            return constituents[i];
          }
        }
      }
      for (int ci = 0; ci < constituents.length; ci++) {
        if (constituents[ci].getType().equals("SN") || constituents[ci].getType().equals("GRUP.NOM")) {
          return constituents[ci];
        }
      }
      String[] tags2 = {"\\$","GRUP\\.A","SA"};
      for (int ci = constituents.length - 1; ci >= 0; ci--) {
        for (int ti = tags2.length - 1; ti >= 0; ti--) {
          if (constituents[ci].getType().matches(tags2[ti])) {
            return constituents[ci];
          }
        }
      }
      String[] tags3 = {"AQ0.*", "AQ[AC].*","AO.*","GRUP\\.A","S\\.A","RG","RN","GRUP\\.NOM"};
      for (int ci = constituents.length - 1; ci >= 0; ci--) {
        for (int ti = tags3.length - 1; ti >= 0; ti--) {
          if (constituents[ci].getType().matches(tags3[ti])) {
            return constituents[ci];
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
        for (int ti = 0; ti < tl; ti++) {
          for (int ci = 0; ci < cl; ci++) {
            if (constituents[ci].getType().matches(tags[ti])) {
              return constituents[ci];
            }
          }
        }
        return constituents[0].getHead();
      }
      else {
        for (int ti = 0; ti < tl; ti++) {
          for (int ci = cl - 1; ci >= 0; ci--) {
            if (constituents[ci].getType().matches(tags[ti])) {
              return constituents[ci];
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
    headRules = new HashMap<>(60);
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

  public void labelGaps(Stack<Constituent> stack) {
    if (stack.size() > 4) {
      //Constituent con0 = (Constituent) stack.get(stack.size()-1);
      Constituent con1 = stack.get(stack.size() - 2);
      Constituent con2 = stack.get(stack.size() - 3);
      Constituent con3 = stack.get(stack.size() - 4);
      Constituent con4 = stack.get(stack.size() - 5);

      //subject extraction
      if (con1.getLabel().equals("SN")
          && con2.getLabel().equals("S") && con3.getLabel().equals("GRUP.NOM")) {
        con1.setLabel(con1.getLabel() + "-G");
        con2.setLabel(con2.getLabel() + "-G");
        con3.setLabel(con3.getLabel() + "-G");
      }
      //object extraction
      else if (con1.getLabel().equals("SN") && con2.getLabel().equals("GRUP.VERB")
          && con3.getLabel().equals("S") && con4.getLabel().equals("GRUP.NOM")) {
        con1.setLabel(con1.getLabel() + "-G");
        con2.setLabel(con2.getLabel() + "-G");
        con3.setLabel(con3.getLabel() + "-G");
        con4.setLabel(con4.getLabel() + "-G");
      }
    }
  }

  /**
   * Writes the head rules to the writer in a format suitable for loading
   * the head rules again with the constructor. The encoding must be
   * taken into account while working with the writer and reader.
   * <p>
   * After the entries have been written, the writer is flushed.
   * The writer remains open after this method returns.
   *
   * @param writer
   * @throws IOException
   */
  public void serialize(Writer writer) throws IOException {

    for (String type : headRules.keySet()) {

      HeadRule headRule = headRules.get(type);

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

    if (obj instanceof AncoraSpanishHeadRules) {
      AncoraSpanishHeadRules rules = (AncoraSpanishHeadRules) obj;

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
