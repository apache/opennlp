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


public class UrlCharSequenceNormalizerTest {

  public UrlCharSequenceNormalizer normalizer = UrlCharSequenceNormalizer.getInstance();

  @Test
  void normalizeUrl() {
    Assertions.assertEquals(
        "asdf   2nnfdf", normalizer.normalize("asdf http://asdf.com/dfa/cxs 2nnfdf"));


    Assertions.assertEquals(
        "asdf   2nnfdf  ", normalizer.normalize("asdf http://asdf.com/dfa/cx" +
            "s 2nnfdf http://asdf.com/dfa/cxs"));
  }

  @Test
  void normalizeEmail() {
    Assertions.assertEquals(
        "asdf   2nnfdf", normalizer.normalize("asdf asd.fdfa@hasdk23.com.br 2nnfdf"));
    Assertions.assertEquals(
        "asdf   2nnfdf  ", normalizer.normalize("asdf asd.fdfa@hasdk23.com.br" +
            " 2nnfdf asd.fdfa@hasdk23.com.br"));
    Assertions.assertEquals(
        "asdf   2nnfdf", normalizer.normalize("asdf asd+fdfa@hasdk23.com.br 2nnfdf"));
    Assertions.assertEquals(
        "asdf  _br 2nnfdf", normalizer.normalize("asdf asd.fdfa@hasdk23.com_br 2nnfdf"));
  }
}
