/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
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

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AbstractDLNativeLibraryErrorTest {

  @Test
  void reportsPlatformAndCpuAlternativeWhenNativeLibraryCannotLoad() {
    final UnsatisfiedLinkError original = new UnsatisfiedLinkError("synthetic loader failure");

    final UnsatisfiedLinkError wrapped = assertThrows(UnsatisfiedLinkError.class,
        () -> AbstractDL.initializeNative(() -> {
          throw original;
        }, "macOS", "aarch64"));

    assertEquals("Could not load ONNX Runtime native library for macOS/aarch64"
        + ". opennlp-dl-gpu bundles native libraries only for linux-x64 and win-x64; "
        + "opennlp-dl provides CPU inference for linux-x64, linux-aarch64, osx-aarch64, "
        + "and win-x64.", wrapped.getMessage());
    assertSame(original, wrapped.getCause());
  }

  @Test
  void preservesGuidanceAfterOnnxClassInitializationFails() {
    final NoClassDefFoundError original = new NoClassDefFoundError(
        "Could not initialize class ai.onnxruntime.OrtEnvironment");
    final NoClassDefFoundError wrapped = assertThrows(NoClassDefFoundError.class,
        () -> AbstractDL.initializeNative(() -> { throw original; }, "linux", "x86_64"));
    assertTrue(wrapped.getMessage().contains("linux/x86_64"));
    assertSame(original, wrapped.getCause());
  }

  @Test
  void doesNotMaskUnrelatedMissingClasses() {
    final NoClassDefFoundError original = new NoClassDefFoundError("other/Dependency");
    assertSame(original, assertThrows(NoClassDefFoundError.class,
        () -> AbstractDL.initializeNative(() -> { throw original; }, "linux", "x86_64")));
  }
}
