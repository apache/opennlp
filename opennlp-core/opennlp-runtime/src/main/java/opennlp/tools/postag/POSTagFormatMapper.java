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

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A mapping implementation for converting between different POS tag formats.
 * This class supports conversion between Penn Treebank (PENN) and Universal Dependencies (UD) formats.
 * The conversion is based on the <a href="https://universaldependencies.org/tagset-conversion/en-penn-uposf.html">Universal Dependencies conversion table.</a>
 * Please note that when converting from UD to Penn format, there may be ambiguity in some cases.
 */
public class POSTagFormatMapper {

  private static final Logger logger = LoggerFactory.getLogger(POSTagFormatMapper.class);

  private static final Map<String, String> CONVERSION_TABLE_PENN_TO_UD = new HashMap<>();
  private static final Map<String, String> CONVERSION_TABLE_UD_TO_PENN = new HashMap<>();

  static {
    /*
     * This is a conversion table to convert PENN to UD format as described in
     * https://universaldependencies.org/tagset-conversion/en-penn-uposf.html
     */
    CONVERSION_TABLE_PENN_TO_UD.put("#", "SYM");
    CONVERSION_TABLE_PENN_TO_UD.put("$", "SYM");
    CONVERSION_TABLE_PENN_TO_UD.put("''", "PUNCT");
    CONVERSION_TABLE_PENN_TO_UD.put(",", "PUNCT");
    CONVERSION_TABLE_PENN_TO_UD.put("-LRB-", "PUNCT");
    CONVERSION_TABLE_PENN_TO_UD.put("-RRB-", "PUNCT");
    CONVERSION_TABLE_PENN_TO_UD.put(".", "PUNCT");
    CONVERSION_TABLE_PENN_TO_UD.put(":", "PUNCT");
    CONVERSION_TABLE_PENN_TO_UD.put("AFX", "ADJ");
    CONVERSION_TABLE_PENN_TO_UD.put("CC", "CCONJ");
    CONVERSION_TABLE_PENN_TO_UD.put("CD", "NUM");
    CONVERSION_TABLE_PENN_TO_UD.put("DT", "DET");
    CONVERSION_TABLE_PENN_TO_UD.put("EX", "PRON");
    CONVERSION_TABLE_PENN_TO_UD.put("FW", "X");
    CONVERSION_TABLE_PENN_TO_UD.put("HYPH", "PUNCT");
    CONVERSION_TABLE_PENN_TO_UD.put("IN", "ADP");
    CONVERSION_TABLE_PENN_TO_UD.put("JJ", "ADJ");
    CONVERSION_TABLE_PENN_TO_UD.put("JJR", "ADJ");
    CONVERSION_TABLE_PENN_TO_UD.put("JJS", "ADJ");
    CONVERSION_TABLE_PENN_TO_UD.put("LS", "X");
    CONVERSION_TABLE_PENN_TO_UD.put("MD", "VERB");
    CONVERSION_TABLE_PENN_TO_UD.put("NIL", "X");
    CONVERSION_TABLE_PENN_TO_UD.put("NN", "NOUN");
    CONVERSION_TABLE_PENN_TO_UD.put("NNP", "PROPN");
    CONVERSION_TABLE_PENN_TO_UD.put("NNPS", "PROPN");
    CONVERSION_TABLE_PENN_TO_UD.put("NNS", "NOUN");
    CONVERSION_TABLE_PENN_TO_UD.put("PDT", "DET");
    CONVERSION_TABLE_PENN_TO_UD.put("POS", "PART");
    CONVERSION_TABLE_PENN_TO_UD.put("PRP", "PRON");
    CONVERSION_TABLE_PENN_TO_UD.put("PRP$", "DET");
    CONVERSION_TABLE_PENN_TO_UD.put("RB", "ADV");
    CONVERSION_TABLE_PENN_TO_UD.put("RBR", "ADV");
    CONVERSION_TABLE_PENN_TO_UD.put("RBS", "ADV");
    CONVERSION_TABLE_PENN_TO_UD.put("RP", "ADP");
    CONVERSION_TABLE_PENN_TO_UD.put("SYM", "SYM");
    CONVERSION_TABLE_PENN_TO_UD.put("TO", "PART");
    CONVERSION_TABLE_PENN_TO_UD.put("UH", "INTJ");
    CONVERSION_TABLE_PENN_TO_UD.put("VB", "VERB");
    CONVERSION_TABLE_PENN_TO_UD.put("VBD", "VERB");
    CONVERSION_TABLE_PENN_TO_UD.put("VBG", "VERB");
    CONVERSION_TABLE_PENN_TO_UD.put("VBN", "VERB");
    CONVERSION_TABLE_PENN_TO_UD.put("VBP", "VERB");
    CONVERSION_TABLE_PENN_TO_UD.put("VBZ", "VERB");
    CONVERSION_TABLE_PENN_TO_UD.put("WDT", "DET");
    CONVERSION_TABLE_PENN_TO_UD.put("WP", "PRON");
    CONVERSION_TABLE_PENN_TO_UD.put("WP$", "DET");
    CONVERSION_TABLE_PENN_TO_UD.put("WRB", "ADV");

    /*
     * Note: The back conversion might lose information.
     */
    CONVERSION_TABLE_UD_TO_PENN.put("ADJ", "JJ");
    CONVERSION_TABLE_UD_TO_PENN.put("ADP", "IN");
    CONVERSION_TABLE_UD_TO_PENN.put("ADV", "RB");
    CONVERSION_TABLE_UD_TO_PENN.put("AUX", "MD");
    CONVERSION_TABLE_UD_TO_PENN.put("CCONJ", "CC");
    CONVERSION_TABLE_UD_TO_PENN.put("DET", "DT");
    CONVERSION_TABLE_UD_TO_PENN.put("INTJ", "UH");
    CONVERSION_TABLE_UD_TO_PENN.put("NOUN", "NN");
    CONVERSION_TABLE_UD_TO_PENN.put("NUM", "CD");
    CONVERSION_TABLE_UD_TO_PENN.put("PART", "RP");
    CONVERSION_TABLE_UD_TO_PENN.put("PRON", "PRP");
    CONVERSION_TABLE_UD_TO_PENN.put("PROPN", "NNP");
    CONVERSION_TABLE_UD_TO_PENN.put("PUNCT", ".");
    CONVERSION_TABLE_UD_TO_PENN.put("SCONJ", "IN");
    CONVERSION_TABLE_UD_TO_PENN.put("SYM", "SYM");
    CONVERSION_TABLE_UD_TO_PENN.put("VERB", "VB");
    CONVERSION_TABLE_UD_TO_PENN.put("X", "FW");
  }

  private final POSTagFormat modelFormat;

  protected POSTagFormatMapper(final String[] possibleOutcomes) {
    this.modelFormat = guessModelTagFormat(possibleOutcomes);
  }

  /**
   * Converts a list of tags to the specified format.
   *
   * @param tags a list of tags to be converted.
   * @return an array containing the converted tags with the same order and size as the given input list.
   * Note: A given tag might be {@code ?} if no mapping for the given {@code tag} could be found.
   */
  public String[] convertTags(List<String> tags) {
    Objects.requireNonNull(tags, "Supplied tags must not be NULL.");
    return tags.stream()
        .map(this::convertTag)
        .toArray(String[]::new);
  }

  /**
   * Converts a given tag to the specified format.
   *
   * @param tag no restrictions on this parameter.
   * @return the converted tag form or {@code ?} if no mapping for {@code tag} could be found.
   */
  public String convertTag(String tag) {
    switch (modelFormat) {
      case UD -> {
        return CONVERSION_TABLE_UD_TO_PENN.getOrDefault(tag, "?");
      }
      case PENN -> {
        if ("NOUN".equals(tag)) {
          logger.warn("Ambiguity detected: NN can be 'NN' or 'NNS' depending on the number. " +
              "Returning 'NN'.");
        }
        if ("PART".equals(tag)) {
          logger.warn("Ambiguity detected: PART can be 'RP' or 'TO'. Returning 'RP'.");
        }
        if ("PROPN".equals(tag)) {
          logger.warn("Ambiguity detected: Can be 'NNP' or 'NNPS. Returning 'NNP'");
        }
        if ("PUNCT".equals(tag)) {
          logger.warn("Ambiguity detected: PUNCT needs specific punctuation mapping. Returning '.'");
        }
        if ("VERB".equals(tag)) {
          logger.warn("Ambiguity detected: VERB can be 'VB', 'VBD', 'VBG', 'VBN', 'VBP', 'VBZ'. " +
              "Returning 'VERB'.");
        }
        return CONVERSION_TABLE_PENN_TO_UD.getOrDefault(tag, "?");
      }
      default -> {
        return tag;
      }
    }
  }

  /**
   *
   * @return The guessed {@link POSTagFormat}. Guaranteed to be not {@code null}.
   */
  public POSTagFormat getGuessedFormat() {
    return this.modelFormat;
  }

  /**
   * Guesses the {@link POSTagFormat} by using majority quorum.
   * @param outcomes must not be {@code null}.
   * @return the guessed {@link POSTagFormat}.
   * If the given input was empty, {@link  POSTagFormat#UNKNOWN} is returned.
   */
  private POSTagFormat guessModelTagFormat(final String[] outcomes) {
    Objects.requireNonNull(outcomes, "Outcomes must not be NULL.");
    int udMatches = 0;
    int pennMatches = 0;

    for (String outcome : outcomes) {
      if (CONVERSION_TABLE_UD_TO_PENN.containsKey(outcome)) {
        udMatches++;
      }
      if (CONVERSION_TABLE_PENN_TO_UD.containsKey(outcome)) {
        pennMatches++;
      }
    }

    if (udMatches > pennMatches) {
      return POSTagFormat.UD;
    } else if (pennMatches > udMatches) {
      return POSTagFormat.PENN;
    } else {
      logger.warn("Detected an unknown POS format.");
      return POSTagFormat.UNKNOWN;
    }
  }

  /**
   * Guesses the {@link POSTagFormat} of a given {@link POSModel}
   * @param posModel must not be {@code null}.
   * @return the guessed {@link POSTagFormat}.
   */
  public static POSTagFormat guessFormat(POSModel posModel) {
    Objects.requireNonNull(posModel, "POSModel must not be NULL.");
    Objects.requireNonNull(posModel.getPosSequenceModel(), "POSSequenceModel must not be NULL.");
    final POSTagFormatMapper mapper = new POSTagFormatMapper(posModel.getPosSequenceModel().getOutcomes());
    return mapper.getGuessedFormat();
  }

  public static class NoOp extends POSTagFormatMapper {

    protected NoOp() {
      super(new String[0]);
    }

    @Override
    public String[] convertTags(List<String> tags) {
      Objects.requireNonNull(tags, "tags must not be NULL.");
      return tags.toArray(new String[0]);
    }

    @Override
    public String convertTag(String tag) {
      return tag;
    }

    @Override
    public POSTagFormat getGuessedFormat() {
      return POSTagFormat.CUSTOM;
    }

  }
}
