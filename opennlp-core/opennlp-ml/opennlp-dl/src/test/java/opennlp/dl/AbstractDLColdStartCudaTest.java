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
 * Requesting the CUDA execution provider must work as the first ONNX Runtime call of a JVM.
 *
 * <p>Loading a shared execution provider library goes through ONNX Runtime's default logger,
 * which only exists once an {@code OrtEnvironment} has been created. When
 * {@link AbstractDL#sessionOptions(InferenceOptions)} called {@code addCUDA} before that, the
 * {@code onnxruntime_gpu} build failed with "Attempt to use DefaultLogger but none has been
 * registered", on any machine, so a cold JVM could never get a GPU session. On a machine
 * without CUDA the request must still fail, but because the CUDA libraries cannot be loaded.</p>
 *
 * <p>The CPU-only {@code onnxruntime} build rejects the request before it gets that far, so
 * this test only discriminates in {@code opennlp-dl-gpu}, which reruns the tests of this module
 * against {@code onnxruntime_gpu}.</p>
 */
class AbstractDLColdStartCudaTest {

  private static final String DEFAULT_LOGGER_FAILURE = "DefaultLogger";

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
