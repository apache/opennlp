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

import java.util.regex.Pattern;

public class UrlCharSequenceNormalizer implements CharSequenceNormalizer {

  private static final Pattern URL_REGEX =
      Pattern.compile("https?://[-_.?&~;+=/#0-9A-Za-z]+");
  private static final Pattern MAIL_REGEX =
      Pattern.compile("[-_.0-9A-Za-z]+@[-_0-9A-Za-z]+[-_.0-9A-Za-z]+");

  private static final UrlCharSequenceNormalizer INSTANCE = new UrlCharSequenceNormalizer();

  public static UrlCharSequenceNormalizer getInstance() {
    return INSTANCE;
  }

  public CharSequence normalize (CharSequence text) {
    String modified = URL_REGEX.matcher(text).replaceAll(" ");
    return MAIL_REGEX.matcher(modified).replaceAll(" ");
  }
}
