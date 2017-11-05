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

/**
 * Normalizer to shrink repeated spaces / chars
 */
public class ShrinkCharSequenceNormalizer implements CharSequenceNormalizer {

  private static final Pattern REPEATED_CHAR_REGEX = Pattern.compile("(.)\\1{2,}",
      Pattern.CASE_INSENSITIVE);
  private static final Pattern SPACE_REGEX = Pattern.compile("\\s{2,}",
      Pattern.CASE_INSENSITIVE);

  private static final ShrinkCharSequenceNormalizer INSTANCE = new ShrinkCharSequenceNormalizer();

  public static ShrinkCharSequenceNormalizer getInstance() {
    return INSTANCE;
  }

  public CharSequence normalize (CharSequence text) {
    text = SPACE_REGEX.matcher(text).replaceAll(" ");
    return REPEATED_CHAR_REGEX.matcher(text).replaceAll("$1$1").trim();
  }
}
