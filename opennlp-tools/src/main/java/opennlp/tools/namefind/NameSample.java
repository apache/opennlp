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

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import opennlp.tools.commons.Sample;
import opennlp.tools.tokenize.WhitespaceTokenizer;
import opennlp.tools.util.Span;

/**
 * Encapsulates names for a single unit of text.
 */
public class NameSample implements Sample {

  private static final long serialVersionUID = 1655333056555270688L;
  private final String id;
  private final List<String> sentence;
  private final List<Span> names;
  private final String[][] additionalContext;
  private final boolean isClearAdaptiveData;

  /** The default type value when there is no type in training data. */
  public static final String DEFAULT_TYPE = "default";

  /**
   * Initializes a {@link NameSample} instance with given parameters.
   * 
   * @param id The identifier to use.
   * @param sentence The tokens representing a training sentence. Must not be {@code null}.
   * @param names The {@link Span names} to use.
   * @param additionalContext Additional context in a 2-dimensional array.
   * @param clearAdaptiveData If {@code true} the adaptive data of the feature generators is cleared.
   *
   * @throws RuntimeException Thrown if name spans are overlapping.
   */
  public NameSample(String id, String[] sentence, Span[] names, String[][] additionalContext,
                    boolean clearAdaptiveData) {
    this.id = id;

    Objects.requireNonNull(sentence, "sentence must not be null");

    if (names == null) {
      names = new Span[0];
    }

    this.sentence = Collections.unmodifiableList(new ArrayList<>(Arrays.asList(sentence)));
    List<Span> namesList = Arrays.asList(names);
    Collections.sort(namesList);
    this.names = Collections.unmodifiableList(namesList);

    if (additionalContext != null) {
      this.additionalContext = new String[additionalContext.length][];

      for (int i = 0; i < additionalContext.length; i++) {
        this.additionalContext[i] = new String[additionalContext[i].length];
        System.arraycopy(additionalContext[i], 0, this.additionalContext[i], 0, additionalContext[i].length);
      }
    }
    else {
      this.additionalContext = null;
    }
    isClearAdaptiveData = clearAdaptiveData;

    // Check that name spans are not overlapping, otherwise throw exception
    if (this.names.size() > 1) {
      for (int i = 1; i < this.names.size(); i++) {
        if (this.names.get(i).getStart() < this.names.get(i - 1).getEnd()) {
          throw new RuntimeException(String.format("name spans %s and %s are overlapped in file: %s," +
                  " sentence: %s",
              this.names.get(i - 1), this.names.get(i), id, this.sentence.toString()));
        }
      }
    }
  }
  
  /**
   * Initializes a {@link NameSample} instance with given parameters.
   *
   * @param sentence The tokens representing a sentence. Must not be {@code null}.
   * @param names The {@link Span names} to use.
   * @param additionalContext Additional context in a 2-dimensional array.
   * @param clearAdaptiveData If {@code true} the adaptive data of the feature generators is cleared.
   *
   * @throws RuntimeException Thrown if name spans are overlapping.
   */
  public NameSample(String[] sentence, Span[] names,
      String[][] additionalContext, boolean clearAdaptiveData) {
    this(null, sentence, names, additionalContext, clearAdaptiveData);
  }

  /**
   * Initializes a {@link NameSample} instance with given parameters.
   *
   * @param sentence The tokens representing a sentence. Must not be {@code null}.
   * @param names The {@link Span names} to use.
   * @param clearAdaptiveData If {@code true} the adaptive data of the feature generators is cleared.
   *
   * @throws RuntimeException Thrown if name spans are overlapping.
   */
  public NameSample(String[] sentence, Span[] names, boolean clearAdaptiveData) {
    this(sentence, names, null, clearAdaptiveData);
  }

  /**
   * @return Retrieves the current identifier. May be {@code null}.
   */
  public String getId() {
    return id;
  }

  /**
   * @return Retrieves the sentence in tokenized form.
   */
  public String[] getSentence() {
    return sentence.toArray(new String[0]);
  }

  /**
   * @return Retrieves the {@link Span names}.
   */
  public Span[] getNames() {
    return names.toArray(new Span[0]);
  }


  /**
   * @return Retrieves additional context. May be {@code null}.
   */
  public String[][] getAdditionalContext() {
    return additionalContext;
  }

  /**
   * @return {@code true} if the adaptive data of the feature generators are cleared,
   *         {@code false} otherwise.
   */
  public boolean isClearAdaptiveDataSet() {
    return isClearAdaptiveData;
  }

  @Override
  public int hashCode() {
    return Objects.hash(Arrays.hashCode(getSentence()), Arrays.hashCode(getNames()),
        Arrays.hashCode(getAdditionalContext()), isClearAdaptiveDataSet());
  }

  @Override
  public boolean equals(Object obj) {

    if (this == obj) {
      return true;
    }

    if (obj instanceof NameSample a) {

      return Arrays.equals(getSentence(), a.getSentence()) &&
          Arrays.equals(getNames(), a.getNames()) &&
          Arrays.equals(getAdditionalContext(), a.getAdditionalContext()) &&
          isClearAdaptiveDataSet() == a.isClearAdaptiveDataSet();
    }

    return false;
  }

  @Override
  public String toString() {
    StringBuilder result = new StringBuilder();

    // If adaptive data must be cleared insert an empty line
    // before the sample sentence line
    if (isClearAdaptiveDataSet())
      result.append("\n");

    for (int tokenIndex = 0; tokenIndex < sentence.size(); tokenIndex++) {
      // token

      for (Span name : names) {
        if (name.getStart() == tokenIndex) {
          // check if nameTypes is null, or if the nameType for this specific
          // entity is empty. If it is, we leave the nameType blank.
          if (name.getType() == null) {
            result.append(NameSampleDataStream.START_TAG).append(' ');
          }
          else {
            result.append(NameSampleDataStream.START_TAG_PREFIX).append(name.getType()).append("> ");
          }
        }
        if (name.getEnd() == tokenIndex) {
          result.append(NameSampleDataStream.END_TAG).append(' ');
        }
      }

      result.append(sentence.get(tokenIndex)).append(' ');
    }

    if (sentence.size() > 1)
      result.setLength(result.length() - 1);

    for (Span name : names) {
      if (name.getEnd() == sentence.size()) {
        result.append(' ').append(NameSampleDataStream.END_TAG);
      }
    }

    return result.toString();
  }

  private static String errorTokenWithContext(String[] sentence, int index) {

    StringBuilder errorString = new StringBuilder();

    // two token before
    if (index > 1)
      errorString.append(sentence[index - 2]).append(" ");

    if (index > 0)
      errorString.append(sentence[index - 1]).append(" ");

    // token itself
    errorString.append("###");
    errorString.append(sentence[index]);
    errorString.append("###").append(" ");

    // two token after
    if (index + 1 < sentence.length)
      errorString.append(sentence[index + 1]).append(" ");

    if (index + 2 < sentence.length)
      errorString.append(sentence[index + 2]);

    return errorString.toString();
  }

  private static final Pattern START_TAG_PATTERN = Pattern.compile("<START(:([^:>\\s]*))?>");


  /**
   * Parses given input into a {@link NameSample}.
   * 
   * @param taggedTokens The input data to parse.
   * @param clearAdaptiveData {@code true} if the adaptive data of the feature generators should be cleared,
   *                          {@code false} otherwise.
   * @return A {@link NameSample} instance resulting from the parsing.
   * @throws IOException Thrown if IO errors occurred during parsing.
   */
  public static NameSample parse(String taggedTokens, boolean clearAdaptiveData) throws IOException {
    return parse(taggedTokens, DEFAULT_TYPE, clearAdaptiveData);
  }

  /**
   * Parses given input into a {@link NameSample}.
   *
   * @param taggedTokens The input data to parse.
   * @param defaultType The type to set by default.
   * @param clearAdaptiveData {@code true} if the adaptive data of the feature generators should be cleared,
   *                          {@code false} otherwise.
   * @return A {@link NameSample} instance resulting from the parsing.
   * @throws IOException Thrown if IO errors occurred during parsing.
   */
  // TODO: Should throw another exception, and then convert it into an IOException in the stream
  public static NameSample parse(String taggedTokens, String defaultType, boolean clearAdaptiveData)
          throws IOException {

    String[] parts = WhitespaceTokenizer.INSTANCE.tokenize(taggedTokens);

    List<String> tokenList = new ArrayList<>(parts.length);
    List<Span> nameList = new ArrayList<>();

    String nameType = defaultType;
    int startIndex = -1;
    int wordIndex = 0;

    // we check if at least one name has the a type. If no one has, we will
    // leave the NameType property of NameSample null.
    boolean catchingName = false;

    for (int pi = 0; pi < parts.length; pi++) {
      Matcher startMatcher = START_TAG_PATTERN.matcher(parts[pi]);
      if (startMatcher.matches()) {
        if (catchingName) {
          throw new IOException("Found unexpected annotation" +
              " while handling a name sequence: " + errorTokenWithContext(parts, pi));
        }
        catchingName = true;
        startIndex = wordIndex;
        String nameTypeFromSample = startMatcher.group(2);
        if (nameTypeFromSample != null) {
          if (nameTypeFromSample.length() == 0) {
            throw new IOException("Missing a name type: " + errorTokenWithContext(parts, pi));
          }
          nameType = nameTypeFromSample;
        }

      }
      else if (parts[pi].equals(NameSampleDataStream.END_TAG)) {
        if (!catchingName) {
          throw new IOException("Found unexpected annotation: " + errorTokenWithContext(parts, pi));
        }
        catchingName = false;
        // create name
        nameList.add(new Span(startIndex, wordIndex, nameType));

      }
      else {
        tokenList.add(parts[pi]);
        wordIndex++;
      }
    }
    String[] sentence = tokenList.toArray(new String[0]);
    Span[] names = nameList.toArray(new Span[0]);

    return new NameSample(sentence, names, clearAdaptiveData);
  }
}
