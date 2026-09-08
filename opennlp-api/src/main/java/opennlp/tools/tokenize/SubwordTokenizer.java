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
package opennlp.tools.tokenize;

import java.util.List;

/**
 * Splits text into subword units from a fixed model vocabulary, reporting the model id and
 * original-text span for each unit.
 *
 * <p>Segmentation follows model entries, not linguistic token boundaries. Each piece is in the
 * model's normalized form and need not equal the input. Offsets in each {@link SubwordPiece}
 * refer to the original input text.</p>
 *
 * <p>Subword units were introduced to neural models to keep the vocabulary closed while still
 * covering rare and unseen words: byte pair encoding (Sennrich et al., 2016), the WordPiece
 * inventory (Schuster and Nakajima, 2012; Wu et al., 2016) and the unigram language model
 * (Kudo, 2018). This interface abstracts over the segmentation method and describes only what
 * the trained inventories have in common, following the tokenizer-independent encoding contract
 * of SentencePiece (Kudo and Richardson, 2018): every unit is a vocabulary entry with an id and
 * a range over the input.</p>
 *
 * <p>An implementation may include model control pieces with empty source spans. Their presence
 * and placement are part of that tokenizer's contract, not this interface.</p>
 *
 * <p>Thread safety is implementation specific.</p>
 *
 * @see SubwordPiece
 * @see <a href="https://aclanthology.org/P16-1162/">Rico Sennrich, Barry Haddow, Alexandra Birch
 *     (2016): Neural Machine Translation of Rare Words with Subword Units. ACL 2016,
 *     pages 1715-1725</a>
 * @see <a href="https://aclanthology.org/P18-1007/">Taku Kudo (2018): Subword Regularization:
 *     Improving Neural Network Translation Models with Multiple Subword Candidates. ACL 2018,
 *     pages 66-75</a>
 * @see <a href="https://aclanthology.org/D18-2012/">Taku Kudo, John Richardson (2018):
 *     SentencePiece: A simple and language independent subword tokenizer and detokenizer for
 *     Neural Text Processing. EMNLP 2018 (system demonstrations), pages 66-71</a>
 * @since 3.0.0
 */
public interface SubwordTokenizer {

  /**
   * Encodes text into subword pieces.
   *
   * @param text The text to encode; must not be {@code null}.
   * @return The pieces in model order; may be empty.
   * @throws IllegalArgumentException Thrown if {@code text} is {@code null}.
   */
  List<SubwordPiece> encode(CharSequence text);

  /**
   * Encodes text into vocabulary ids.
   *
   * @param text The text to encode; must not be {@code null}.
   * @return The ids from {@link #encode(CharSequence)}, in the same order.
   * @throws IllegalArgumentException Thrown if {@code text} is {@code null}.
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
   * @param text The text to encode; must not be {@code null}.
   * @return The piece strings from {@link #encode(CharSequence)}, in the same order.
   * @throws IllegalArgumentException Thrown if {@code text} is {@code null}.
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
