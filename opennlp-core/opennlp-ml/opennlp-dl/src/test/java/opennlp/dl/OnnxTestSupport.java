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
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Objects;

import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtProvider;

/**
 * Test helpers for the ONNX components of this module: test resources as files, and the GPU
 * settings of {@link InferenceOptions}.
 */
public final class OnnxTestSupport {

  /** A CUDA device id that no machine has. */
  public static final int UNUSABLE_DEVICE_ID = 99;

  private OnnxTestSupport() {
  }

  /**
   * Copies a classpath resource into a directory. The resource is copied rather than resolved in
   * place, because inside the {@code opennlp-dl} test-jar, as used by {@code opennlp-dl-gpu}, it
   * is not a file.
   *
   * @param dir The directory to copy into.
   * @param resource The absolute classpath name of the resource.
   * @param name The file name of the copy.
   * @return The copied file.
   * @throws IOException Thrown if the resource cannot be copied.
   */
  public static File copy(final Path dir, final String resource, final String name)
      throws IOException {
    final Path file = dir.resolve(name);
    try (InputStream is = Objects.requireNonNull(
        OnnxTestSupport.class.getResourceAsStream(resource), resource)) {
      Files.copy(is, file, StandardCopyOption.REPLACE_EXISTING);
    }
    return file.toFile();
  }

  /**
   * {@return inference options that request the GPU with the given device id}
   *
   * @param deviceId The CUDA device id.
   */
  public static InferenceOptions gpu(final int deviceId) {
    final InferenceOptions options = new InferenceOptions();
    options.setGpu(true);
    options.setGpuDeviceId(deviceId);
    return options;
  }

  /**
   * {@return whether the ONNX Runtime on the classpath is built with the CUDA execution provider}
   * This says nothing about a usable CUDA device.
   */
  public static boolean hasCudaProvider() {
    return OrtEnvironment.getAvailableProviders().contains(OrtProvider.CUDA);
  }
}
