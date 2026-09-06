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

package opennlp.tools.eval;

import java.io.File;
import java.io.IOException;
import java.math.BigInteger;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import opennlp.tools.depparse.DependencyCrossValidator;
import opennlp.tools.depparse.DependencyEvaluator;
import opennlp.tools.depparse.DependencyModel;
import opennlp.tools.depparse.DependencyParser;
import opennlp.tools.depparse.DependencyParserME;
import opennlp.tools.depparse.FeedforwardDependencyModel;
import opennlp.tools.depparse.FeedforwardDependencyParser;
import opennlp.tools.depparse.FeedforwardDependencyTrainer;
import opennlp.tools.formats.conllu.ConlluDependencySampleStream;
import opennlp.tools.formats.conllu.ConlluTagset;
import opennlp.tools.util.MarkableFileInputStreamFactory;
import opennlp.tools.util.Parameters;
import opennlp.tools.util.TrainingParameters;

/**
 * Measures the accuracy of the two dependency parsers on Universal Dependencies 2.0
 * treebanks and pins the exact scores.
 *
 * <p>Every test trains from scratch on a treebank's training split and scores the parser
 * on sentences it has not seen: the treebank's development split, or, for the English
 * cross validation, the held-out fold. The treebank's sentence segmentation, tokens, and
 * universal part-of-speech tags are the parser input, so the scores measure parsing by
 * itself and not the errors of an upstream pipeline. Four numbers are pinned per run:
 * the unlabeled attachment score (UAS, the fraction of tokens with the correct head), the
 * labeled attachment score (LAS, the fraction with the correct head and relation label),
 * and both again over the tokens that are not tagged {@code PUNCT}, the customary
 * reporting convention for Universal Dependencies.</p>
 *
 * <p>The transition parser is the maximum-entropy arc-standard parser trained with a
 * feature cutoff of {@value #TRANSITION_CUTOFF}; the feedforward parser is trained with
 * {@link FeedforwardDependencyTrainer.Settings#defaults()} and parsed greedily. The
 * transition parser is evaluated on English, German, Spanish, and French, the feedforward
 * parser on English and Spanish. The English cross validation uses
 * {@value #ENGLISH_FOLDS} folds of the training split, the same fold count as the
 * constituency parser evaluation.</p>
 */
public class UniversalDependencyParserEval extends AbstractEvalTest {

  /** The feature cutoff of the transition parser. */
  private static final int TRANSITION_CUTOFF = 5;

  /** The fold count of the English cross validation. */
  private static final int ENGLISH_FOLDS = 5;

  /**
   * One treebank: its language code and its training and development splits.
   *
   * @param language The ISO 639-3 code passed to the transition trainer.
   * @param train The training split.
   * @param dev The development split.
   */
  private record Treebank(String language, File train, File dev) {

    /**
     * Locates a treebank under {@code ud20} in the data directory.
     *
     * @param language The ISO 639-3 code.
     * @param directory The treebank directory name, for example {@code UD_English}.
     * @param prefix The split file prefix, for example {@code en}.
     * @return The treebank. Never {@code null}.
     * @throws IOException Thrown if the data directory is not set or missing.
     */
    static Treebank of(String language, String directory, String prefix) throws IOException {
      final File base = new File(getOpennlpDataDir(), "ud20/" + directory);
      return new Treebank(language, new File(base, prefix + "-ud-train.conllu"),
          new File(base, prefix + "-ud-dev.conllu"));
    }
  }

  /**
   * The scores of one run.
   *
   * @param words The number of scored tokens.
   * @param wordsExcludingPunctuation The number of scored tokens not tagged {@code PUNCT}.
   * @param uas The unlabeled attachment score over all tokens.
   * @param las The labeled attachment score over all tokens.
   * @param uasExcludingPunctuation The unlabeled attachment score without punctuation.
   * @param lasExcludingPunctuation The labeled attachment score without punctuation.
   */
  private record Scores(long words, long wordsExcludingPunctuation, double uas, double las,
      double uasExcludingPunctuation, double lasExcludingPunctuation) {

    /**
     * Reads the scores of a finished evaluation.
     *
     * @param evaluator The evaluator after {@link DependencyEvaluator#evaluate}.
     * @return The scores. Never {@code null}.
     */
    static Scores of(DependencyEvaluator evaluator) {
      return new Scores(evaluator.getWordCount(), evaluator.getWordCountExcludingPunctuation(),
          evaluator.getUas(), evaluator.getLas(), evaluator.getUasExcludingPunctuation(),
          evaluator.getLasExcludingPunctuation());
    }

    /**
     * Reads the scores of a finished cross validation.
     *
     * @param validator The validator after {@link DependencyCrossValidator#evaluate}.
     * @return The scores. Never {@code null}.
     */
    static Scores of(DependencyCrossValidator validator) {
      return new Scores(validator.getWordCount(), validator.getWordCountExcludingPunctuation(),
          validator.getUas(), validator.getLas(), validator.getUasExcludingPunctuation(),
          validator.getLasExcludingPunctuation());
    }
  }

  private static Treebank english;
  private static Treebank german;
  private static Treebank spanish;
  private static Treebank french;

  /**
   * Locates the treebank splits and verifies their digests.
   *
   * @throws Exception Thrown if the data directory or a split is missing or changed.
   */
  @BeforeAll
  static void verifyTrainingData() throws Exception {
    english = Treebank.of("eng", "UD_English", "en");
    german = Treebank.of("deu", "UD_German", "de");
    spanish = Treebank.of("spa", "UD_Spanish-AnCora", "es_ancora");
    french = Treebank.of("fra", "UD_French", "fr");
    verifyFileChecksum(english.train().toPath(),
        new BigInteger("240180699945832517506080812986784120023"));
    verifyFileChecksum(english.dev().toPath(),
        new BigInteger("77584448353380940340541536944256636015"));
    verifyFileChecksum(german.train().toPath(),
        new BigInteger("290029973628740099051462227636742189201"));
    verifyFileChecksum(german.dev().toPath(),
        new BigInteger("133330826309481994758690698938095410310"));
    verifyFileChecksum(spanish.train().toPath(),
        new BigInteger("224942804200733453179524127037951530195"));
    verifyFileChecksum(spanish.dev().toPath(),
        new BigInteger("280996187464384493180190898172297941708"));
    verifyFileChecksum(french.train().toPath(),
        new BigInteger("147594341855033303213328942622831640563"));
    verifyFileChecksum(french.dev().toPath(),
        new BigInteger("146783792803312408008054489850921785757"));
  }

  /**
   * Cross validates the transition parser on the English training split: five parsers,
   * each trained on four fifths of the split and scored on the remaining fifth, so every
   * training sentence is scored once by a parser that did not see it.
   *
   * @throws IOException Thrown if reading or training fails.
   */
  @Test
  void crossValidateTransitionParserEnglish() throws IOException {
    final DependencyCrossValidator validator = new DependencyCrossValidator(
        samples -> new DependencyParserME(
            DependencyParserME.train(english.language(), samples, transitionParameters())));
    try (ConlluDependencySampleStream train = samples(english.train())) {
      validator.evaluate(train, ENGLISH_FOLDS);
    }
    assertScores(new Scores(204585, 180906, 0.8205440281545567d, 0.7885182198108366d,
            0.8421666500834688d, 0.8065404132532917d), Scores.of(validator));
  }

  /**
   * Trains the transition parser on the English training split and scores it on the
   * development split.
   *
   * @throws IOException Thrown if reading or training fails.
   */
  @Test
  void trainAndEvalTransitionParserEnglish() throws IOException {
    assertScores(new Scores(25148, 22065, 0.8181565134404326d, 0.7861460155877207d,
            0.8372082483571267d, 0.8014502605937004d),
        evaluate(transitionParser(english), english.dev()));
  }

  /**
   * Trains the transition parser on the German training split and scores it on the
   * development split.
   *
   * @throws IOException Thrown if reading or training fails.
   */
  @Test
  void trainAndEvalTransitionParserGerman() throws IOException {
    assertScores(new Scores(12348, 10730, 0.7843375445416262d, 0.7305636540330418d,
            0.802982292637465d, 0.7413793103448276d),
        evaluate(transitionParser(german), german.dev()));
  }

  /**
   * Trains the transition parser on the Spanish AnCora training split and scores it on
   * the development split.
   *
   * @throws IOException Thrown if reading or training fails.
   */
  @Test
  void trainAndEvalTransitionParserSpanishAncora() throws IOException {
    assertScores(new Scores(52336, 46058, 0.8355243044940385d, 0.7914437480892693d,
            0.8598506231273612d, 0.8098918754613748d),
        evaluate(transitionParser(spanish), spanish.dev()));
  }

  /**
   * Trains the transition parser on the French training split and scores it on the
   * development split.
   *
   * @throws IOException Thrown if reading or training fails.
   */
  @Test
  void trainAndEvalTransitionParserFrench() throws IOException {
    assertScores(new Scores(35766, 31947, 0.8501370016216518d, 0.8191578594195604d,
            0.8794879018374182d, 0.8450245719472878d),
        evaluate(transitionParser(french), french.dev()));
  }

  /**
   * Trains the feedforward parser with its default settings on the English training
   * split and scores it on the development split.
   *
   * @throws IOException Thrown if reading or training fails.
   */
  @Test
  void trainAndEvalFeedforwardParserEnglish() throws IOException {
    assertScores(new Scores(25148, 22065, 0.8412597423254334d, 0.8174009861619215d,
            0.8539768864717879d, 0.8272830274189894d),
        evaluate(feedforwardParser(english), english.dev()));
  }

  /**
   * Trains the feedforward parser with its default settings on the Spanish AnCora
   * training split and scores it on the development split.
   *
   * @throws IOException Thrown if reading or training fails.
   */
  @Test
  void trainAndEvalFeedforwardParserSpanishAncora() throws IOException {
    assertScores(new Scores(52336, 46058, 0.8617013145826964d, 0.8279195964536838d,
            0.8780016500933605d, 0.839615267705936d),
        evaluate(feedforwardParser(spanish), spanish.dev()));
  }

  /**
   * @return The training parameters of the transition parser. Never {@code null}.
   */
  private static TrainingParameters transitionParameters() {
    final TrainingParameters parameters = TrainingParameters.defaultParams();
    parameters.put(Parameters.CUTOFF_PARAM, TRANSITION_CUTOFF);
    return parameters;
  }

  /**
   * Trains the maximum-entropy transition parser on a treebank's training split.
   *
   * @param treebank The treebank.
   * @return The trained parser. Never {@code null}.
   * @throws IOException Thrown if reading or training fails.
   */
  private static DependencyParser transitionParser(Treebank treebank) throws IOException {
    final DependencyModel model;
    try (ConlluDependencySampleStream train = samples(treebank.train())) {
      model = DependencyParserME.train(treebank.language(), train, transitionParameters());
    }
    return new DependencyParserME(model);
  }

  /**
   * Trains the feedforward parser with its default settings on a treebank's training
   * split.
   *
   * @param treebank The treebank.
   * @return The trained parser, decoding greedily. Never {@code null}.
   * @throws IOException Thrown if reading or training fails.
   */
  private static DependencyParser feedforwardParser(Treebank treebank) throws IOException {
    final FeedforwardDependencyModel model;
    try (ConlluDependencySampleStream train = samples(treebank.train())) {
      model = FeedforwardDependencyTrainer.train(train,
          FeedforwardDependencyTrainer.Settings.defaults());
    }
    return new FeedforwardDependencyParser(model);
  }

  /**
   * Scores a parser on one split.
   *
   * @param parser The parser to score.
   * @param split The CoNLL-U file with the gold trees.
   * @return The scores. Never {@code null}.
   * @throws IOException Thrown if reading the split fails.
   */
  private static Scores evaluate(DependencyParser parser, File split) throws IOException {
    final DependencyEvaluator evaluator = new DependencyEvaluator(parser);
    try (ConlluDependencySampleStream dev = samples(split)) {
      evaluator.evaluate(dev);
    }
    return Scores.of(evaluator);
  }

  /**
   * Asserts that the measured scores match the pinned ones.
   *
   * @param expected The pinned scores.
   * @param actual The measured scores.
   */
  private static void assertScores(Scores expected, Scores actual) {
    Assertions.assertEquals(expected.words(), actual.words(), "words");
    Assertions.assertEquals(expected.wordsExcludingPunctuation(),
        actual.wordsExcludingPunctuation(), "words excluding punctuation");
    Assertions.assertEquals(expected.uas(), actual.uas(), ACCURACY_DELTA, "UAS");
    Assertions.assertEquals(expected.las(), actual.las(), ACCURACY_DELTA, "LAS");
    Assertions.assertEquals(expected.uasExcludingPunctuation(),
        actual.uasExcludingPunctuation(), ACCURACY_DELTA, "UAS excluding punctuation");
    Assertions.assertEquals(expected.lasExcludingPunctuation(),
        actual.lasExcludingPunctuation(), ACCURACY_DELTA, "LAS excluding punctuation");
  }

  /**
   * Opens one split with the universal tagset.
   *
   * @param split The CoNLL-U file.
   * @return The sample stream.
   * @throws IOException Thrown if the file cannot be opened.
   */
  private static ConlluDependencySampleStream samples(File split) throws IOException {
    return new ConlluDependencySampleStream(new MarkableFileInputStreamFactory(split),
        ConlluTagset.U);
  }
}
