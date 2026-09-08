/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package opennlp.dl;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import opennlp.tools.tokenize.WordpieceTokenizer;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Checks the complete token arrays created by the shared deep-learning encoder. */
public class SharedDlEncodingTest {

  private static final int CONCURRENT_CALLS = 16;
  private static final int CONCURRENT_WORKERS = 4;
  private static final long WAIT_SECONDS = 10;

  private static final Map<String, Integer> BERT_TOKEN_IDS = Map.ofEntries(
      Map.entry(WordpieceTokenizer.BERT_CLS_TOKEN, 101),
      Map.entry(WordpieceTokenizer.BERT_SEP_TOKEN, Integer.MAX_VALUE),
      Map.entry(WordpieceTokenizer.BERT_UNK_TOKEN, 900_001),
      Map.entry("hello", 42),
      Map.entry("world", 1_500_000_000),
      Map.entry("play", 0),
      Map.entry("##ing", 2_147_483_646),
      Map.entry("cafe", 88),
      Map.entry("Caf\u00E9", 89),
      Map.entry("\u03C3\u03BF\u03C6\u03BF\u03C2", 90),
      Map.entry("\u03A3\u039F\u03A6\u039F\u03A3", 91));

  private static final Map<String, Integer> ROBERTA_TOKEN_IDS = Map.of(
      WordpieceTokenizer.ROBERTA_CLS_TOKEN, Integer.MAX_VALUE,
      WordpieceTokenizer.ROBERTA_SEP_TOKEN, 2,
      WordpieceTokenizer.ROBERTA_UNK_TOKEN, 800_000_000,
      "hello", 0,
      "world", 500,
      "play", 10,
      "##ing", 1_500_000_000);

  private static final Map<String, Integer> ROBERTA_BERT_UNKNOWN_TOKEN_IDS = Map.of(
      WordpieceTokenizer.ROBERTA_CLS_TOKEN, 71,
      WordpieceTokenizer.ROBERTA_SEP_TOKEN, 72,
      WordpieceTokenizer.BERT_UNK_TOKEN, Integer.MAX_VALUE,
      "hello", 73);

  private static final EncodingExpectation BERT_HELLO_EXPECTATION = new EncodingExpectation(
      BERT_TOKEN_IDS, true, "Hello",
      new String[] {"[CLS]", "hello", "[SEP]"},
      new long[] {101, 42, 2_147_483_647L},
      new long[] {1, 1, 1},
      new long[] {0, 0, 0});

  private static final EncodingExpectation BERT_MIXED_EXPECTATION = new EncodingExpectation(
      BERT_TOKEN_IDS, true, "Hello playing rabbit",
      new String[] {"[CLS]", "hello", "play", "##ing", "[UNK]", "[SEP]"},
      new long[] {101, 42, 0, 2_147_483_646L, 900_001, 2_147_483_647L},
      new long[] {1, 1, 1, 1, 1, 1},
      new long[] {0, 0, 0, 0, 0, 0});

  private static final EncodingExpectation BERT_UNKNOWN_EXPECTATION = new EncodingExpectation(
      BERT_TOKEN_IDS, true, "rabbit",
      new String[] {"[CLS]", "[UNK]", "[SEP]"},
      new long[] {101, 900_001, 2_147_483_647L},
      new long[] {1, 1, 1},
      new long[] {0, 0, 0});

  private static final EncodingExpectation BERT_WORDPIECES_EXPECTATION =
      new EncodingExpectation(
          BERT_TOKEN_IDS, true, "Playing",
          new String[] {"[CLS]", "play", "##ing", "[SEP]"},
          new long[] {101, 0, 2_147_483_646L, 2_147_483_647L},
          new long[] {1, 1, 1, 1},
          new long[] {0, 0, 0, 0});

  private static final List<EncodingExpectation> CONCURRENT_EXPECTATIONS = List.of(
      BERT_HELLO_EXPECTATION,
      BERT_UNKNOWN_EXPECTATION,
      BERT_WORDPIECES_EXPECTATION);

  /** Expected output for one model-free encoding case. */
  private record EncodingExpectation(
      Map<String, Integer> tokenIds,
      boolean lowerCase,
      CharSequence input,
      String[] tokens,
      long[] ids,
      long[] mask,
      long[] types) {
  }

  /**
   * Confirms the token strings, ids, attention mask, and token types.
   *
   * @param name The case name.
   * @param expected The fixture and expected arrays.
   */
  @ParameterizedTest(name = "{0}")
  @MethodSource("encodingCases")
  void testEncodingArrays(String name, EncodingExpectation expected) {
    final Tokens actual = new ModelFreeDL(expected.tokenIds(), expected.lowerCase())
        .encode(expected.input());

    assertEncoding(expected, actual);
  }

  /** Confirms null text is rejected at the instance encoder boundary. */
  @Test
  void testRejectsNullText() {
    final ModelFreeDL encoder = new ModelFreeDL(BERT_TOKEN_IDS, true);

    final IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
        () -> encoder.encode(null));
    assertEquals("text must not be null", exception.getMessage());
  }

  /** Confirms that successive calls return independent arrays. */
  @Test
  void testRepeatedCallsReturnIndependentArrays() {
    final ModelFreeDL encoder = new ModelFreeDL(BERT_TOKEN_IDS, true);
    final Tokens first = encoder.encode("Hello");
    final Tokens second = encoder.encode("Hello");

    assertNotSame(first.tokens(), second.tokens());
    assertNotSame(first.ids(), second.ids());
    assertNotSame(first.mask(), second.mask());
    assertNotSame(first.types(), second.types());

    first.tokens()[1] = "changed";
    first.ids()[1] = -1;
    first.mask()[1] = 0;
    first.types()[1] = 1;

    assertEncoding(BERT_HELLO_EXPECTATION, second);
    assertEncoding(BERT_HELLO_EXPECTATION, encoder.encode("Hello"));
  }

  /**
   * Confirms bounded concurrent calls produce complete independent results.
   *
   * @throws InterruptedException Thrown if the test thread is interrupted.
   * @throws ExecutionException Thrown if an encoding task fails.
   * @throws TimeoutException Thrown if an encoding task exceeds the wait limit.
   */
  @Test
  void testConcurrentCallsReturnIndependentArrays()
      throws InterruptedException, ExecutionException, TimeoutException {
    final ModelFreeDL encoder = new ModelFreeDL(BERT_TOKEN_IDS, true);
    final ExecutorService executor = Executors.newFixedThreadPool(CONCURRENT_WORKERS);
    final CountDownLatch start = new CountDownLatch(1);
    final List<Future<Tokens>> futures = new ArrayList<>();
    try {
      for (int i = 0; i < CONCURRENT_CALLS; i++) {
        final EncodingExpectation expected =
            CONCURRENT_EXPECTATIONS.get(i % CONCURRENT_EXPECTATIONS.size());
        futures.add(executor.submit(() -> {
          start.await();
          return encoder.encode(expected.input());
        }));
      }
      start.countDown();

      final List<Tokens> completed = new ArrayList<>();
      for (int i = 0; i < futures.size(); i++) {
        final Future<Tokens> future = futures.get(i);
        final Tokens actual = future.get(WAIT_SECONDS, TimeUnit.SECONDS);
        assertEncoding(CONCURRENT_EXPECTATIONS.get(i % CONCURRENT_EXPECTATIONS.size()), actual);
        for (final Tokens prior : completed) {
          assertNotSame(prior.tokens(), actual.tokens());
          assertNotSame(prior.ids(), actual.ids());
          assertNotSame(prior.mask(), actual.mask());
          assertNotSame(prior.types(), actual.types());
        }
        completed.add(actual);
      }
    } finally {
      executor.shutdownNow();
      assertTrue(executor.awaitTermination(WAIT_SECONDS, TimeUnit.SECONDS));
    }
  }

  /**
   * Supplies model-free encoding fixtures.
   *
   * @return The named fixtures.
   */
  private static Stream<Arguments> encodingCases() {
    return Stream.of(
        Arguments.of("bert-empty", new EncodingExpectation(BERT_TOKEN_IDS, true, "",
            new String[] {"[CLS]", "[SEP]"},
            new long[] {101, 2_147_483_647L},
            new long[] {1, 1},
            new long[] {0, 0})),
        Arguments.of("bert-ascii-whitespace", new EncodingExpectation(
            BERT_TOKEN_IDS, true, " \t\n",
            new String[] {"[CLS]", "[SEP]"},
            new long[] {101, 2_147_483_647L},
            new long[] {1, 1},
            new long[] {0, 0})),
        Arguments.of("bert-unicode-whitespace", new EncodingExpectation(
            BERT_TOKEN_IDS, true, "\u00A0\u2028\u3000",
            new String[] {"[CLS]", "[SEP]"},
            new long[] {101, 2_147_483_647L},
            new long[] {1, 1},
            new long[] {0, 0})),
        Arguments.of("bert-known", BERT_HELLO_EXPECTATION),
        Arguments.of("bert-known-sequence", new EncodingExpectation(
            BERT_TOKEN_IDS, true, "Hello WORLD",
            new String[] {"[CLS]", "hello", "world", "[SEP]"},
            new long[] {101, 42, 1_500_000_000L, 2_147_483_647L},
            new long[] {1, 1, 1, 1},
            new long[] {0, 0, 0, 0})),
        Arguments.of("bert-unicode-separator", new EncodingExpectation(
            BERT_TOKEN_IDS, true, "Hello\u00A0WORLD",
            new String[] {"[CLS]", "hello", "world", "[SEP]"},
            new long[] {101, 42, 1_500_000_000L, 2_147_483_647L},
            new long[] {1, 1, 1, 1},
            new long[] {0, 0, 0, 0})),
        Arguments.of("bert-unknown", BERT_UNKNOWN_EXPECTATION),
        Arguments.of("bert-known-unknown", new EncodingExpectation(
            BERT_TOKEN_IDS, true, "Hello rabbit",
            new String[] {"[CLS]", "hello", "[UNK]", "[SEP]"},
            new long[] {101, 42, 900_001, 2_147_483_647L},
            new long[] {1, 1, 1, 1},
            new long[] {0, 0, 0, 0})),
        Arguments.of("bert-wordpieces", BERT_WORDPIECES_EXPECTATION),
        Arguments.of("bert-mixed", BERT_MIXED_EXPECTATION),
        Arguments.of("bert-string-builder", new EncodingExpectation(
            BERT_TOKEN_IDS, true, new StringBuilder("Hello"),
            new String[] {"[CLS]", "hello", "[SEP]"},
            new long[] {101, 42, 2_147_483_647L},
            new long[] {1, 1, 1},
            new long[] {0, 0, 0})),
        Arguments.of("bert-cased-accent", new EncodingExpectation(
            BERT_TOKEN_IDS, false, "Caf\u00E9",
            new String[] {"[CLS]", "Caf\u00E9", "[SEP]"},
            new long[] {101, 89, 2_147_483_647L},
            new long[] {1, 1, 1},
            new long[] {0, 0, 0})),
        Arguments.of("bert-cased-miss", new EncodingExpectation(
            BERT_TOKEN_IDS, false, "Hello",
            new String[] {"[CLS]", "[UNK]", "[SEP]"},
            new long[] {101, 900_001, 2_147_483_647L},
            new long[] {1, 1, 1},
            new long[] {0, 0, 0})),
        Arguments.of("bert-precomposed-accent", new EncodingExpectation(
            BERT_TOKEN_IDS, true, "CAF\u00C9",
            new String[] {"[CLS]", "cafe", "[SEP]"},
            new long[] {101, 88, 2_147_483_647L},
            new long[] {1, 1, 1},
            new long[] {0, 0, 0})),
        Arguments.of("bert-decomposed-accent", new EncodingExpectation(
            BERT_TOKEN_IDS, true, "Cafe\u0301",
            new String[] {"[CLS]", "cafe", "[SEP]"},
            new long[] {101, 88, 2_147_483_647L},
            new long[] {1, 1, 1},
            new long[] {0, 0, 0})),
        Arguments.of("bert-final-sigma", new EncodingExpectation(
            BERT_TOKEN_IDS, true, "\u03A3\u039F\u03A6\u039F\u03A3",
            new String[] {"[CLS]", "\u03C3\u03BF\u03C6\u03BF\u03C2", "[SEP]"},
            new long[] {101, 90, 2_147_483_647L},
            new long[] {1, 1, 1},
            new long[] {0, 0, 0})),
        Arguments.of("bert-cased-sigma", new EncodingExpectation(
            BERT_TOKEN_IDS, false, "\u03A3\u039F\u03A6\u039F\u03A3",
            new String[] {"[CLS]", "\u03A3\u039F\u03A6\u039F\u03A3", "[SEP]"},
            new long[] {101, 91, 2_147_483_647L},
            new long[] {1, 1, 1},
            new long[] {0, 0, 0})),
        Arguments.of("roberta-empty", new EncodingExpectation(
            ROBERTA_TOKEN_IDS, true, "",
            new String[] {"<s>", "</s>"},
            new long[] {2_147_483_647L, 2},
            new long[] {1, 1},
            new long[] {0, 0})),
        Arguments.of("roberta-known", new EncodingExpectation(
            ROBERTA_TOKEN_IDS, true, "Hello",
            new String[] {"<s>", "hello", "</s>"},
            new long[] {2_147_483_647L, 0, 2},
            new long[] {1, 1, 1},
            new long[] {0, 0, 0})),
        Arguments.of("roberta-unknown", new EncodingExpectation(
            ROBERTA_TOKEN_IDS, true, "rabbit",
            new String[] {"<s>", "<unk>", "</s>"},
            new long[] {2_147_483_647L, 800_000_000, 2},
            new long[] {1, 1, 1},
            new long[] {0, 0, 0})),
        Arguments.of("roberta-mixed", new EncodingExpectation(
            ROBERTA_TOKEN_IDS, true, "Hello rabbit WORLD",
            new String[] {"<s>", "hello", "<unk>", "world", "</s>"},
            new long[] {2_147_483_647L, 0, 800_000_000, 500, 2},
            new long[] {1, 1, 1, 1, 1},
            new long[] {0, 0, 0, 0, 0})),
        Arguments.of("roberta-wordpieces", new EncodingExpectation(
            ROBERTA_TOKEN_IDS, true, "Playing",
            new String[] {"<s>", "play", "##ing", "</s>"},
            new long[] {2_147_483_647L, 10, 1_500_000_000L, 2},
            new long[] {1, 1, 1, 1},
            new long[] {0, 0, 0, 0})),
        Arguments.of("roberta-bert-unknown", new EncodingExpectation(
            ROBERTA_BERT_UNKNOWN_TOKEN_IDS, true, "Hello rabbit",
            new String[] {"<s>", "hello", "[UNK]", "</s>"},
            new long[] {71, 73, 2_147_483_647L, 72},
            new long[] {1, 1, 1, 1},
            new long[] {0, 0, 0, 0})));
  }

  /**
   * Compares all model input arrays.
   *
   * @param expected The expected arrays.
   * @param actual The encoded arrays.
   */
  private void assertEncoding(EncodingExpectation expected, Tokens actual) {
    assertArrayEquals(expected.tokens(), actual.tokens());
    assertArrayEquals(expected.ids(), actual.ids());
    assertArrayEquals(expected.mask(), actual.mask());
    assertArrayEquals(expected.types(), actual.types());
  }
}
