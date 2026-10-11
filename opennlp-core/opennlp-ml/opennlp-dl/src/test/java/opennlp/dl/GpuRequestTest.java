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
import java.nio.file.Path;
import java.util.Map;

import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import opennlp.dl.doccat.DocumentCategorizerDL;
import opennlp.dl.doccat.scoring.AverageClassificationScoringStrategy;
import opennlp.dl.namefinder.NameFinderDL;
import opennlp.tools.sentdetect.NewlineSentenceDetector;

import static opennlp.dl.OnnxTestSupport.UNUSABLE_DEVICE_ID;
import static opennlp.dl.OnnxTestSupport.copy;
import static opennlp.dl.OnnxTestSupport.gpu;
import static opennlp.dl.vectors.TinyVectorModels.TINY_VECTORS;
import static opennlp.dl.vectors.TinyVectorModels.model;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Tests how a GPU request in {@link InferenceOptions} reaches ONNX Runtime: a request for a
 * device that is not usable is rejected with an {@link OrtException}, never served by the CPU,
 * and a CPU request does not ask for any device.
 *
 * <p>In {@code opennlp-dl}, whose ONNX Runtime has no CUDA support, every GPU request is
 * rejected, so the tests there check that a request never falls back to the CPU. The rejection
 * of the device id itself is checked when {@code opennlp-dl-gpu} runs these tests.</p>
 */
class GpuRequestTest {

  private static final String PLAIN_VOCAB = "/opennlp/dl/vocab-plain.txt";
  private static final String VOCAB_FILE = "vocab.txt";

  /** The device id is not read while {@code gpu} is off, so no provider is requested. */
  @ParameterizedTest
  @ValueSource(ints = {-1, 0, UNUSABLE_DEVICE_ID})
  void testSessionOptionsWithoutGpuIgnoreTheDeviceId(final int deviceId) {
    final InferenceOptions options = new InferenceOptions();
    options.setGpuDeviceId(deviceId);
    assertDoesNotThrow(() -> {
      try (OrtSession.SessionOptions sessionOptions = AbstractDL.sessionOptions(options)) {
        assertNotNull(sessionOptions);
      }
    });
  }

  /** A name finder on a device id that no machine has fails instead of running on the CPU. */
  @Test
  void testNameFinderRejectsAnUnusableDevice(@TempDir final Path dir) throws IOException {
    final File model = model(dir, TINY_VECTORS);
    final File vocab = copy(dir, PLAIN_VOCAB, VOCAB_FILE);
    final InferenceOptions options = gpu(UNUSABLE_DEVICE_ID);
    final Map<Integer, String> labels = Map.of(0, "O", 1, "B-PER");
    assertThrows(OrtException.class, () -> new NameFinderDL(model, vocab, labels, options,
        new NewlineSentenceDetector()).close());
  }

  /** A categorizer on a device id that no machine has fails instead of running on the CPU. */
  @Test
  void testDocumentCategorizerRejectsAnUnusableDevice(@TempDir final Path dir)
      throws IOException {
    final File model = model(dir, TINY_VECTORS);
    final File vocab = copy(dir, PLAIN_VOCAB, VOCAB_FILE);
    final InferenceOptions options = gpu(UNUSABLE_DEVICE_ID);
    final Map<Integer, String> categories = Map.of(0, "a", 1, "b");
    assertThrows(OrtException.class, () -> new DocumentCategorizerDL(model, vocab, categories,
        new AverageClassificationScoringStrategy(), options).close());
  }

  /** Null options are rejected before a device is requested. */
  @Test
  void testSessionOptionsRejectNullOptions() {
    assertThrows(IllegalArgumentException.class, () -> AbstractDL.sessionOptions(null));
  }

  /**
   * Invalid split sizes are rejected with an {@link IllegalArgumentException} before a device is
   * requested, so the unusable device id never reaches ONNX Runtime.
   */
  @ParameterizedTest
  @CsvSource({"0,0", "-1,0", "10,-1", "10,10", "10,11"})
  void testSessionOptionsRejectInvalidSplitBeforeAnyDevice(final int splitSize,
      final int overlap) {
    final InferenceOptions options = gpu(UNUSABLE_DEVICE_ID);
    options.setDocumentSplitSize(splitSize);
    options.setSplitOverlapSize(overlap);
    assertThrows(IllegalArgumentException.class, () -> AbstractDL.sessionOptions(options));
  }
}
