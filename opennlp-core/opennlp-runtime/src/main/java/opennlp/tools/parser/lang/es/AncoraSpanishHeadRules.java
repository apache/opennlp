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
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;
import java.util.Stack;
import java.util.StringTokenizer;

import opennlp.tools.parser.Constituent;
import opennlp.tools.parser.GapLabeler;
import opennlp.tools.parser.HeadRules;
import opennlp.tools.parser.Parse;
import opennlp.tools.parser.chunking.Parser;
import opennlp.tools.util.ParamChecks;
import opennlp.tools.util.model.ArtifactSerializer;
import opennlp.tools.util.model.SerializableArtifact;

/**
 * Class for storing the Ancora Spanish head rules associated with parsing. In this class
 * headrules for noun phrases are specified. The rest of the rules are
 * in opennlp-tools/lang/es/parser/es-head-rules
 * <p>
 * NOTE: This class has been adapted from opennlp.tools.parser.lang.en.HeadRules
 * <p>
 * The main change is the constituents search direction in the first for loop.
 * <p>
 * Note also the change in the return of the getHead() method:
 * In the lang.en.HeadRules class: return constituents[ci].getHead();
 * Now: return constituents[ci];
 * <p>
 * Other changes include removal of deprecated methods.
 * <p>
 * A version 2 rules file starts with the line {@code # opennlp-ancora-head-rules 2}. Each
 * following line is one rule: the number of fields, the constituent type, the direction
 * ({@code 1} searches left to right, {@code 0} right to left) and the tag patterns in priority
 * order. A tag pattern matches the complete constituent type and is built from:
 * <ul>
 *   <li>the literal characters {@code A-Z}, {@code 0-9}, {@code .} and {@code $}, matched
 *       case-sensitively,</li>
 *   <li>{@code *}, which matches zero or more characters (two in a row are rejected),</li>
 *   <li>one of the character choices {@code [MAS]}, {@code [CS]}, {@code [IS]}, {@code [12]}
 *       and {@code [AC]} of the AnCora tagset, which matches one of the listed characters.</li>
 * </ul>
 * A pattern has at most 62 elements. A file without the header line is
 * read as the regular expression form of earlier releases, limited to {@code .*} and escaped
 * {@code .} and {@code $} ({@code \.} or {@code \\.}), and converted to the patterns above;
 * any other regular expression construct is rejected.
 */
public class AncoraSpanishHeadRules implements HeadRules, GapLabeler, SerializableArtifact {

  // POS tagsets are fixed by linguistic convention (Penn Treebank: ~45 tags).
  // No single head rule will ever list more than a small fraction of the tagset.
  // 1000 gives 20x headroom over the real-world maximum and is not configurable
  // because tag counts are a linguistics constraint, not a deployment parameter.
  private static final int MAX_TAGS_PER_RULE = 1_000;
  // One bit per pattern element in a long, plus the accept bit and the shift into the next
  // state in TagPattern.matches, which must stay clear of the sign bit.
  private static final int MAX_PATTERN_STATES = 62;
  private static final String FORMAT_HEADER = "# opennlp-ancora-head-rules 2";
  private static final Set<String> CHOICES =
      Set.of("MAS", "CS", "IS", "12", "AC");

  /**
   * Reads and writes {@link AncoraSpanishHeadRules} as a model artifact, in UTF-8.
   */
  public static class HeadRulesSerializer implements ArtifactSerializer<AncoraSpanishHeadRules> {

    /**
     * {@inheritDoc}
     *
     * <p>Reads a version 2 rules file, or a file in the format of earlier releases.</p>
     *
     * @throws IllegalArgumentException Thrown if {@code in} is {@code null}.
     */
    @Override
    public AncoraSpanishHeadRules create(InputStream in) throws IOException {
      ParamChecks.requireNonNullArg(in, "in");
      return new AncoraSpanishHeadRules(new BufferedReader(
          new InputStreamReader(in, StandardCharsets.UTF_8)));
    }

    /**
     * {@inheritDoc}
     *
     * <p>Writes the version 2 format.</p>
     *
     * @throws IllegalArgumentException Thrown if {@code artifact} or {@code out} is {@code null}.
     */
    @Override
    public void serialize(AncoraSpanishHeadRules artifact, OutputStream out) throws IOException {
      ParamChecks.requireNonNullArg(artifact, "artifact");
      ParamChecks.requireNonNullArg(out, "out");
      artifact.serialize(new OutputStreamWriter(out, StandardCharsets.UTF_8));
    }
  }

  private static class HeadRule {
    public final boolean leftToRight;
    public final String[] tags;
    public final TagPattern[] tagPatterns;

    public HeadRule(boolean l2r, String[] tags, TagPattern[] tagPatterns) {
      leftToRight = l2r;

      for (String tag : tags) {
        Objects.requireNonNull(tag, "tags must not contain null values!");
      }

      this.tags = tags;
      this.tagPatterns = tagPatterns;
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

        return (rule.leftToRight == leftToRight) &&
            Arrays.equals(rule.tags, tags);
      }

      return false;
    }
  }

  private static final class TagPattern {

    private final long[] characterStates = new long[128];
    private final long wildcardStates;
    private final long acceptState;

    /** Resolves each ASCII literal or bounded choice to its active-state bit mask. */
    private TagPattern(String glob) {
      long wildcards = 0;
      int states = 0;
      for (int i = 0; i < glob.length();) {
        if (states == MAX_PATTERN_STATES) {
          throw new IllegalArgumentException("pattern exceeds " + MAX_PATTERN_STATES + " states");
        }
        long state = 1L << states;
        char c = glob.charAt(i);
        if (c == '*') {
          if (states > 0 && (wildcards & (state >>> 1)) != 0) {
            throw new IllegalArgumentException("consecutive wildcards are not supported");
          }
          wildcards |= state;
          i++;
        } else if (c == '[') {
          int end = glob.indexOf(']', i + 1);
          if (end < 0) {
            throw new IllegalArgumentException("unterminated character choice");
          }
          String choice = glob.substring(i + 1, end);
          if (!CHOICES.contains(choice)) {
            throw new IllegalArgumentException("unsupported character choice [" + choice + "]");
          }
          for (int j = 0; j < choice.length(); j++) {
            characterStates[choice.charAt(j)] |= state;
          }
          i = end + 1;
        } else {
          if (!isLiteral(c)) {
            throw new IllegalArgumentException("unsupported character '" + c + "'");
          }
          characterStates[c] |= state;
          i++;
        }
        states++;
      }
      if (states == 0) {
        throw new IllegalArgumentException("pattern must not be empty");
      }
      wildcardStates = wildcards;
      acceptState = 1L << states;
    }

    /** Advances all active states together, without backtracking or per-character allocation. */
    private boolean matches(String tag) {
      long states = epsilonClosure(1L);
      for (int i = 0; i < tag.length(); i++) {
        char c = tag.charAt(i);
        long matching = c < characterStates.length ? characterStates[c] : 0;
        long next = ((states & matching) << 1) | (states & wildcardStates);
        states = epsilonClosure(next);
        if (states == 0) {
          return false;
        }
      }
      return (epsilonClosure(states) & acceptState) != 0;
    }

    /** Skips empty wildcards; consecutive wildcards are rejected during construction. */
    private long epsilonClosure(long states) {
      return states | ((states & wildcardStates) << 1);
    }

    /** Accepts only literal characters used by Ancora tags. */
    private static boolean isLiteral(char c) {
      return c >= 'A' && c <= 'Z' || c >= '0' && c <= '9' || c == '.' || c == '$';
    }
  }

  private static final TagPattern[] TAGS1 = compileBuiltIns(
      "AQA*", "AQC*", "GRUP.A", "S.A", "NC*S*", "NP*", "NC*P*", "GRUP.NOM");
  private static final TagPattern[] TAGS2 = compileBuiltIns("$", "GRUP.A", "SA");
  private static final TagPattern[] TAGS3 = compileBuiltIns(
      "AQ0*", "AQ[AC]*", "AO*", "GRUP.A", "S.A", "RG", "RN", "GRUP.NOM");

  /** Compiles the fixed noun-phrase tag rules once. */
  private static TagPattern[] compileBuiltIns(String... globs) {
    TagPattern[] patterns = new TagPattern[globs.length];
    for (int i = 0; i < globs.length; i++) {
      patterns[i] = new TagPattern(globs[i]);
    }
    return patterns;
  }

  private Map<String, HeadRule> headRules;
  private final Set<String> punctSet;

  /**
   * Creates a new set of head rules based on the specified reader.
   *
   * @param rulesReader A {@link Reader} for a head rules file.
   *
   * @throws IllegalArgumentException Thrown if {@code rulesReader} is {@code null}.
   * @throws IOException Thrown if the head rules cannot be read or contain an unsupported rule.
   */
  public AncoraSpanishHeadRules(Reader rulesReader) throws IOException {
    ParamChecks.requireNonNullArg(rulesReader, "rulesReader");
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
    if (constituents == null || constituents.length == 0) {
      throw new IllegalArgumentException("constituents must not be null or empty");
    }
    for (Parse constituent : constituents) {
      ParamChecks.requireNonNullArg(constituent, "constituents element");
    }
    ParamChecks.requireNonNullArg(type, "type");
    if (Parser.TOK_NODE.equals(constituents[0].getType())) {
      return null;
    }
    HeadRule hr;
    if (type.equals("SN") || type.equals("GRUP.NOM")) {
      for (Parse constituent : constituents) {
        for (int t = TAGS1.length - 1; t >= 0; t--) {
          if (TAGS1[t].matches(constituent.getType())) {
            return constituent;
          }
        }
      }
      for (Parse constituent : constituents) {
        if (constituent.getType().equals("SN") || constituent.getType().equals("GRUP.NOM")) {
          return constituent;
        }
      }
      for (int ci = constituents.length - 1; ci >= 0; ci--) {
        for (int ti = TAGS2.length - 1; ti >= 0; ti--) {
          if (TAGS2[ti].matches(constituents[ci].getType())) {
            return constituents[ci];
          }
        }
      }
      for (int ci = constituents.length - 1; ci >= 0; ci--) {
        for (int ti = TAGS3.length - 1; ti >= 0; ti--) {
          if (TAGS3[ti].matches(constituents[ci].getType())) {
            return constituents[ci];
          }
        }
      }
      return constituents[constituents.length - 1].getHead();
    }
    else if ((hr = headRules.get(type)) != null) {
      TagPattern[] tagPatterns = hr.tagPatterns;
      int cl = constituents.length;
      if (hr.leftToRight) {
        for (TagPattern tagPattern : tagPatterns) {
          for (Parse constituent : constituents) {
            if (tagPattern.matches(constituent.getType())) {
              return constituent;
            }
          }
        }
        return constituents[0].getHead();
      }
      else {
        for (TagPattern tagPattern : tagPatterns) {
          for (int ci = cl - 1; ci >= 0; ci--) {
            if (tagPattern.matches(constituents[ci].getType())) {
              return constituents[ci];
            }
          }
        }
        return constituents[cl - 1].getHead();
      }
    }
    return constituents[constituents.length - 1].getHead();
  }

  /**
   * Reads all rules, in the version 2 format if the first line is its header and in the format
   * of earlier releases otherwise.
   *
   * @param str The reader positioned at the start of the rules file.
   * @throws IOException Thrown if the rules cannot be read, the header is unknown, or a rule is
   *     malformed.
   */
  private void readHeadRules(BufferedReader str) throws IOException {
    headRules = new LinkedHashMap<>(60);
    String line = str.readLine();
    boolean versionTwo = FORMAT_HEADER.equals(line);
    int lineNumber = 1;
    if (line != null && line.startsWith("#") && !versionTwo) {
      throw new IOException("Unsupported Ancora head rules header at line 1: " + line);
    }
    if (line != null && !versionTwo) {
      readHeadRule(line, lineNumber, true);
    }
    while ((line = str.readLine()) != null) {
      readHeadRule(line, ++lineNumber, !versionTwo);
    }
  }

  /**
   * Validates one rule, compiles its tag patterns and adds it to the rules.
   *
   * @param line The rule line.
   * @param lineNumber The 1-based line number, for error messages.
   * @param legacy Whether the tags are regular expressions of the earlier format.
   * @throws IOException Thrown if the rule is malformed or repeats a constituent type.
   */
  private void readHeadRule(String line, int lineNumber, boolean legacy) throws IOException {
    StringTokenizer tokens = new StringTokenizer(line);
    if (!tokens.hasMoreTokens()) {
      throw ruleError(lineNumber, null, "blank lines are not supported");
    }
    String countToken = tokens.nextToken();
    int count;
    try {
      count = Integer.parseInt(countToken);
    } catch (NumberFormatException e) {
      throw ruleError(lineNumber, null, "invalid field count '" + countToken + "'", e);
    }
    if (count < 2 || count - 2 > MAX_TAGS_PER_RULE || tokens.countTokens() != count) {
      throw ruleError(lineNumber, null, "declared field count does not match the rule");
    }
    String type = tokens.nextToken();
    String direction = tokens.nextToken();
    if (!"0".equals(direction) && !"1".equals(direction)) {
      throw ruleError(lineNumber, type, "direction must be 0 or 1");
    }
    if (headRules.containsKey(type)) {
      throw ruleError(lineNumber, type, "duplicate constituent rule");
    }
    String[] tags = new String[count - 2];
    TagPattern[] patterns = new TagPattern[tags.length];
    for (int i = 0; i < tags.length; i++) {
      String source = tokens.nextToken();
      String glob = legacy ? convertLegacyPattern(source, lineNumber, type) : source;
      try {
        patterns[i] = new TagPattern(glob);
      } catch (IllegalArgumentException e) {
        throw ruleError(lineNumber, type, "unsupported tag pattern '" + source
            + "': " + e.getMessage(), e);
      }
      tags[i] = glob;
    }
    headRules.put(type, new HeadRule("1".equals(direction), tags, patterns));
  }

  /**
   * Converts a tag of the earlier regular expression format to a version 2 pattern.
   *
   * @param source The regular expression tag.
   * @param lineNumber The 1-based line number, for error messages.
   * @param type The constituent type of the rule, for error messages.
   * @return The equivalent version 2 pattern.
   * @throws IOException Thrown if {@code source} uses a construct other than {@code .*} and an
   *     escaped {@code .} or {@code $}.
   */
  private String convertLegacyPattern(String source, int lineNumber, String type)
      throws IOException {
    // The bundled INC rule lists GRUP.ADV without an escape; read its dot as a literal.
    if ("GRUP.ADV".equals(source)) {
      return source;
    }
    StringBuilder glob = new StringBuilder(source.length());
    for (int i = 0; i < source.length();) {
      char c = source.charAt(i);
      if (c == '.' && i + 1 < source.length() && source.charAt(i + 1) == '*') {
        glob.append('*');
        i += 2;
      } else if (c == '\\') {
        int escaped = i + 1;
        if (escaped < source.length() && source.charAt(escaped) == '\\') {
          escaped++;
        }
        if (escaped >= source.length()
            || source.charAt(escaped) != '.' && source.charAt(escaped) != '$') {
          throw ruleError(lineNumber, type, "unsupported legacy escape in '" + source + "'");
        }
        glob.append(source.charAt(escaped));
        i = escaped + 1;
      } else if (c == '.' || c == '$' || c == '*' || c == '+' || c == '|'
          || c == '(' || c == ')' || c == '?' || c == '{' || c == '}' || c == '^') {
        throw ruleError(lineNumber, type, "unsupported legacy regex construct in '"
            + source + "'");
      } else {
        glob.append(c);
        i++;
      }
    }
    return glob.toString();
  }

  /**
   * {@return the exception for a malformed rule, naming its line and constituent type}
   *
   * @param line The 1-based line number.
   * @param type The constituent type, or {@code null} if it was not read yet.
   * @param message The reason.
   */
  private IOException ruleError(int line, String type, String message) {
    return ruleError(line, type, message, null);
  }

  /**
   * {@return the exception for a malformed rule, naming its line and constituent type}
   *
   * @param line The 1-based line number.
   * @param type The constituent type, or {@code null} if it was not read yet.
   * @param message The reason.
   * @param cause The underlying exception, or {@code null}.
   */
  private IOException ruleError(int line, String type, String message, Throwable cause) {
    String context = type == null ? "" : " for rule " + type;
    return new IOException("Invalid Ancora head rules at line " + line + context + ": " + message,
        cause);
  }

  @Override
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
   * @throws IllegalArgumentException Thrown if {@code writer} is {@code null}.
   * @throws IOException Thrown if IO errors occurred during write operation.
   */
  public void serialize(Writer writer) throws IOException {
    ParamChecks.requireNonNullArg(writer, "writer");

    writer.write(FORMAT_HEADER);
    writer.write('\n');

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

    if (obj instanceof AncoraSpanishHeadRules rules) {

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
