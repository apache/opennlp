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

import java.util.stream.Stream;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;


public class UrlCharSequenceNormalizerTest {

  private final UrlCharSequenceNormalizer normalizer = UrlCharSequenceNormalizer.getInstance();

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

  /**
   * The output on URLs from the WHATWG URL test data, as language detector models were
   * trained on it. These rows pin the limits stated in the class Javadoc (OPENNLP-1946);
   * {@link BoundedUrlCharSequenceNormalizer} is the variant without them.
   */
  private static Stream<Arguments> trainedOutput() {
    return Stream.of(
        Arguments.of("http://f:21/", " :21/"),
        Arguments.of("http://example.com/%20foo", " %20foo"),
        Arguments.of("http://user:pass@foo.example.com:8080/x", " : :8080/x"),
        Arguments.of("http://[::1]/", "http://[::1]/"),
        Arguments.of("http://example.com/foo\"bar", " \"bar"),
        Arguments.of("http://example.com/a'b", " 'b"),
        Arguments.of("HTTP://EXAMPLE.COM/", "HTTP://EXAMPLE.COM/"),
        Arguments.of("http://münchen.de/", " ünchen.de/"),
        Arguments.of("git+https://github.com/foo/bar", "git+ "),
        Arguments.of("blob:https://example.com:443/", "blob: :443/"),
        Arguments.of("telnet://user:pass@foobar.com:23/", "telnet://user: :23/"),
        Arguments.of("redis://user:pass@host:6379/0", "redis://user: :6379/0"),
        Arguments.of("http://256.256.256.256", " "),
        Arguments.of("http://?", " "),
        Arguments.of("see http://example.com/a.", "see  "),
        Arguments.of("(http://example.com/a)", "( )"));
  }

  @ParameterizedTest(name = "\"{0}\"")
  @MethodSource("trainedOutput")
  void normalizeKeepsTheOutputModelsWereTrainedOn(String text, String expected) {
    Assertions.assertEquals(expected, normalizer.normalize(text));
  }

  @Test
  void normalizeNullThrows() {
    Assertions.assertThrows(IllegalArgumentException.class, () -> normalizer.normalize(null));
  }
}
