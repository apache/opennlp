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

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import opennlp.tools.tokenize.WordpieceTokenizer;

import static opennlp.dl.vectors.TinyVectorModels.TINY_VECTORS;
import static opennlp.dl.vectors.TinyVectorModels.model;
import static opennlp.dl.vectors.TinyVectorModels.vocab;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Checks that {@link AbstractDL} keeps the {@link OrtSession.SessionOptions} it was given open
 * until {@link AbstractDL#close()}, closes the session before those options, still closes the
 * options when construction fails, and keeps a session close failure when closing the options
 * fails too.
 */
class AbstractDLSessionOptionsTest {

  /** Special tokens a model-free encoder needs, and nothing else. */
  private static final Map<String, Integer> TOKENIZER_VOCAB =
      Map.of("[CLS]", 0, "[SEP]", 1, "[UNK]", 2);

  /**
   * The options remain usable after the session has been created, and {@link AbstractDL#close()}
   * closes the session before it closes the options. A second {@code close()} does neither again.
   */
  @Test
  void testOptionsStayOpenUntilClose(@TempDir final Path dir) throws IOException, OrtException {
    final TrackingOptions options = new TrackingOptions();
    final OptionsOwner owner = new OptionsOwner(model(dir, TINY_VECTORS), vocab(dir), options);
    options.watch(owner.session());

    assertFalse(options.wasClosed());
    options.setIntraOpNumThreads(1);
    assertNotNull(owner.session().getInputInfo());

    owner.close();

    assertTrue(options.wasClosed());
    assertTrue(options.sessionWasClosedFirst());
    assertOptionsClosed(options);
    assertThrows(IllegalStateException.class, () -> owner.session().getInputInfo());

    owner.close();
    assertOptionsClosed(options);
    assertEquals(1, options.closes());
  }

  /**
   * A file ONNX Runtime cannot load as a model never returns an instance, and the options passed
   * to the constructor are closed.
   */
  @Test
  void testSessionCreationFailureClosesOptions(@TempDir final Path dir) throws IOException {
    final File model = Files.writeString(dir.resolve("not-a-model.onnx"), "not onnx").toFile();
    final TrackingOptions options = new TrackingOptions();

    assertThrows(OrtException.class, () -> new OptionsOwner(model, vocab(dir), options));

    assertTrue(options.wasClosed());
    assertOptionsClosed(options);
  }

  /**
   * Vocabulary loading runs after the session exists. When it fails, the constructor closes the
   * options itself, because {@link AbstractDL#close()} is never called.
   */
  @Test
  void testVocabularyFailureClosesOptions(@TempDir final Path dir) throws IOException {
    final File vocabulary = Files.writeString(dir.resolve("vocab.txt"), "hello\n").toFile();
    final TrackingOptions options = new TrackingOptions();

    final IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
        () -> new OptionsOwner(model(dir, TINY_VECTORS), vocabulary, options));

    assertEquals("vocabulary must contain special token '"
        + WordpieceTokenizer.BERT_CLS_TOKEN + "'", e.getMessage());
    assertTrue(options.wasClosed());
    assertOptionsClosed(options);
  }

  /**
   * A null model is rejected before a session exists, and the options passed to the constructor
   * are still closed.
   */
  @Test
  void testNullModelClosesOptions(@TempDir final Path dir) throws IOException {
    final TrackingOptions options = new TrackingOptions();

    final IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
        () -> new OptionsOwner(null, vocab(dir), options));

    assertEquals("model must not be null", e.getMessage());
    assertTrue(options.wasClosed());
    assertEquals(1, options.closes());
    assertOptionsClosed(options);
  }

  /**
   * When the session is already closed and closing the options also fails,
   * {@link AbstractDL#close()} throws the session failure and attaches the options failure
   * with {@link Throwable#addSuppressed(Throwable)}. A second {@code close()} does neither again.
   */
  @Test
  void testOptionsCloseFailureIsSuppressedWhenSessionCloseFails(@TempDir final Path dir)
      throws IOException, OrtException {
    final IllegalStateException optionsFailure = new IllegalStateException("options close failed");
    final FailingOptions options = new FailingOptions(optionsFailure);
    final OptionsOwner owner = new OptionsOwner(model(dir, TINY_VECTORS), vocab(dir), options);
    owner.session().close();

    final IllegalStateException thrown = assertThrows(IllegalStateException.class, owner::close);

    assertEquals("Trying to close an already closed OrtSession.", thrown.getMessage());
    assertEquals(1, thrown.getSuppressed().length);
    assertSame(optionsFailure, thrown.getSuppressed()[0]);
    assertEquals(1, options.closes());
    assertOptionsClosed(options);

    assertDoesNotThrow(owner::close);
    assertEquals(1, options.closes());
  }

  /** The test constructor owns neither a session nor options, so closing it does nothing. */
  @Test
  void testCloseWithoutSessionOrOptionsIsIdempotent() {
    final ModelFreeDL model = new ModelFreeDL(TOKENIZER_VOCAB, true);

    assertDoesNotThrow(model::close);
    assertDoesNotThrow(model::close);
  }

  private static void assertOptionsClosed(final OrtSession.SessionOptions options) {
    assertThrows(IllegalStateException.class, () -> options.setIntraOpNumThreads(1));
  }

  /**
   * An {@link AbstractDL} whose only job is to expose the session the base class created.
   */
  private static final class OptionsOwner extends AbstractDL {

    OptionsOwner(final File model, final File vocabulary,
        final OrtSession.SessionOptions sessionOptions) throws IOException, OrtException {
      super(model, vocabulary, sessionOptions, false);
    }

    OrtSession session() {
      return session;
    }
  }

  /**
   * Records whether {@link #close()} ran, how many times, and whether the watched session was
   * already closed.
   */
  private static final class TrackingOptions extends OrtSession.SessionOptions {

    private OrtSession session;
    private boolean closed;
    private boolean sessionClosedFirst;
    private int closes;

    void watch(final OrtSession session) {
      this.session = session;
    }

    boolean wasClosed() {
      return closed;
    }

    boolean sessionWasClosedFirst() {
      return sessionClosedFirst;
    }

    int closes() {
      return closes;
    }

    @Override
    public void close() {
      closes++;
      if (session != null) {
        try {
          session.getInputInfo();
          sessionClosedFirst = false;
        } catch (final IllegalStateException e) {
          sessionClosedFirst = true;
        } catch (final OrtException e) {
          throw new IllegalStateException(
              "Could not read the session while closing its options.", e);
        }
      }
      super.close();
      closed = true;
    }
  }

  /**
   * Closes the native options, then fails, so a session close failure can keep this failure
   * as a suppressed exception.
   */
  private static final class FailingOptions extends OrtSession.SessionOptions {

    private final RuntimeException failure;
    private int closes;

    FailingOptions(final RuntimeException failure) {
      this.failure = failure;
    }

    int closes() {
      return closes;
    }

    @Override
    public void close() {
      closes++;
      super.close();
      throw failure;
    }
  }
}
