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
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The term segmenter's fidelity to the teacher's own tokenization: WordPiece casing and subword
 * continuation, unknown-word fallback, delimiter removal, and the SentencePiece path through the
 * teacher's trained model file.
 */
class TermSegmenterTest {

  // A WordPiece teacher whose vocabulary can subword-split "corpuses" into corpus + ##es.
  private static final String WORDPIECE_TEACHER =
      "{\"normalizer\":{\"type\":\"BertNormalizer\",\"lowercase\":true},"
          + "\"post_processor\":null,"
          + "\"model\":{\"type\":\"WordPiece\",\"unk_token\":\"[UNK]\","
          + "\"vocab\":{\"[UNK]\":0,\"[CLS]\":1,\"[SEP]\":2,"
          + "\"habeas\":3,\"corpus\":4,\"##es\":5}}}";

  private static TeacherTokenizer wordpieceTeacher(Path dir) throws IOException {
    Files.writeString(dir.resolve(ModelFileNames.TOKENIZER_JSON), WORDPIECE_TEACHER);
    return TeacherTokenizer.read(dir.resolve(ModelFileNames.TOKENIZER_JSON), null);
  }

  @Test
  void testSegmentsWithTheTeachersCasingAndSubwords(@TempDir Path dir) throws IOException {
    final TermSegmenter segmenter =
        TermSegmenter.forTeacher(wordpieceTeacher(dir), dir);

    assertEquals(List.of("habeas", "corpus"), segmenter.pieces("Habeas CORPUS"));
    assertEquals(List.of("corpus", "##es"), segmenter.pieces("corpuses"));
  }

  @Test
  void testDropsTheEncodersDelimitersButKeepsTheUnknownPiece(@TempDir Path dir)
      throws IOException {
    final TermSegmenter segmenter =
        TermSegmenter.forTeacher(wordpieceTeacher(dir), dir);

    // The wrapping [CLS]/[SEP] are the segmenter's own; an out-of-vocabulary word stays as the
    // unknown piece, so the teacher still sees a position for it.
    assertEquals(List.of("habeas", "[UNK]"), segmenter.pieces("habeas zzz"));
  }

  @Test
  void testWordpiecePiecesMapBackToTeacherInputIds(@TempDir Path dir) throws IOException {
    final TeacherTokenizer teacher = wordpieceTeacher(dir);
    final TermSegmenter segmenter = TermSegmenter.forTeacher(teacher, dir);

    // No post-processor, so the sequence is exactly the piece ids in the teacher's id space.
    final long[] sequence = teacher.inputSequence(segmenter.pieces("habeas corpuses"));
    assertArrayEquals(new long[] {3, 4, 5}, sequence);
  }

  @Test
  void testAUnigramTeacherSegmentsThroughItsTrainedModelFile(@TempDir Path dir)
      throws IOException {
    // The trained tiny SentencePiece fixture next to a matching Unigram tokenizer.json.
    final byte[] modelBytes;
    try (InputStream in = TermSegmenterTest.class
        .getResourceAsStream(EmbeddingTestFixtures.TINY_UNIGRAM_RESOURCE)) {
      modelBytes = in.readAllBytes();
    }
    Files.write(dir.resolve("spiece.model"), modelBytes);
    Files.writeString(dir.resolve(ModelFileNames.TOKENIZER_JSON),
        "{\"post_processor\":null,"
            + "\"model\":{\"type\":\"Unigram\",\"unk_id\":0,"
            + "\"vocab\":[[\"<unk>\",0.0],[\"▁a\",-1.5],[\"a\",-2.0]]}}");
    final TeacherTokenizer teacher = TeacherTokenizer.read(
        dir.resolve(ModelFileNames.TOKENIZER_JSON), null);

    final TermSegmenter segmenter = TermSegmenter.forTeacher(teacher, dir);
    final List<String> pieces = segmenter.pieces("a");

    assertFalse(pieces.isEmpty());
    // Control pieces never appear; every piece is a string the trained model produced.
    assertTrue(pieces.stream().noneMatch(p -> p.equals("<s>") || p.equals("</s>")),
        pieces.toString());
  }

  @Test
  void testAUnigramTeacherWithoutItsModelFileIsRejected(@TempDir Path dir) throws IOException {
    Files.writeString(dir.resolve(ModelFileNames.TOKENIZER_JSON),
        "{\"post_processor\":null,"
            + "\"model\":{\"type\":\"Unigram\",\"unk_id\":0,\"vocab\":[[\"<unk>\",0.0]]}}");
    final TeacherTokenizer teacher = TeacherTokenizer.read(
        dir.resolve(ModelFileNames.TOKENIZER_JSON), null);

    final IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
        () -> TermSegmenter.forTeacher(teacher, dir));
    assertTrue(e.getMessage().contains("SentencePiece"), e.getMessage());
  }

  @Test
  void testNullArgumentsAreRejected(@TempDir Path dir) throws IOException {
    final TeacherTokenizer teacher = wordpieceTeacher(dir);
    assertThrows(IllegalArgumentException.class, () -> TermSegmenter.forTeacher(null, dir));
    assertThrows(IllegalArgumentException.class, () -> TermSegmenter.forTeacher(teacher, null));
    final TermSegmenter segmenter = TermSegmenter.forTeacher(teacher, dir);
    assertThrows(IllegalArgumentException.class, () -> segmenter.pieces(null));
  }
}
