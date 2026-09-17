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

package opennlp.tools.util.jvm;

/**
 * Detects whether the code runs inside a
 * <a href="https://www.graalvm.org/latest/reference-manual/native-image/">GraalVM native image</a>.
 * <p>
 * The image builder sets the system property {@value #IMAGE_CODE_PROPERTY} to
 * {@code buildtime} while it builds the image and to {@code runtime} in the
 * finished executable. Code paths that a native image cannot serve, such as class
 * path scanning or Java serialization without metadata, use this class to report
 * the limitation instead of failing deep inside the JDK.
 */
public final class NativeImage {

  /**
   * The system property the GraalVM image builder and the finished image set.
   */
  public static final String IMAGE_CODE_PROPERTY = "org.graalvm.nativeimage.imagecode";

  private NativeImage() {
  }

  /**
   * @return {@code true} if the code runs in a finished native image, {@code false}
   *         on a JVM and while the image is being built.
   */
  public static boolean inImageRuntime() {
    return "runtime".equals(System.getProperty(IMAGE_CODE_PROPERTY));
  }
}
