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

public class TwitterCharSequenceNormalizer implements CharSequenceNormalizer {

  private static final Pattern HASH_USER_REGEX =
      Pattern.compile("[#@]\\S+");

  private static final Pattern RT_REGEX =
      Pattern.compile("\\b(rt[ :])+", Pattern.CASE_INSENSITIVE);

  private static final Pattern FACE_REGEX =
      Pattern.compile("[:;x]-?[()dop]", Pattern.CASE_INSENSITIVE);

  private static final Pattern LAUGH_REGEX =
      Pattern.compile("([hj])+([aieou])+(\\1+\\2+)+", Pattern.CASE_INSENSITIVE);

  private static final TwitterCharSequenceNormalizer INSTANCE = new TwitterCharSequenceNormalizer();

  public static TwitterCharSequenceNormalizer getInstance() {
    return INSTANCE;
  }

  public CharSequence normalize (CharSequence text) {
    String modified = HASH_USER_REGEX.matcher(text).replaceAll(" ");
    modified = RT_REGEX.matcher(modified).replaceAll(" ");
    modified = FACE_REGEX.matcher(modified).replaceAll(" ");
    modified = LAUGH_REGEX.matcher(modified).replaceAll("$1$2$1$2");
    return modified;
  }
}
