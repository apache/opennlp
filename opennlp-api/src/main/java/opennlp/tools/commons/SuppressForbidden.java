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

package opennlp.tools.commons;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a member with a public contract that is a user-supplied regular expression,
 * for example the patterns given to {@code RegexNameFinder} or the alphanumeric
 * {@link java.util.regex.Pattern} accepted by the tokenizer factories.
 * <p>
 * Production code scans text with code-point loops and the
 * {@code opennlp.tools.util.StringUtil} helpers instead of {@code java.util.regex}.
 * The build enforces this rule with forbiddenapis, using the signatures in
 * {@code dev/forbidden-regex.txt} (OPENNLP-1935). An annotated member is exempt
 * from that check. Apply it to the narrowest member (field, constructor, or method)
 * that needs it rather than to the enclosing class, so a regular expression added to
 * another member of the same class is still reported.
 * <p>
 * The annotation is kept in class files and read by the build only; it has no
 * effect at runtime.
 */
@Documented
@Retention(RetentionPolicy.CLASS)
@Target({ElementType.TYPE, ElementType.FIELD, ElementType.METHOD, ElementType.CONSTRUCTOR})
public @interface SuppressForbidden {

  /**
   * @return The public contract that requires the regular expression.
   */
  String value();
}
