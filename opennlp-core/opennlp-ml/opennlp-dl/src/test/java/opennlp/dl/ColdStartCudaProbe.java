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

import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;

/**
 * Requests the CUDA execution provider through {@link AbstractDL#sessionOptions(InferenceOptions)}
 * as the first ONNX Runtime call of a fresh JVM, and prints the outcome on one line.
 * {@link AbstractDLColdStartCudaTest} runs it in a child JVM, because whether ONNX Runtime has
 * been touched before is process-wide state that the other tests of the module change.
 */
public final class ColdStartCudaProbe {

  /** Printed when the provider was added. */
  static final String ADDED = "ADDED";

  /** Prefix of the line printed when ONNX Runtime rejected the request. */
  static final String REJECTED = "REJECTED ";

  private ColdStartCudaProbe() {
  }

  public static void main(String[] args) {
    final InferenceOptions options = new InferenceOptions();
    options.setGpu(true);
    options.setGpuDeviceId(0);
    try (OrtSession.SessionOptions sessionOptions = AbstractDL.sessionOptions(options)) {
      System.out.println(ADDED);
    } catch (OrtException e) {
      System.out.println(REJECTED + e.getCode() + ": " + e.getMessage());
    }
  }
}
