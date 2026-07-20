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

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the cookbook path documented in {@code normalizer.xml}: segment with
 * {@link TermAnalyzer}, annotate each term with {@link EmojiAnnotator}, and read the
 * typed fields of the resulting {@link EmojiAnnotation}.
 */
public class EmojiAnnotatorUsageExampleTest {

  private static String cp(int... codePoints) {
    final StringBuilder sb = new StringBuilder();
    for (final int codePoint : codePoints) {
      sb.appendCodePoint(codePoint);
    }
    return sb.toString();
  }

  @Test
  void testAnnotatesFlagAndHeartFromDocumentedExample() {
    final EmojiAnnotator annotator = new EmojiAnnotator();
    final TermAnalyzer analyzer = TermAnalyzer.builder().emojiFold().build();

    // "Berlin <German flag> <red heart>"
    final String text = "Berlin " + cp(0x1F1E9, 0x1F1EA) + " " + cp(0x2764, 0xFE0F);
    final List<String> regions = new ArrayList<>();
    final List<String> names = new ArrayList<>();
    for (final Term term : analyzer.analyze(text)) {
      annotator.annotate(term).ifPresent(a -> {
        a.isoRegion().ifPresent(regions::add);
        a.name().ifPresent(names::add);
        assertTrue(a.entityType().isPresent());
        assertTrue(a.category().isPresent());
      });
    }

    assertEquals(List.of("DE"), regions);
    assertEquals(List.of("red heart"), names);
    assertEquals(Optional.empty(), annotator.annotate("Berlin"));
  }
}
