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

import java.util.Random;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Characterization tests for {@link UrlCharSequenceNormalizer}.
 *
 * <p>The fixed expectations below were probed against the regex implementation
 * ({@code "https?://[-_.?&~;+=/#0-9A-Za-z]+"} to a space, then
 * {@code "(?<![-+_.0-9A-Za-z])[-+_.0-9A-Za-z]+@[-0-9A-Za-z]+[-.0-9A-Za-z]+"} to a space) and pin
 * the accept/reject boundary byte for byte. Notable sharp edges kept visible:</p>
 * <ul>
 *   <li>the scheme is lowercase-only, and a URL body needs at least one character out of the
 *       body class, so {@code "http:// x"} and {@code "http://"} pass through untouched;</li>
 *   <li>the body class has no colon, percent sign, at sign, or non-ASCII, so ports, escapes,
 *       userinfo, and IDN labels cut the match short;</li>
 *   <li>a mail match needs a local part run bounded on the left by the lookbehind, and a domain
 *       of at least two characters starting with no dot: {@code "a@b c"} does not match, while
 *       {@code "a@b."} swallows the trailing dot;</li>
 *   <li>after a failed candidate the scan resumes one char further, so {@code "a@b@cd.com"}
 *       still finds the inner address.</li>
 * </ul>
 */
public class UrlCharSequenceNormalizerCharacterizationTest {

  private static final UrlCharSequenceNormalizer NORMALIZER =
      UrlCharSequenceNormalizer.getInstance();

  private static void check(String input, String expected) {
    assertEquals(expected, NORMALIZER.normalize(input).toString());
  }

  @Test
  void urlsBecomeASingleSpace() {
    check("", "");
    check("asdf http://asdf.com/dfa/cxs 2nnfdf", "asdf   2nnfdf");
    check("asdf http://asdf.com/dfa/cxs 2nnfdf http://asdf.com/dfa/cxs", "asdf   2nnfdf  ");
    check("http://example.com", " ");
    check("visit http://example.com now", "visit   now");
    check("https://a.b/c?d=e&f=g#h+i~j;k=l_m-n", " ");
    check("https://x", " ");
    check("http://EXAMPLE.com", " ");
    check("xhttp://y", "x ");
    check("ahttp://x", "a ");
  }

  @Test
  void urlRecognitionRejectsAtTheExactRegexBoundary() {
    check("http:// x", "http:// x");
    check("http://", "http://");
    check("HTTP://X.COM", "HTTP://X.COM");
    check("httpss://x", "httpss://x");
    check("ftp://x", "ftp://x");
    check("www.example.com", "www.example.com");
    // The body class has no colon, percent sign, or at sign, and no non-ASCII.
    check("http://x:8080/y", " :8080/y");
    check("http://a%20b", " %20b");
    check("http://http://x", " ://x");
    check("http://\u043F\u0440\u0438\u043C\u0435\u0440", "http://\u043F\u0440\u0438\u043C\u0435\u0440");
    check("http://x.\u043F\u0440\u0438\u043C\u0435\u0440", " \u043F\u0440\u0438\u043C\u0435\u0440");
    check("http://user@example.com", " @example.com");
  }

  @Test
  void mailAddressesBecomeASingleSpace() {
    check("asdf asd.fdfa@hasdk23.com.br 2nnfdf", "asdf   2nnfdf");
    check("asdf asd.fdfa@hasdk23.com.br 2nnfdf asd.fdfa@hasdk23.com.br", "asdf   2nnfdf  ");
    check("asdf asd+fdfa@hasdk23.com.br 2nnfdf", "asdf   2nnfdf");
    check("asdf asd.fdfa@hasdk23.com_br 2nnfdf", "asdf  _br 2nnfdf");
    check(".user@x.com", " ");
    check("-a+b_c.d@e-f.g2 end", "  end");
    check("a@b.c", " ");
    check("1@2.3", " ");
    check("a@-b", " ");
    check("x@y..z", " ");
    check("x@y-.", " ");
    check("mailto:user@example.com", "mailto: ");
    check("a@b.co,c@d.eu", " , ");
    check("a b@c.de f", "a   f");
    check("see http://x.y, then a@b.cd!", "see  , then  !");
  }

  @Test
  void mailRecognitionRejectsAtTheExactRegexBoundary() {
    // A one-char domain with nothing to lend to the second domain class fails.
    check("a@b c", "a@b c");
    check("a@bc d", "  d");
    // The trailing dot is part of the second domain class and gets swallowed.
    check("a@b.", " ");
    check("user@example.com.", " ");
    check("Contact a@b.com.", "Contact  ");
    // The domain must not start with a dot.
    check("a@.com", "a@.com");
    check("x@.y", "x@.y");
    // The local part must be a maximal run: no match can start inside one.
    check("user@@example.com", "user@@example.com");
    check("@example.com", "@example.com");
    // After the failed candidate at "a", the scan resumes and finds "b@cd.com".
    check("a@b@cd.com", "a@ ");
  }

  @Test
  void lookbehindOnlySeesTheSingleCharBeforeTheLocalPart() {
    // Non-ASCII (including a low surrogate) is outside the lookbehind class, so the address
    // right after it still matches.
    check("\uD83D\uDE00a@b.co", "\uD83D\uDE00 ");
    check("\u00E9a@b.co", "\u00E9 ");
  }

  @Test
  void nullTextIsRejected() {
    // Not characterization: the cursor refactor deliberately rejects null with an
    // IllegalArgumentException, where the regex version threw an undocumented
    // NullPointerException from Matcher.
    assertThrows(IllegalArgumentException.class, () -> NORMALIZER.normalize(null));
  }

  @Test
  void matchesTheFormerRegexesOnRandomizedInputs() {
    final Pattern urlRegex = Pattern.compile("https?://[-_.?&~;+=/#0-9A-Za-z]+");
    final Pattern mailRegex =
        Pattern.compile("(?<![-+_.0-9A-Za-z])[-+_.0-9A-Za-z]+@[-0-9A-Za-z]+[-.0-9A-Za-z]+");
    final String[] pool = {"a", "b", "Z", "1", " ", ".", ",", "-", "+", "_", "@", ":", "/", "%",
        "~", ";", "=", "&", "?", "#", "http", "https", "ttp", "://", "http://", "s", "x.com",
        "co", ".com", "a@b", "@b.co", "\u00E9", "\uD83D\uDE00", "\uD83D", "\uDE00"};
    final Random random = new Random(42);
    for (int i = 0; i < 5000; i++) {
      final String input = CharacterizationInputs.randomInput(random, pool);
      final String expected = mailRegex
          .matcher(urlRegex.matcher(input).replaceAll(" "))
          .replaceAll(" ");
      assertEquals(expected, NORMALIZER.normalize(input).toString(),
          () -> "Input: " + CharacterizationInputs.escape(input));
    }
  }
  @Test
  void noMatchInputIsReturnedUncopied() {
    final String plain = "no links in this sentence at all";
    Assertions.assertSame(plain, NORMALIZER.normalize(plain));
  }
}
