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
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * Classes, fields, or methods annotated {@code &#64;Internal} are for OpenNLP
 * internal use only. Such elements are likely to be removed, have a different access level,
 * or might experience a signature change in upcoming releases of OpenNLP.
 */
@Documented
@Retention(value = RetentionPolicy.RUNTIME)
public @interface Internal {

  String value() default "";

  /**
   * The OpenNLP release when an element was first declared internal.
   */
  String since() default "";
}
