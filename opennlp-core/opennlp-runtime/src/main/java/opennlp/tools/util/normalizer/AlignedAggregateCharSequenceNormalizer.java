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
 * An {@link OffsetAwareNormalizer} that applies a chain of offset-aware rungs in order and composes
 * their per-stage {@link Alignment}s with {@link Alignment#andThen(Alignment)}, so the result maps a
 * span found in the fully normalized text back to the original input through every stage.
 *
 * <p>Produced by {@code TextNormalizer.Builder.buildAligned()}, which validates that every rung is
 * offset-aware before constructing this.</p>
 */
final class AlignedAggregateCharSequenceNormalizer implements OffsetAwareNormalizer {

  private static final long serialVersionUID = 3056944120186103477L;

  private final OffsetAwareNormalizer[] steps;

  AlignedAggregateCharSequenceNormalizer(OffsetAwareNormalizer[] steps) {
    this.steps = steps;
  }

  @Override
  public CharSequence normalize(CharSequence text) {
    CharSequence result = text;
    for (final OffsetAwareNormalizer step : steps) {
      result = step.normalize(result);
    }
    return result;
  }

  @Override
  public AlignedText normalizeAligned(CharSequence text) {
    if (steps.length == 0) {
      // Identity pipeline: use one String for both sides so the alignment's lengths cannot diverge
      // from the stored original for a CharSequence whose length() differs from its toString().
      final String identity = text.toString();
      return new AlignedText(identity, identity,
          new Alignment.Builder().equal(identity.length()).build(identity.length()));
    }
    // Normalize the input to a String once so the stored original and the per-stage alignment
    // lengths agree even for a CharSequence whose length() differs from its toString().
    final String input = text.toString();
    AlignedText stage = steps[0].normalizeAligned(input);
    Alignment alignment = stage.alignment();
    for (int i = 1; i < steps.length; i++) {
      final AlignedText next = steps[i].normalizeAligned(stage.normalized());
      alignment = alignment.andThen(next.alignment());
      stage = next;
    }
    return new AlignedText(input, stage.normalized(), alignment);
  }
}
