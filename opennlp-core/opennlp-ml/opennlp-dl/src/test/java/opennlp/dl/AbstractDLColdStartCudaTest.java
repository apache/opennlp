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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static opennlp.dl.OnnxTestSupport.gpu;
import static opennlp.dl.OnnxTestSupport.hasCudaProvider;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Tests that {@link AbstractDL#sessionOptions(InferenceOptions)} can request the CUDA execution
 * provider as the first ONNX Runtime call of a JVM. Without a usable CUDA installation the
 * request may be rejected, but never because ONNX Runtime's default logger is missing.
 *
 * <p>Whether ONNX Runtime has been used is process-wide state that the other tests of the module
 * change, so the request runs in a child JVM through {@link FirstCudaRequest}. The test runs
 * where ONNX Runtime has the CUDA provider, which is {@code opennlp-dl-gpu}; a CUDA device is not
 * needed.</p>
 */
class AbstractDLColdStartCudaTest {

  private static final String ADDED = "ADDED";

  private static final String REJECTED = "REJECTED ";

  private static final String DEFAULT_LOGGER_FAILURE = "DefaultLogger";

  private static final long TIMEOUT_MINUTES = 2;

  /**
   * Runs {@link FirstCudaRequest} in a child JVM and checks that its outcome line reports the
   * provider as added, or a rejection that is not about the default logger.
   */
  @Test
  void testFirstCudaRequestOfAJvm(@TempDir final Path dir)
      throws IOException, InterruptedException {
    assumeTrue(hasCudaProvider(), "the ONNX Runtime on the classpath has no CUDA execution provider");
    final Path out = dir.resolve("stdout.log");
    final Path err = dir.resolve("stderr.log");
    final Path java = Path.of(System.getProperty("java.home"), "bin", "java");
    final Process process = new ProcessBuilder(List.of(java.toString(), "-cp",
        System.getProperty("java.class.path"), FirstCudaRequest.class.getName()))
        .redirectOutput(out.toFile())
        .redirectError(err.toFile())
        .start();
    if (!process.waitFor(TIMEOUT_MINUTES, TimeUnit.MINUTES)) {
      process.destroyForcibly();
      fail("the child JVM did not finish within " + TIMEOUT_MINUTES + " minutes");
    }
    // The outcome line is read from stdout only: ONNX Runtime logs to stderr, on Windows in UTF-16.
    final String stdout = read(out);
    final String report = "stdout:\n" + stdout + "\nstderr:\n" + read(err);
    assertEquals(0, process.exitValue(), report);
    final String outcome = outcomeLine(stdout, report);
    assertTrue(outcome.equals(ADDED) || !outcome.contains(DEFAULT_LOGGER_FAILURE), report);
  }

  /**
   * Reads a file of child JVM output, replacing bytes that are not UTF-8.
   *
   * @param file The file to read.
   * @return The content of the file.
   * @throws IOException Thrown if the file cannot be read.
   */
  private static String read(final Path file) throws IOException {
    return new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
  }

  /**
   * Finds the outcome line that {@link FirstCudaRequest} printed.
   *
   * @param stdout The standard output of the child JVM.
   * @param report The complete child JVM output, for the failure message.
   * @return The line that is {@link #ADDED} or starts with {@link #REJECTED}.
   */
  private static String outcomeLine(final String stdout, final String report) {
    return stdout.lines()
        .filter(line -> line.equals(ADDED) || line.startsWith(REJECTED))
        .findFirst()
        .orElseGet(() -> fail("no outcome line in the child JVM output:\n" + report));
  }

  /**
   * The child JVM entry point: requests the CUDA execution provider for device {@code 0} as the
   * first ONNX Runtime call, then prints {@link #ADDED} or {@link #REJECTED} followed by the
   * ONNX Runtime error on one line.
   */
  public static final class FirstCudaRequest {

    private FirstCudaRequest() {
    }

    /**
     * Entry point of the child JVM that the cold start test starts, because the CUDA request has
     * to be the first ONNX Runtime call of a fresh JVM.
     *
     * @param args Ignored; the test starts the child JVM without arguments.
     */
    public static void main(final String[] args) {
      try {
        final OrtSession.SessionOptions sessionOptions = AbstractDL.sessionOptions(gpu(0));
        sessionOptions.close();
        System.out.println(ADDED);
      } catch (OrtException e) {
        System.out.println(REJECTED + e.getCode() + ": "
            + e.getMessage().replace('\n', ' ').replace('\r', ' '));
      }
    }
  }
}
