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

package opennlp.tools.stemmer.snowball;

import opennlp.tools.stemmer.Stemmer;

public class SnowballStemmer implements Stemmer {

  public enum ALGORITHM {
    DANISH,
    DUTCH,
    ENGLISH,
    FINNISH,
    FRENCH,
    GERMAN,
    HUNGARIAN,
    ITALIAN,
    NORWEGIAN,
    PORTER,
    PORTUGUESE,
    ROMANIAN,
    RUSSIAN,
    SPANISH,
    SWEDISH,
    TURKISH
  }

  private final AbstractSnowballStemmer stemmer;
  private final int repeat;

  public SnowballStemmer(ALGORITHM algorithm, int repeat) {
    this.repeat = repeat;

    if (ALGORITHM.DANISH.equals(algorithm)) {
      stemmer = new danishStemmer();
    }
    else if (ALGORITHM.DUTCH.equals(algorithm)) {
      stemmer = new dutchStemmer();
    }
    else if (ALGORITHM.ENGLISH.equals(algorithm)) {
      stemmer = new englishStemmer();
    }
    else if (ALGORITHM.FINNISH.equals(algorithm)) {
      stemmer = new finnishStemmer();
    }
    else if (ALGORITHM.FRENCH.equals(algorithm)) {
      stemmer = new frenchStemmer();
    }
    else if (ALGORITHM.GERMAN.equals(algorithm)) {
      stemmer = new germanStemmer();
    }
    else if (ALGORITHM.HUNGARIAN.equals(algorithm)) {
      stemmer = new hungarianStemmer();
    }
    else if (ALGORITHM.ITALIAN.equals(algorithm)) {
      stemmer = new italianStemmer();
    }
    else if (ALGORITHM.NORWEGIAN.equals(algorithm)) {
      stemmer = new norwegianStemmer();
    }
    else if (ALGORITHM.PORTER.equals(algorithm)) {
      stemmer = new porterStemmer();
    }
    else if (ALGORITHM.PORTUGUESE.equals(algorithm)) {
      stemmer = new portugueseStemmer();
    }
    else if (ALGORITHM.ROMANIAN.equals(algorithm)) {
      stemmer = new romanianStemmer();
    }
    else if (ALGORITHM.RUSSIAN.equals(algorithm)) {
      stemmer = new russianStemmer();
    }
    else if (ALGORITHM.SPANISH.equals(algorithm)) {
      stemmer = new spanishStemmer();
    }
    else if (ALGORITHM.SWEDISH.equals(algorithm)) {
      stemmer = new swedishStemmer();
    }
    else if (ALGORITHM.TURKISH.equals(algorithm)) {
      stemmer = new turkishStemmer();
    }
    else {
      throw new IllegalStateException("Unexpected stemmer algorithm: " + algorithm.toString());
    }
  }

  public SnowballStemmer(ALGORITHM algorithm) {
    this(algorithm, 1);
  }

  public CharSequence stem(CharSequence word) {

    stemmer.setCurrent(word.toString());

    for (int i = 0; i < repeat; i++) {
      stemmer.stem();
    }

    return stemmer.getCurrent();
  }
}
