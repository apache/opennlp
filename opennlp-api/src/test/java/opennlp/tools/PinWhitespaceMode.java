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

package opennlp.tools;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.parallel.ResourceLock;

import opennlp.tools.util.WhitespaceMode;

/**
 * Pins the {@link WhitespaceMode} for all tests of the annotated class, for example to run
 * models trained under {@link WhitespaceMode#LEGACY}. The mode is active before the first
 * {@code @BeforeAll} method, is restored after each test, and is reset to property resolution
 * after the last test. The class also holds the {@link ResetWhitespaceMode#RESOURCE} lock.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@ExtendWith(WhitespaceModeExtension.class)
@ResourceLock(ResetWhitespaceMode.RESOURCE)
public @interface PinWhitespaceMode {

  /**
   * @return The {@link WhitespaceMode} to activate for the annotated class.
   */
  WhitespaceMode value();
}
