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

package opennlp.tools.sentiment;

import java.util.List;

import org.junit.jupiter.api.Test;

import static opennlp.tools.util.normalizer.NormalizerTestUtil.cp;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class EmojiSentimentContextGeneratorTest {

  @Test
  void emojiFeaturesAreAppendedToTheTokenContext() {
    final String[] tokens = {"so", "sad", cp(0x1F62D)};
    final String[] context = new EmojiSentimentContextGenerator().getContext(tokens);
    final List<String> features = List.of(context);
    // The default token context is preserved in full...
    assertEquals(List.of(tokens), features.subList(0, tokens.length));
    // ...and the pictograph additionally contributes its typed evidence.
    assertTrue(features.contains("emojiSentiment=-2"), "Missing feature in: " + features);
    assertTrue(features.contains("emojiType=FACE"), "Missing feature in: " + features);
    assertTrue(features.contains("emojiCategory=SMILEYS_AND_EMOTION"),
        "Missing feature in: " + features);
  }

  @Test
  void plainTextContextEqualsTheDefaultGenerators() {
    // Without emoji the opt-in generator produces exactly the default context, so opting in
    // cannot disturb what a model learns from ordinary text.
    final String[] tokens = {"no", "emoji", "here", "", cp(0x1F1E9)};
    assertArrayEquals(new SentimentContextGenerator().getContext(tokens),
        new EmojiSentimentContextGenerator().getContext(tokens));
  }

  @Test
  void theFactorySeamCreatesTheOptInGenerator() {
    // Opt-in path: EmojiSentimentFactory is passed to SentimentME.train and recorded in the
    // model manifest, so prediction re-creates the same context generator. The default factory
    // is unchanged.
    assertEquals(EmojiSentimentContextGenerator.class,
        new EmojiSentimentFactory().createContextGenerator().getClass());
    assertEquals(SentimentContextGenerator.class,
        new SentimentFactory().createContextGenerator().getClass());
  }
}
