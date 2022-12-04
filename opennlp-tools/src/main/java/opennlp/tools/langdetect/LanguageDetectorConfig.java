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
package opennlp.tools.langdetect;

public class LanguageDetectorConfig {

  public static final int DEFAULT_MAX_LENGTH = 10000;

  public static final int DEFAULT_CHUNK_SIZE = 200;

  public static final int DEFAULT_MIN_CONSEC_IMPROVEMENTS = 2;

  public static final double DEFAULT_MIN_DIFF = 0.20;

  public static final LanguageDetectorConfig DEFAULT_LANGUAGE_DETECTOR_CONFIG =
            new ImmutableLanguageDetectorConfig();

  private int maxLength = DEFAULT_MAX_LENGTH;
  private int chunkSize = DEFAULT_CHUNK_SIZE;
  private int minConsecImprovements = DEFAULT_MIN_CONSEC_IMPROVEMENTS;
  private double minDiff = DEFAULT_MIN_DIFF;

  /**
   * @return The maximum length in codepoints of text to process.
   */
  public int getMaxLength() {
    return maxLength;
  }

  public void setMaxLength(int maxLength) {
    this.maxLength = maxLength;
  }

  /**
   * After processing a chunk of this size, the probing
   * detection will compute probabilities and determine
   * if there is enough confidence to stop.
   *
   * @return The size in codepoints of chunk to process at each step for
   *         the probing detection.
   */
  public int getChunkSize() {
    return chunkSize;
  }

  public void setChunkSize(int chunkSize) {
    this.chunkSize = chunkSize;
  }

  /**
   * If this value equals {@code 0}, probing detection will
   * rely solely on {@link #getMinDiff()}
   *
   * @return The minimum number of consecutive increased probabilities
   *         for the top language required in probing detection
   *         to stop early.
   */
  public int getMinConsecImprovements() {
    return minConsecImprovements;
  }

  public void setMinConsecImprovements(int minConsecImprovements) {
    this.minConsecImprovements = minConsecImprovements;
  }

  /**
   * If this value equals {@code 0}, probing detection will
   * rely solely on {@link #getMinConsecImprovements()}
   *
   * @return The minimum difference in confidence between the top predicted
   *         language and the next most likely language.
   */
  public double getMinDiff() {
    return minDiff;
  }

  public void setMinDiff(double minDiff) {
    this.minDiff = minDiff;
  }

  private static class ImmutableLanguageDetectorConfig
          extends LanguageDetectorConfig {

    @Override
    public void setMaxLength(int maxLength) {
      //no-op
    }

    @Override
    public void setChunkSize(int chunkSize) {
      //no-op
    }

    @Override
    public void setMinConsecImprovements(int minConsecImprovements) {
      //no-op
    }

    @Override
    public void setMinDiff(double minDiff) {
      //no-op
    }

  }
}
