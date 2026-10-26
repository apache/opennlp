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

public class InferenceOptions {

  private boolean includeAttentionMask = true;
  private boolean includeTokenTypeIds = true;
  private boolean gpu;
  private int gpuDeviceId = 0;
  private int intraOpNumThreads = 0;
  private int maxConcurrentInferences = 0;
  private int documentSplitSize = 250;
  private int splitOverlapSize = 50;
  private Boolean lowerCase;
  private boolean normalizeWhitespace;
  private boolean normalizeDashes;

  public boolean isIncludeAttentionMask() {
    return includeAttentionMask;
  }

  public void setIncludeAttentionMask(boolean includeAttentionMask) {
    this.includeAttentionMask = includeAttentionMask;
  }

  public boolean isIncludeTokenTypeIds() {
    return includeTokenTypeIds;
  }

  public void setIncludeTokenTypeIds(boolean includeTokenTypeIds) {
    this.includeTokenTypeIds = includeTokenTypeIds;
  }

  public boolean isGpu() {
    return gpu;
  }

  public void setGpu(boolean gpu) {
    this.gpu = gpu;
  }

  public int getGpuDeviceId() {
    return gpuDeviceId;
  }

  public void setGpuDeviceId(int gpuDeviceId) {
    this.gpuDeviceId = gpuDeviceId;
  }

  /**
   * {@return the number of threads ONNX Runtime may use to parallelize the execution within
   * operators} The default of {@code 0} leaves the choice to ONNX Runtime, which uses one thread
   * per physical core.
   */
  public int getIntraOpNumThreads() {
    return intraOpNumThreads;
  }

  /**
   * Sets the number of threads ONNX Runtime may use to parallelize the execution within
   * operators. Fix the count to make CPU inference times comparable across machines, which
   * otherwise differ in their core counts. The default of {@code 0} leaves the choice to ONNX
   * Runtime.
   *
   * @param intraOpNumThreads The number of intra-op threads, or {@code 0} for the ONNX Runtime
   *     default. Must not be negative.
   */
  public void setIntraOpNumThreads(int intraOpNumThreads) {
    this.intraOpNumThreads = intraOpNumThreads;
  }

  /**
   * {@return the number of inferences a {@link opennlp.dl.vectors.SentenceVectorsDL} instance
   * runs at once} The default of {@code 0} stands for the number of available processors.
   */
  public int getMaxConcurrentInferences() {
    return maxConcurrentInferences;
  }

  /**
   * Sets the number of inferences a {@link opennlp.dl.vectors.SentenceVectorsDL} instance runs at
   * once. Each running inference holds one direct buffer of the instance for its tensors, so this
   * bounds the direct memory of the instance; a caller beyond the limit waits for a running
   * inference to finish. A buffer grows to the largest inference it has held: about 67 MB for a
   * token output at 16384 token positions with a hidden dimension of 1024. Other components
   * ignore this setting.
   *
   * @param maxConcurrentInferences The number of concurrent inferences, or {@code 0} for the
   *     number of available processors. Must not be negative.
   */
  public void setMaxConcurrentInferences(int maxConcurrentInferences) {
    this.maxConcurrentInferences = maxConcurrentInferences;
  }

  public int getDocumentSplitSize() {
    return documentSplitSize;
  }

  public void setDocumentSplitSize(int documentSplitSize) {
    this.documentSplitSize = documentSplitSize;
  }

  public int getSplitOverlapSize() {
    return splitOverlapSize;
  }

  public void setSplitOverlapSize(int splitOverlapSize) {
    this.splitOverlapSize = splitOverlapSize;
  }

  /** {@return whether input whitespace is normalized to ASCII spaces before inference} */
  public boolean isNormalizeWhitespace() {
    return normalizeWhitespace;
  }

  /**
   * Replaces every Unicode whitespace character in the input with an ASCII space before inference.
   * This is offset preserving (each whitespace code point maps to one space), so any spans a model
   * produces still align with the input. Off by default.
   *
   * <p>This is a one-for-one replacement, not the collapse-and-trim whitespace fold of the runtime
   * {@code TextNormalizer.whitespace()} normalizer: runs of whitespace are not merged and leading or
   * trailing whitespace is not removed, so offsets are preserved.</p>
   *
   * @param normalizeWhitespace Whether to normalize whitespace.
   */
  public void setNormalizeWhitespace(boolean normalizeWhitespace) {
    this.normalizeWhitespace = normalizeWhitespace;
  }

  /** {@return whether input dashes are normalized to the ASCII hyphen before inference} */
  public boolean isNormalizeDashes() {
    return normalizeDashes;
  }

  /**
   * Replaces Unicode dashes in the input with the ASCII hyphen-minus before inference. This is
   * offset preserving for the dash characters in the Basic Multilingual Plane (the common case).
   * The mathematical minus signs are not affected. Off by default.
   *
   * <p>A supplementary-plane dash shrinks from two chars to one, which shifts later offsets, so
   * with this enabled {@code find(...)} reports offsets into the normalized text in that case. Use
   * {@code NameFinderDL.findInOriginal(...)} for offsets mapped back to the original input.</p>
   *
   * @param normalizeDashes Whether to normalize dashes.
   */
  public void setNormalizeDashes(boolean normalizeDashes) {
    this.normalizeDashes = normalizeDashes;
  }

  /**
   * Returns whether tokenization should lower case the input text and strip
   * accents, as required by uncased models.
   *
   * @return {@code Boolean.TRUE} for uncased models, {@code Boolean.FALSE} for
   *     cased models, or {@code null} if not set, in which case each component
   *     applies the default that matches its commonly used models.
   */
  public Boolean getLowerCase() {
    return lowerCase;
  }

  /**
   * Sets whether tokenization should lower case the input text and strip
   * accents. Set {@code true} for uncased models and {@code false} for cased
   * models. If not set, each component applies the default that matches its
   * commonly used models.
   *
   * @param lowerCase Whether to lower case the input text during tokenization.
   */
  public void setLowerCase(boolean lowerCase) {
    this.lowerCase = lowerCase;
  }

}
