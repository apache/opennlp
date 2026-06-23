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
package opennlp.tools.util.normalizer;

/**
 * A {@link CharSequenceNormalizer} that reduces text to its Unicode confusable
 * {@linkplain Confusables#skeleton(CharSequence) skeleton} (UTS #39).
 *
 * <p>This maps lookalike characters to a common prototype so that, for example, a word spelled with
 * Cyrillic or Greek letters that imitate Latin ones reduces to the same form as its Latin spelling.
 * The result is a matching key, not readable text: it is lossy and does not preserve offsets, so it
 * is only meaningful as a derived layer of the original/normalized model.</p>
 */
public class ConfusableSkeletonCharSequenceNormalizer implements CharSequenceNormalizer {

  private static final long serialVersionUID = 3074516628190437751L;

  private static final ConfusableSkeletonCharSequenceNormalizer INSTANCE =
      new ConfusableSkeletonCharSequenceNormalizer();

  private ConfusableSkeletonCharSequenceNormalizer() {
  }

  /** {@return the shared, stateless instance} */
  public static ConfusableSkeletonCharSequenceNormalizer getInstance() {
    return INSTANCE;
  }

  @Override
  public CharSequence normalize(CharSequence text) {
    return Confusables.skeleton(text);
  }
}
