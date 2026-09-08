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

package opennlp.tools.stemmer.light;

import java.util.Set;

import opennlp.tools.commons.ThreadSafe;
import opennlp.tools.stemmer.Stemmer;
import opennlp.tools.stemmer.StemmerFactory;

/**
 * Minimal Spanish stemmer that normalizes plural nouns.
 *
 * <p>Based on Apache Lucene's {@code SpanishPluralStemmer}. Instances are stateless, thread-safe,
 * and implement {@link StemmerFactory}. Input must use lowercase NFC; the stemmer does not apply
 * case folding or Unicode normalization.</p>
 *
 * @see <a href="https://github.com/apache/lucene/blob/4965e8d4d960445a0522fae512c60c6d8f11fc29/lucene/analysis/common/src/java/org/apache/lucene/analysis/es/SpanishPluralStemmer.java">
 *     Apache Lucene SpanishPluralStemmer</a>
 * @since 3.0.0
 */
@ThreadSafe
public final class SpanishMinimalStemmer extends AbstractCharArrayStemmer
    implements StemmerFactory {

  private static final Set<String> INVARIANTS = Set.of(
      "abrebotellas",
      "abrecartas",
      "abrelatas",
      "afueras",
      "albatros",
      "albricias",
      "aledaños",
      "alexis",
      "alicates",
      "analisis",
      "andurriales",
      "antitesis",
      "añicos",
      "apendicitis",
      "apocalipsis",
      "arcoiris",
      "aries",
      "bilis",
      "boletus",
      "boris",
      "brindis",
      "cactus",
      "canutas",
      "caries",
      "cascanueces",
      "cascarrabias",
      "ciempies",
      "cifosis",
      "cortaplumas",
      "corpus",
      "cosmos",
      "cosquillas",
      "creces",
      "crisis",
      "cuatrocientas",
      "cuatrocientos",
      "cuelgacapas",
      "cuentacuentos",
      "cuentapasos",
      "cumpleaños",
      "doscientas",
      "doscientos",
      "dosis",
      "enseres",
      "entonces",
      "esponsales",
      "estatus",
      "exequias",
      "fauces",
      "forceps",
      "fotosintesis",
      "gafas",
      "gafotas",
      "gargaras",
      "gris",
      "honorarios",
      "ictus",
      "jueves",
      "lapsus",
      "lavacoches",
      "lavaplatos",
      "limpiabotas",
      "lunes",
      "maitines",
      "martes",
      "mondadientes",
      "novecientas",
      "novecientos",
      "nupcias",
      "ochocientas",
      "ochocientos",
      "pais",
      "paris",
      "parabrisas",
      "paracaidas",
      "parachoques",
      "paraguas",
      "pararrayos",
      "pisapapeles",
      "piscis",
      "portaaviones",
      "portamaletas",
      "portamantas",
      "quinientas",
      "quinientos",
      "quitamanchas",
      "recogepelotas",
      "rictus",
      "rompeolas",
      "sacacorchos",
      "sacapuntas",
      "saltamontes",
      "salvavidas",
      "seis",
      "seiscientas",
      "seiscientos",
      "setecientas",
      "setecientos",
      "sintesis",
      "tenis",
      "tifus",
      "trabalenguas",
      "vacaciones",
      "venus",
      "versus",
      "viacrucis",
      "virus",
      "viveres",
      "volandas");

  private static final Set<String> SPECIAL_CASES = Set.of(
      "albalaes",
      "albumes",
      "bojes",
      "carcajes",
      "clubes",
      "contrarrelojes",
      "faralaes",
      "itemes",
      "noes",
      "relojes",
      "sandwiches",
      "sies",
      "yoes");

  /** {@inheritDoc} */
  @Override
  public Stemmer newStemmer() {
    return new SpanishMinimalStemmer();
  }

  /** {@inheritDoc} */
  @Override
  int stem(char[] s, int len) {
    if (len < 4) {
      return len;
    }
    removeAccents(s, len);
    if (s[len - 1] != 's') {
      return len;
    }
    final String normalized = new String(s, 0, len);
    if (INVARIANTS.contains(normalized)) {
      return len;
    }
    if (SPECIAL_CASES.contains(normalized)) {
      return len - 2;
    }
    if (!isVowel(s[len - 2])) {
      return len - 1;
    }
    if (s[len - 4] == 'q'
        || (s[len - 4] == 'g' && s[len - 3] == 'u'
            && (s[len - 2] == 'i' || s[len - 2] == 'e'))) {
      return len - 1;
    }
    if (isVowel(s[len - 4]) && s[len - 3] == 'r' && s[len - 2] == 'e') {
      return len - 2;
    }
    if (isVowel(s[len - 4])
        && (s[len - 3] == 'd' || s[len - 3] == 'l'
            || s[len - 3] == 'n' || s[len - 3] == 'x')
        && s[len - 2] == 'e') {
      return len - 2;
    }
    if ((s[len - 3] == 'y' || s[len - 3] == 'u') && s[len - 2] == 'e') {
      return len - 2;
    }
    if ((s[len - 4] == 'u' || s[len - 4] == 'l' || s[len - 4] == 'r'
        || s[len - 4] == 't' || s[len - 4] == 'n')
        && s[len - 3] == 'i' && s[len - 2] == 'e') {
      return len - 2;
    }
    if (s[len - 3] == 's' && s[len - 2] == 'e') {
      return len - 2;
    }
    if (isVowel(s[len - 3]) && s[len - 2] == 'i') {
      s[len - 2] = 'y';
      return len - 1;
    }
    if (s[len - 3] == 'd' && s[len - 2] == 'i') {
      s[len - 2] = 'y';
      return len - 1;
    }
    if (s[len - 3] == 'c' && s[len - 2] == 'e') {
      s[len - 3] = 'z';
      return len - 2;
    }
    if (isVowel(s[len - 2])) {
      return len - 1;
    }
    return len;
  }

  /** Returns whether {@code c} is an unaccented Spanish vowel. */
  private boolean isVowel(char c) {
    return c == 'a' || c == 'e' || c == 'i' || c == 'o' || c == 'u';
  }

  /** Replaces accented vowels in the stem with their unaccented forms. */
  private void removeAccents(char[] s, int len) {
    for (int i = 0; i < len; i++) {
      switch (s[i]) {
        case '\u00E0', '\u00E1', '\u00E2', '\u00E4' -> s[i] = 'a';
        case '\u00E8', '\u00E9', '\u00EA', '\u00EB' -> s[i] = 'e';
        case '\u00EC', '\u00ED', '\u00EE', '\u00EF' -> s[i] = 'i';
        case '\u00F2', '\u00F3', '\u00F4', '\u00F6' -> s[i] = 'o';
        case '\u00F9', '\u00FA', '\u00FB', '\u00FC' -> s[i] = 'u';
        default -> {
        }
      }
    }
  }
}
