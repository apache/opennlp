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

package opennlp.tools.tokenize;

import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

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
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import opennlp.tools.tokenize.lang.Factory;

/**
 * JMH benchmark for the check of the alphanumeric optimization in {@link TokenizerME}: whether a
 * whitespace-separated candidate token is accepted whole. It compares
 * {@link TokenizerCharacterPolicy#test(CharSequence)} with the regular expression that releases
 * before 3.0 stored in the model manifest for the same language, on the same candidates.
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 10, time = 1)
@Fork(2)
public class TokenizerCharacterPolicyBenchmark {

  /** Candidate tokens of two or more characters, as the tokenizer checks them. */
  private static final String[] CANDIDATES = {
      "The", "driver", "injured", "accident.", "Edinburgh", "Edinburgh.", "letter", "right",
      "OpenNLP", "provides", "tools", "NLP.", "Pierre", "Vinken,", "61", "years", "old,",
      "nonexecutive", "Nov.", "29.", "555-1234", "(anytime).", "3.5\"", "don't", "it's",
      "N.V.,", "e-mail", "2026", "acción", "niño", "señor", "árbol", "Málaga,", "über",
      "Straße", "Ärger", "garçon", "Œuvre", "żółć", "Łódź", "città", "perché", "naïve",
      "coöperate", "café", "x86_64", "C++", "@user", "#tag", "https://opennlp.apache.org"
  };

  /** The language whose policy and released expression are compared. */
  @State(Scope.Benchmark)
  public static class LanguageState {

    @Param({"en", "es", "de", "pl"})
    String language;

    TokenizerCharacterPolicy policy;

    Pattern pattern;

    @Setup(Level.Trial)
    public void setUp() {
      policy = new Factory().getCharacterPolicy(language);
      pattern = Pattern.compile(switch (language) {
        case "es" -> "^[0-9a-záéíóúüýñA-ZÁÉÍÓÚÝÑ]+$";
        case "de" -> "^[A-Za-z0-9äéöüÄÉÖÜß]+$";
        case "pl" -> "^[A-Za-z0-9żźćńółęąśŻŹĆĄŚĘŁÓŃ]+$";
        default -> "^[A-Za-z0-9]+$";
      });
      for (String candidate : CANDIDATES) {
        if (policy.test(candidate) != pattern.matcher(candidate).matches()) {
          throw new IllegalStateException("policy and expression disagree on " + candidate);
        }
      }
    }
  }

  /**
   * Checks every candidate with the character policy.
   *
   * @param state The language state.
   * @param bh The sink for the results.
   */
  @Benchmark
  public void characterPolicy(LanguageState state, Blackhole bh) {
    for (String candidate : CANDIDATES) {
      bh.consume(state.policy.test(candidate));
    }
  }

  /**
   * Checks every candidate with the regular expression of earlier releases.
   *
   * @param state The language state.
   * @param bh The sink for the results.
   */
  @Benchmark
  public void regularExpression(LanguageState state, Blackhole bh) {
    for (String candidate : CANDIDATES) {
      bh.consume(state.pattern.matcher(candidate).matches());
    }
  }

  /**
   * Quick local iteration only: {@code forks(0)} disables JVM fork isolation
   * (unlike {@code mvn} with the {@code jmh} profile).
   * Use the Maven-invoked configuration for publishable numbers.
   */
  public static void main(String[] args) throws Exception {
    Options opt = new OptionsBuilder()
        .include(TokenizerCharacterPolicyBenchmark.class.getSimpleName())
        .forks(0)
        .warmupIterations(3)
        .measurementIterations(5)
        .build();
    new Runner(opt).run();
  }
}
