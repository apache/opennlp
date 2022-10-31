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

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;


public class TwitterCharSequenceNormalizerTest {

  public TwitterCharSequenceNormalizer normalizer = TwitterCharSequenceNormalizer.getInstance();

  @Test
  void normalizeHashtag() {
    Assertions.assertEquals("asdf   2nnfdf", normalizer.normalize("asdf #hasdk23 2nnfdf"));
  }

  @Test
  void normalizeUser() {
    Assertions.assertEquals("asdf   2nnfdf", normalizer.normalize("asdf @hasdk23 2nnfdf"));
  }

  @Test
  void normalizeRT() {
    Assertions.assertEquals(" 2nnfdf", normalizer.normalize("RT RT RT 2nnfdf"));
  }

  @Test
  void normalizeLaugh() {
    Assertions.assertEquals("ahahah", normalizer.normalize("ahahahah"));
    Assertions.assertEquals("haha", normalizer.normalize("hahha"));
    Assertions.assertEquals("haha", normalizer.normalize("hahaa"));
    Assertions.assertEquals("ahaha", normalizer.normalize("ahahahahhahahhahahaaaa"));
    Assertions.assertEquals("jaja", normalizer.normalize("jajjajajaja"));
  }


  @Test
  void normalizeFace() {
    Assertions.assertEquals("hello   hello", normalizer.normalize("hello :-) hello"));
    Assertions.assertEquals("hello   hello", normalizer.normalize("hello ;) hello"));
    Assertions.assertEquals("  hello", normalizer.normalize(":) hello"));
    Assertions.assertEquals("hello  ", normalizer.normalize("hello :P"));
  }

}
