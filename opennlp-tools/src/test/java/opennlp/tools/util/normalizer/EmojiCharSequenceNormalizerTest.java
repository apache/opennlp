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

import org.junit.Assert;
import org.junit.Test;


public class EmojiCharSequenceNormalizerTest {

  public EmojiCharSequenceNormalizer normalizer = EmojiCharSequenceNormalizer.getInstance();

  @Test
  public void normalizeEmoji() throws Exception {

    String s = new StringBuilder()
        .append("Any funny text goes here ")
        .appendCodePoint(0x1F606)
        .appendCodePoint(0x1F606)
        .appendCodePoint(0x1F606)
        .append(" ")
        .appendCodePoint(0x1F61B)
        .toString();
    Assert.assertEquals(
        "Any funny text goes here    ", normalizer.normalize(s));
  }

}
