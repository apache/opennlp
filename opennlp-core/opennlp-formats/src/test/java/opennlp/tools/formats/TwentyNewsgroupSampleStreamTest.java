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
package opennlp.tools.formats;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import opennlp.tools.doccat.DocumentSample;
import opennlp.tools.tokenize.WhitespaceTokenizer;

public class TwentyNewsgroupSampleStreamTest {

  @Test
  void testReadsDocumentsAsUtf8(@TempDir Path dataDir) throws IOException {
    Path category = Files.createDirectory(dataDir.resolve("rec.food"));
    Files.writeString(category.resolve("1"), "Ñandú in München", StandardCharsets.UTF_8);

    try (TwentyNewsgroupSampleStream stream =
             new TwentyNewsgroupSampleStream(WhitespaceTokenizer.INSTANCE, dataDir)) {
      DocumentSample sample = stream.read();
      Assertions.assertEquals("rec.food", sample.getCategory());
      Assertions.assertArrayEquals(new String[] {"Ñandú", "in", "München"}, sample.getText());
    }
  }
}
