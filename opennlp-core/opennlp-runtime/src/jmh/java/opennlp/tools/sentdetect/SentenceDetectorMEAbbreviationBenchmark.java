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

package opennlp.tools.sentdetect;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import opennlp.tools.dictionary.Dictionary;
import opennlp.tools.formats.ResourceAsStreamFactory;
import opennlp.tools.util.InputStreamFactory;
import opennlp.tools.util.ObjectStream;
import opennlp.tools.util.PlainTextByLineStream;
import opennlp.tools.util.StringList;
import opennlp.tools.util.TrainingParameters;

/**
 * JMH benchmark for the abbreviation veto of
 * {@link SentenceDetectorME#isAcceptableBreak(CharSequence, int, int)}.
 * <p>
 * One op is one {@link SentenceDetectorME#sentPosDetect(CharSequence)} call over a document of
 * {@code documentChars} characters. Three variants run over the same input:
 * <ul>
 *   <li>{@code noDictionary} is the floor, the veto is switched off entirely, so what is left is
 *       the shared cost of scanning and of the maxent evaluation per candidate;</li>
 *   <li>{@code legacyVeto} is {@link LegacyAbbreviationSentenceDetectorME}, which holds the
 *       previous full-text scan;</li>
 *   <li>{@code boundedWindowVeto} is the current implementation.</li>
 * </ul>
 * The {@code documentChars} axis is the point of the benchmark: the previous implementation
 * searched the whole text once per dictionary entry per candidate, so its cost per document grows
 * quadratically, while the bounded window makes it grow linearly. The {@code dictionaryEntries}
 * axis exposes the second factor of that product.
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 3, time = 2)
@Measurement(iterations = 5, time = 2)
@Fork(2)
@Threads(1)
public class SentenceDetectorMEAbbreviationBenchmark {

  /**
   * The abbreviations of the shipped English dictionary. They occur in the benchmark document.
   */
  private static final String[] PRESENT_ABBREVIATIONS = {"Mr.", "Mrs.", "Ms.", "tel."};

  @State(Scope.Benchmark)
  public static class DocumentState {

    /**
     * The document length in characters. Doubling it is what separates a quadratic veto from a
     * linear one.
     */
    @Param({"12500", "25000", "50000", "100000"})
    public int documentChars;

    /**
     * The number of dictionary entries. The shipped German list has 215, the shipped English one
     * that the runtime module tests against has 4.
     */
    @Param({"10", "200"})
    public int dictionaryEntries;

    SentenceModel model;
    String document;
    SentenceDetectorME noDictionary;
    SentenceDetectorME legacy;
    SentenceDetectorME boundedWindow;

    @Setup(Level.Trial)
    public void prepare() throws IOException {
      model = trainModel();
      document = buildDocument(documentChars);
      final Dictionary dictionary = buildDictionary(dictionaryEntries);
      noDictionary = new SentenceDetectorME(model, (Dictionary) null);
      legacy = new LegacyAbbreviationSentenceDetectorME(model, dictionary);
      boundedWindow = new SentenceDetectorME(model, dictionary);
    }
  }

  @Benchmark
  public void noDictionary(DocumentState state, Blackhole bh) {
    bh.consume(state.noDictionary.sentPosDetect(state.document));
  }

  @Benchmark
  public void legacyVeto(DocumentState state, Blackhole bh) {
    bh.consume(state.legacy.sentPosDetect(state.document));
  }

  @Benchmark
  public void boundedWindowVeto(DocumentState state, Blackhole bh) {
    bh.consume(state.boundedWindow.sentPosDetect(state.document));
  }

  /*
   * ------------------------------------------------------------------------------------------
   * Fixtures.
   * ------------------------------------------------------------------------------------------
   */

  /**
   * @return A model trained on the bundled English samples, without an abbreviation dictionary,
   *     so all three variants share one feature generator and differ only in the veto.
   * @throws IOException Thrown if the training samples cannot be read.
   */
  private static SentenceModel trainModel() throws IOException {
    final InputStreamFactory in = new ResourceAsStreamFactory(
        SentenceDetectorMEAbbreviationBenchmark.class,
        "/opennlp/tools/sentdetect/Sentences.txt");
    final ObjectStream<SentenceSample> samples = new SentenceSampleStream(
        new PlainTextByLineStream(in, StandardCharsets.UTF_8));
    return SentenceDetectorME.train("eng", samples,
        new SentenceDetectorFactory("eng", true, null, null),
        TrainingParameters.defaultParams());
  }

  /**
   * Builds a document of about {@code chars} characters that contains abbreviations at a
   * realistic rate, so the veto is reached often rather than in a corner.
   *
   * @param chars The minimum document length in characters.
   * @return The document.
   */
  private static String buildDocument(int chars) {
    final String paragraph =
        "Mr. Smith left the building at noon. She told me he lived in Edinburgh. "
            + "Mrs. Clark called tel. 555 1234 and asked for Ms. Adams. "
            + "The driver got badly injured near the old bridge. "
            + "OpenNLP provides tools for natural language processing. "
            + "I wrote him a letter right away and posted it the same day. ";
    final StringBuilder document = new StringBuilder(chars + paragraph.length());
    while (document.length() < chars) {
      document.append(paragraph);
    }
    return document.toString();
  }

  /**
   * Builds a case-insensitive dictionary of {@code entries} abbreviations, as the shipped
   * dictionaries are. The first entries occur in the document; the rest are synthetic and do not,
   * which is the realistic case, since a dictionary covers a language and a document uses a
   * handful of its entries.
   *
   * @param entries The number of entries to produce.
   * @return The dictionary.
   */
  private static Dictionary buildDictionary(int entries) {
    final Dictionary dictionary = new Dictionary(false);
    for (String abbreviation : PRESENT_ABBREVIATIONS) {
      dictionary.put(new StringList(abbreviation));
    }
    for (int i = PRESENT_ABBREVIATIONS.length; i < entries; i++) {
      dictionary.put(new StringList(String.format(Locale.ROOT, "zq%d.", i)));
    }
    return dictionary;
  }

  /**
   * Quick local iteration only: {@code forks(0)} disables JVM fork isolation
   * (unlike {@code mvn} with the {@code jmh} profile).
   * Use the Maven-invoked configuration for publishable numbers.
   */
  public static void main(String[] args) throws Exception {
    Options opt = new OptionsBuilder()
        .include(SentenceDetectorMEAbbreviationBenchmark.class.getSimpleName())
        .forks(0)
        .warmupIterations(2)
        .measurementIterations(3)
        .build();
    new Runner(opt).run();
  }
}
