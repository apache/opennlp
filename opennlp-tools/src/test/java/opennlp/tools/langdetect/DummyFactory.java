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


import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

import opennlp.tools.ngram.NGramModel;
import opennlp.tools.tokenize.SimpleTokenizer;
import opennlp.tools.util.StringList;
import opennlp.tools.util.normalizer.CharSequenceNormalizer;

public class DummyFactory extends LanguageDetectorFactory {


  public DummyFactory() {
    super();
  }

  @Override
  public void init() {
    super.init();
  }

  @Override
  public LanguageDetectorContextGenerator getContextGenerator() {
    return new DummyFactory.MyContectGenerator(2, 5,
        new DummyFactory.UpperCaseNormalizer());
  }

  public class UpperCaseNormalizer implements CharSequenceNormalizer {
    @Override
    public CharSequence normalize(CharSequence text) {
      return text.toString().toUpperCase();
    }
  }

  public class MyContectGenerator extends DefaultLanguageDetectorContextGenerator {

    public MyContectGenerator(int min, int max, CharSequenceNormalizer... normalizers) {
      super(min, max, normalizers);
    }

    @Override
    public String[] getContext(CharSequence document) {
      String[] superContext = super.getContext(document);

      List<String> context = new ArrayList(Arrays.asList(superContext));

      document = this.normalizer.normalize(document);

      SimpleTokenizer tokenizer = SimpleTokenizer.INSTANCE;
      String[] words = tokenizer.tokenize(document.toString());
      NGramModel tokenNgramModel = new NGramModel();
      if (words.length > 0) {
        tokenNgramModel.add(new StringList(words), 1, 3);
        Iterator tokenNgramIterator = tokenNgramModel.iterator();

        while (tokenNgramIterator.hasNext()) {
          StringList tokenList = (StringList) tokenNgramIterator.next();
          if (tokenList.size() > 0) {
            context.add("tg=" + tokenList.toString());
          }
        }
      }

      return context.toArray(new String[context.size()]);
    }
  }
}
