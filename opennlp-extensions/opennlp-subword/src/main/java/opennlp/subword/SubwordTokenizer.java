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
package opennlp.subword;

import java.util.List;

/**
 * Splits text into subword units against a fixed vocabulary, reporting for every unit its
 * vocabulary id and the exact span of the original text it covers.
 *
 * <p>Subword tokenization is the input layer of modern sequence models: text is decomposed into
 * pieces from a trained vocabulary so that any input, including words never seen in training, maps
 * to a bounded id space. Unlike a linguistic {@code Tokenizer}, the segmentation is
 * vocabulary-driven, and the pieces are in the model's normalized form rather than substrings of
 * the input. The offsets carried by each {@link SubwordPiece} are what tie the two worlds
 * together: they always refer to the caller's original text.</p>
 *
 * <p>Implementations are expected to be safe for concurrent use by multiple threads; any
 * implementation that is not must document it.</p>
 */
public interface SubwordTokenizer {

  /**
   * Encodes text into subword pieces.
   *
   * @param text The text to encode; must not be null.
   * @return The pieces in text order; empty when the text contains nothing encodable.
   * @throws IllegalArgumentException Thrown if {@code text} is null.
   */
  List<SubwordPiece> encode(CharSequence text);

  /**
   * Encodes text into vocabulary ids.
   *
   * @param text The text to encode; must not be null.
   * @return The ids in text order; empty when the text contains nothing encodable.
   * @throws IllegalArgumentException Thrown if {@code text} is null.
   */
  default int[] encodeToIds(CharSequence text) {
    final List<SubwordPiece> pieces = encode(text);
    final int[] ids = new int[pieces.size()];
    for (int i = 0; i < ids.length; i++) {
      ids[i] = pieces.get(i).id();
    }
    return ids;
  }

  /**
   * Encodes text into piece strings in the vocabulary's normalized form.
   *
   * @param text The text to encode; must not be null.
   * @return The pieces in text order; empty when the text contains nothing encodable.
   * @throws IllegalArgumentException Thrown if {@code text} is null.
   */
  default String[] encodeToPieces(CharSequence text) {
    final List<SubwordPiece> pieces = encode(text);
    final String[] out = new String[pieces.size()];
    for (int i = 0; i < out.length; i++) {
      out[i] = pieces.get(i).piece();
    }
    return out;
  }
}
