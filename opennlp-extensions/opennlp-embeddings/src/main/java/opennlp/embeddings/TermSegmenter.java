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
package opennlp.embeddings;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.IntPredicate;

import opennlp.subword.sentencepiece.SentencePieceTokenizer;
import opennlp.tools.tokenize.SubwordPiece;
import opennlp.tools.tokenize.SubwordTokenizer;
import opennlp.tools.tokenize.WordpieceEncoder;
import opennlp.tools.tokenize.WordpieceTokenizer;

/**
 * Segments a term's text into the piece strings the teacher's own tokenizer would produce, so a
 * distillation can run a whole word or phrase through the teacher the way the teacher would see
 * it in running text. A WordPiece teacher segments through a {@link WordpieceEncoder} built over
 * the teacher's full vocabulary; a Unigram teacher segments through its trained SentencePiece
 * {@code .model} file.
 *
 * <p>The sequence-delimiter pieces the segmenter itself wraps around an encoding are removed;
 * {@link TeacherTokenizer#inputSequence(List)} adds the teacher's own wrapping when the pieces
 * are turned into an input sequence.</p>
 */
final class TermSegmenter {

  private final SubwordTokenizer tokenizer;
  private final Set<String> dropPieces;
  private final IntPredicate dropPieceId;

  /** Holds the segmenter and its piece filters; built by {@link #forTeacher}. */
  private TermSegmenter(SubwordTokenizer tokenizer, Set<String> dropPieces,
                        IntPredicate dropPieceId) {
    this.tokenizer = tokenizer;
    this.dropPieces = dropPieces;
    this.dropPieceId = dropPieceId;
  }

  /**
   * Builds the segmenter matching a teacher's tokenizer family.
   *
   * @param teacher          The teacher's parsed tokenizer. Must not be {@code null}.
   * @param teacherDirectory The teacher's directory, holding the trained SentencePiece
   *                         {@code .model} file when the teacher is a Unigram model. Must not be
   *                         {@code null}.
   * @return The segmenter.
   * @throws IllegalArgumentException Thrown if an argument is {@code null}, a Unigram teacher
   *     has no trained SentencePiece file, or a WordPiece teacher's vocabulary lacks the BERT
   *     special tokens the encoder wraps with.
   * @throws IOException Thrown if reading the SentencePiece file fails.
   */
  static TermSegmenter forTeacher(TeacherTokenizer teacher, Path teacherDirectory)
      throws IOException {
    if (teacher == null) {
      throw new IllegalArgumentException("Teacher must not be null");
    }
    if (teacherDirectory == null) {
      throw new IllegalArgumentException("TeacherDirectory must not be null");
    }
    if (TeacherTokenizer.WORDPIECE.equals(teacher.modelType())) {
      // The lowercase default matches ModelAssembler's: absent means the uncased convention.
      final boolean lowerCase = teacher.lowerCase() == null || teacher.lowerCase();
      final WordpieceEncoder encoder;
      try {
        encoder = new WordpieceEncoder(teacher.tokensByOriginalId(), lowerCase,
            WordpieceTokenizer.BERT_CLS_TOKEN, WordpieceTokenizer.BERT_SEP_TOKEN,
            teacher.unkToken());
      } catch (IllegalArgumentException e) {
        throw new IllegalArgumentException("The teacher's WordPiece vocabulary cannot segment "
            + "terms: " + e.getMessage(), e);
      }
      return new TermSegmenter(encoder,
          Set.of(WordpieceTokenizer.BERT_CLS_TOKEN, WordpieceTokenizer.BERT_SEP_TOKEN),
          id -> false);
    }
    final Path sentencePieceModelFile = ModelFileNames.firstRegularFile(teacherDirectory,
        ModelFileNames.SENTENCEPIECE_MODELS);
    if (sentencePieceModelFile == null) {
      throw new IllegalArgumentException("Teacher directory " + teacherDirectory + " has no "
          + "trained SentencePiece file (one of "
          + String.join(", ", ModelFileNames.SENTENCEPIECE_MODELS) + "); distilling terms "
          + "needs the teacher's own segmentation");
    }
    final SentencePieceTokenizer sentencePiece =
        SentencePieceTokenizer.load(sentencePieceModelFile);
    return new TermSegmenter(sentencePiece, Set.of(),
        id -> id >= 0 && sentencePiece.isControl(id));
  }

  /**
   * Segments a term into the teacher's piece strings, without sequence delimiters.
   *
   * @param term The term text. Must not be {@code null}.
   * @return The piece strings in order.
   * @throws IllegalArgumentException Thrown if {@code term} is {@code null}.
   */
  List<String> pieces(String term) {
    if (term == null) {
      throw new IllegalArgumentException("Term must not be null");
    }
    final List<SubwordPiece> encoded = tokenizer.encode(term);
    final List<String> pieces = new ArrayList<>(encoded.size());
    for (final SubwordPiece piece : encoded) {
      if (dropPieces.contains(piece.piece()) || dropPieceId.test(piece.id())) {
        continue;
      }
      pieces.add(piece.piece());
    }
    return pieces;
  }
}
