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

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests that {@link AbstractDL#sessionOptions(InferenceOptions)} can request the CUDA execution
 * provider as the first ONNX Runtime call of a JVM. Without CUDA the request may fail, but not
 * because ONNX Runtime's default logger is missing.
 *
 * <p>The CPU-only {@code onnxruntime} build rejects the request earlier, so the test is
 * meaningful in {@code opennlp-dl-gpu}, which runs the tests of this module against
 * {@code onnxruntime_gpu}.</p>
 */
class AbstractDLColdStartCudaTest {

  private static final String DEFAULT_LOGGER_FAILURE = "DefaultLogger";

  /**
   * Runs {@link ColdStartCudaProbe} in a child JVM and checks that the CUDA request was either
   * added or rejected without a missing default logger.
   */
  @Test
  void testCudaRequestDoesNotNeedAnEarlierOrtEnvironment() throws IOException, InterruptedException {
    final Path java = Path.of(System.getProperty("java.home"), "bin", "java");
    final Process process = new ProcessBuilder(List.of(java.toString(), "-cp",
        System.getProperty("java.class.path"), ColdStartCudaProbe.class.getName()))
        .redirectErrorStream(true)
        .start();
    final String output;
    try (var in = process.getInputStream()) {
      output = new String(in.readAllBytes(), StandardCharsets.UTF_8);
    }
    assertTrue(process.waitFor(2, TimeUnit.MINUTES), "the probe did not finish");
    assertEquals(0, process.exitValue(), output);
    assertTrue(output.contains(ColdStartCudaProbe.ADDED)
        || output.contains(ColdStartCudaProbe.REJECTED), output);
    assertFalse(output.contains(DEFAULT_LOGGER_FAILURE), output);
  }
}
