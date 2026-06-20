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

/**
 * A {@link CharSequenceNormalizer} that transliterates German umlauts and the eszett the way German
 * conventionally expands them (DIN 5007-2): a-umlaut to {@code ae}, o-umlaut to {@code oe},
 * u-umlaut to {@code ue}, and eszett to {@code ss}, with the capital umlauts expanded likewise.
 *
 * <p>This is the correct diacritic fold for German, where the generic
 * {@link AccentFoldCharSequenceNormalizer} (which would yield {@code a}, {@code o}, {@code u}) is
 * wrong. It is an expanding, offset-changing transform, so like the other folds it belongs to the
 * derived matching form rather than to anything offset-preserving. A cursor pass with no regular
 * expression.</p>
 */
public class GermanUmlautCharSequenceNormalizer implements CharSequenceNormalizer {

  private static final long serialVersionUID = 1L;

  private static final int SMALL_A_UMLAUT = 0x00E4;
  private static final int SMALL_O_UMLAUT = 0x00F6;
  private static final int SMALL_U_UMLAUT = 0x00FC;
  private static final int CAPITAL_A_UMLAUT = 0x00C4;
  private static final int CAPITAL_O_UMLAUT = 0x00D6;
  private static final int CAPITAL_U_UMLAUT = 0x00DC;
  private static final int ESZETT = 0x00DF;

  private static final GermanUmlautCharSequenceNormalizer INSTANCE =
      new GermanUmlautCharSequenceNormalizer();

  private GermanUmlautCharSequenceNormalizer() {
  }

  /** {@return the shared, stateless instance} */
  public static GermanUmlautCharSequenceNormalizer getInstance() {
    return INSTANCE;
  }

  @Override
  public CharSequence normalize(CharSequence text) {
    final int length = text.length();
    final StringBuilder out = new StringBuilder(length + 4);
    for (int i = 0; i < length; i++) {
      final char c = text.charAt(i);
      switch (c) {
        case SMALL_A_UMLAUT:
          out.append("ae");
          break;
        case SMALL_O_UMLAUT:
          out.append("oe");
          break;
        case SMALL_U_UMLAUT:
          out.append("ue");
          break;
        case CAPITAL_A_UMLAUT:
          out.append("Ae");
          break;
        case CAPITAL_O_UMLAUT:
          out.append("Oe");
          break;
        case CAPITAL_U_UMLAUT:
          out.append("Ue");
          break;
        case ESZETT:
          out.append("ss");
          break;
        default:
          out.append(c);
          break;
      }
    }
    return out.toString();
  }
}
