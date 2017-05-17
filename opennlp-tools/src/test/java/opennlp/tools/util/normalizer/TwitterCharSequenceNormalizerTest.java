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


public class TwitterCharSequenceNormalizerTest {

  public TwitterCharSequenceNormalizer normalizer = TwitterCharSequenceNormalizer.getInstance();

  @Test
  public void normalizeHashtag() throws Exception {
    Assert.assertEquals("asdf   2nnfdf", normalizer.normalize("asdf #hasdk23 2nnfdf"));
  }

  @Test
  public void normalizeUser() throws Exception {
    Assert.assertEquals("asdf   2nnfdf", normalizer.normalize("asdf @hasdk23 2nnfdf"));
  }

  @Test
  public void normalizeRT() throws Exception {
    Assert.assertEquals(" 2nnfdf", normalizer.normalize("RT RT RT 2nnfdf"));
  }

  @Test
  public void normalizeLaugh() throws Exception {
    Assert.assertEquals("ahahah", normalizer.normalize("ahahahah"));
    Assert.assertEquals("haha", normalizer.normalize("hahha"));
    Assert.assertEquals("haha", normalizer.normalize("hahaa"));
    Assert.assertEquals("ahaha", normalizer.normalize("ahahahahhahahhahahaaaa"));
    Assert.assertEquals("jaja", normalizer.normalize("jajjajajaja"));
  }



  @Test
  public void normalizeFace() throws Exception {
    Assert.assertEquals("hello   hello", normalizer.normalize("hello :-) hello"));
    Assert.assertEquals("hello   hello", normalizer.normalize("hello ;) hello"));
    Assert.assertEquals("  hello", normalizer.normalize(":) hello"));
    Assert.assertEquals("hello  ", normalizer.normalize("hello :P"));
  }

}
