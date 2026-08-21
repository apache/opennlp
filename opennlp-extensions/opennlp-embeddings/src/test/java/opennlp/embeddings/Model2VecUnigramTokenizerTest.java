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
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import opennlp.tools.util.InvalidFormatException;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Model2VecUnigramTokenizerTest {

  @Test
  void testReportsMissingVocabularyAsInvalidModelContent(@TempDir Path dir) throws IOException {
    final Path tokenizer = dir.resolve("tokenizer.json");
    Files.writeString(tokenizer,
        "{\"normalizer\":{\"type\":\"Precompiled\",\"precompiled_charsmap\":\"\"},"
            + "\"pre_tokenizer\":{\"type\":\"Metaspace\",\"replacement\":\"▁\","
            + "\"prepend_scheme\":\"always\",\"split\":false},"
            + "\"model\":{\"type\":\"Unigram\",\"unk_id\":0}} ");

    final InvalidFormatException error = assertThrows(InvalidFormatException.class,
        () -> Model2VecUnigramTokenizer.load(tokenizer));

    assertTrue(error.getMessage().contains("model.vocab"), error.getMessage());
  }
}
