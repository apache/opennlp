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
