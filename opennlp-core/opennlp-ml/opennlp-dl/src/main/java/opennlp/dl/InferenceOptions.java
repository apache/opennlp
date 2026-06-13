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
