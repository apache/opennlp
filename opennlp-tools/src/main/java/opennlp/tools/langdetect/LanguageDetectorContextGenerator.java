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

package opennlp.tools.langdetect;

import java.util.Collection;
import java.util.LinkedList;

import opennlp.tools.ngram.NGramModel;
import opennlp.tools.util.StringList;
import opennlp.tools.util.StringUtil;
import opennlp.tools.util.normalizer.AggregateCharSequenceNormalizer;
import opennlp.tools.util.normalizer.CharSequenceNormalizer;
import opennlp.tools.util.normalizer.EmojiCharSequenceNormalizer;
import opennlp.tools.util.normalizer.NumberCharSequenceNormalizer;
import opennlp.tools.util.normalizer.ShrinkCharSequenceNormalizer;
import opennlp.tools.util.normalizer.TwitterCharSequenceNormalizer;
import opennlp.tools.util.normalizer.UnicodeCharSequenceNormalizer;
import opennlp.tools.util.normalizer.UrlCharSequenceNormalizer;

/**
 * Context generator for document categorizer
 */
class LanguageDetectorContextGenerator {

  private final int minLength;
  private final int maxLength;
  private final CharSequenceNormalizer normalizer;

  LanguageDetectorContextGenerator(int minLength, int maxLength) {
    this.minLength = minLength;
    this.maxLength = maxLength;

    this.normalizer = new AggregateCharSequenceNormalizer(
        EmojiCharSequenceNormalizer.getInstance(),
        UrlCharSequenceNormalizer.getInstance(),
        TwitterCharSequenceNormalizer.getInstance(),
        NumberCharSequenceNormalizer.getInstance(),
        UnicodeCharSequenceNormalizer.getInstance(),
        ShrinkCharSequenceNormalizer.getInstance());
  }

  /**
   * Initializes the current instance with min 2 length and max 5 length of ngrams.
   */
  LanguageDetectorContextGenerator() {
    this(2, 5);
  }

  public String[] getContext(String document) {

    Collection<String> context = new LinkedList<>();

    NGramModel model = new NGramModel();
    String normalized = normalizer.normalize(document).toString();
    model.add(normalized, minLength, maxLength);

    for (StringList tokenList : model) {
      if (tokenList.size() > 0) {
        context.add("ng=" + StringUtil.toLowerCase(tokenList.getToken(0)));
      }
    }
    return context.toArray(new String[context.size()]);
  }
}
