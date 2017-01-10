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


package opennlp.tools.postag;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import opennlp.tools.tokenize.WhitespaceTokenizer;
import opennlp.tools.util.InvalidFormatException;

/**
 * Represents an pos-tagged sentence.
 */
public class POSSample {

  private List<String> sentence;

  private List<String> tags;

  private final String[][] additionalContext;

  public POSSample(String sentence[], String tags[]) {
    this(sentence, tags, null);
  }

  public POSSample(List<String> sentence, List<String> tags) {
    this(sentence, tags, null);
  }

  public POSSample(List<String> sentence, List<String> tags,
      String[][] additionalContext) {
    this.sentence = Collections.unmodifiableList(sentence);
    this.tags = Collections.unmodifiableList(tags);

    checkArguments();
    String[][] ac;
    if (additionalContext != null) {
      ac = new String[additionalContext.length][];

      for (int i = 0; i < additionalContext.length; i++) {
        ac[i] = new String[additionalContext[i].length];
        System.arraycopy(additionalContext[i], 0, ac[i], 0,
            additionalContext[i].length);
      }
    } else {
      ac = null;
    }
    this.additionalContext = ac;
  }

  public POSSample(String sentence[], String tags[],
      String[][] additionalContext) {
    this(Arrays.asList(sentence), Arrays.asList(tags), additionalContext);
  }

  private void checkArguments() {
    if (sentence.size() != tags.size()) {
      throw new IllegalArgumentException(
        "There must be exactly one tag for each token. tokens: " + sentence.size() +
            ", tags: " + tags.size());
    }

    if (sentence.contains(null)) {
      throw new IllegalArgumentException("null elements are not allowed in sentence tokens!");
    }
    if (tags.contains(null)) {
      throw new IllegalArgumentException("null elements are not allowed in tags!");
    }
  }

  public String[] getSentence() {
    return sentence.toArray(new String[sentence.size()]);
  }

  public String[] getTags() {
    return tags.toArray(new String[tags.size()]);
  }

  public String[][] getAddictionalContext() {
    return this.additionalContext;
  }

  @Override
  public String toString() {

    StringBuilder result = new StringBuilder();

    for (int i = 0; i < getSentence().length; i++) {
      result.append(getSentence()[i]);
      result.append('_');
      result.append(getTags()[i]);
      result.append(' ');
    }

    if (result.length() > 0) {
      // get rid of last space
      result.setLength(result.length() - 1);
    }

    return result.toString();
  }

  public static POSSample parse(String sentenceString) throws InvalidFormatException {

    String tokenTags[] = WhitespaceTokenizer.INSTANCE.tokenize(sentenceString);

    String sentence[] = new String[tokenTags.length];
    String tags[] = new String[tokenTags.length];

    for (int i = 0; i < tokenTags.length; i++) {
      int split = tokenTags[i].lastIndexOf("_");

      if (split == -1) {
        throw new InvalidFormatException("Cannot find \"_\" inside token '" + tokenTags[i] + "'!");
      }

      sentence[i] = tokenTags[i].substring(0, split);
      tags[i] = tokenTags[i].substring(split + 1);
    }

    return new POSSample(sentence, tags);
  }

  @Override
  public int hashCode() {
    return Objects.hash(Arrays.hashCode(getSentence()), Arrays.hashCode(getTags()));
  }

  @Override
  public boolean equals(Object obj) {
    if (obj == this) {
      return true;
    }

    if (obj instanceof POSSample) {
      POSSample a = (POSSample) obj;

      return Arrays.equals(getSentence(), a.getSentence())
          && Arrays.equals(getTags(), a.getTags());
    }

    return this == obj;
  }
}
