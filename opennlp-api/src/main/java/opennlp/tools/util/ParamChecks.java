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

package opennlp.tools.util;

import java.util.Collection;

import opennlp.tools.commons.Internal;

/**
 * Argument checks that report an invalid parameter with an
 * {@link IllegalArgumentException} naming that parameter.
 * <p>
 * <b>Note:</b>
 * Do not use this class, internal use only!
 *
 * @since 3.0.0
 */
@Internal
public final class ParamChecks {

  private static final String NOT_NULL_SUFFIX = " must not be null";
  private static final String NOT_EMPTY_SUFFIX = " must not be null or empty";
  private static final String NOT_BLANK_SUFFIX = " must not be null or blank";
  private static final String NOT_NEGATIVE_SUFFIX = " must not be negative";
  private static final String FINITE_SUFFIX = " must be finite";

  private ParamChecks() {
    // utility class
  }

  /**
   * Returns {@code value} if it is not {@code null}.
   * <p>
   * The message of a thrown exception is {@code "<name> must not be null"}.
   *
   * @param value The argument to check.
   * @param name The parameter name used in the exception message.
   * @param <T> The argument type.
   * @return {@code value}, never {@code null}.
   * @throws IllegalArgumentException Thrown if {@code value} is {@code null}.
   */
  public static <T> T requireNonNullArg(T value, String name) {
    if (value == null) {
      throw new IllegalArgumentException(name + NOT_NULL_SUFFIX);
    }
    return value;
  }

  /**
   * Returns {@code value} if it is neither {@code null} nor empty.
   * <p>
   * The message of a thrown exception is {@code "<name> must not be null or empty"}.
   *
   * @param value The argument to check.
   * @param name The parameter name used in the exception message.
   * @param <T> The argument type.
   * @return {@code value}, never {@code null} or empty.
   * @throws IllegalArgumentException Thrown if {@code value} is {@code null} or has a length
   *     of zero.
   */
  public static <T extends CharSequence> T requireNonEmpty(T value, String name) {
    if (value == null || value.isEmpty()) {
      throw new IllegalArgumentException(name + NOT_EMPTY_SUFFIX);
    }
    return value;
  }

  /**
   * Returns {@code value} if it is neither {@code null} nor empty.
   * <p>
   * The message of a thrown exception is {@code "<name> must not be null or empty"}.
   *
   * @param value The argument to check.
   * @param name The parameter name used in the exception message.
   * @param <T> The argument type.
   * @return {@code value}, never {@code null} or empty.
   * @throws IllegalArgumentException Thrown if {@code value} is {@code null} or contains no
   *     elements.
   */
  public static <T extends Collection<?>> T requireNonEmpty(T value, String name) {
    if (value == null || value.isEmpty()) {
      throw new IllegalArgumentException(name + NOT_EMPTY_SUFFIX);
    }
    return value;
  }

  /**
   * Returns {@code value} if it is neither {@code null} nor blank.
   * <p>
   * A string is blank if it is empty or contains only Unicode {@code White_Space} code points,
   * as defined by {@link StringUtil#isUnicodeBlank(CharSequence)}. The message of a thrown
   * exception is
   * {@code "<name> must not be null or blank"}.
   *
   * @param value The argument to check.
   * @param name The parameter name used in the exception message.
   * @return {@code value}, never {@code null} or blank.
   * @throws IllegalArgumentException Thrown if {@code value} is {@code null} or blank.
   */
  public static String requireNonBlank(String value, String name) {
    if (StringUtil.isUnicodeBlank(value)) {
      throw new IllegalArgumentException(name + NOT_BLANK_SUFFIX);
    }
    return value;
  }

  /**
   * Returns {@code value} if it is not negative.
   * <p>
   * The message of a thrown exception is {@code "<name> must not be negative"}.
   *
   * @param value The argument to check.
   * @param name The parameter name used in the exception message.
   * @return {@code value}, never negative.
   * @throws IllegalArgumentException Thrown if {@code value} is less than zero.
   */
  public static int requireNonNegative(int value, String name) {
    if (value < 0) {
      throw new IllegalArgumentException(name + NOT_NEGATIVE_SUFFIX);
    }
    return value;
  }

  /**
   * Returns {@code value} if it is not negative.
   * <p>
   * The message of a thrown exception is {@code "<name> must not be negative"}.
   *
   * @param value The argument to check.
   * @param name The parameter name used in the exception message.
   * @return {@code value}, never negative.
   * @throws IllegalArgumentException Thrown if {@code value} is less than zero.
   */
  public static long requireNonNegative(long value, String name) {
    if (value < 0) {
      throw new IllegalArgumentException(name + NOT_NEGATIVE_SUFFIX);
    }
    return value;
  }

  /**
   * Returns {@code value} if it is not negative.
   * <p>
   * {@code NaN} is not less than zero and is returned unchanged, so check it with
   * {@link #requireFinite(double, String)} first where it is not allowed. The message of a
   * thrown exception is {@code "<name> must not be negative"}.
   *
   * @param value The argument to check.
   * @param name The parameter name used in the exception message.
   * @return {@code value}, never negative.
   * @throws IllegalArgumentException Thrown if {@code value} is less than zero.
   */
  public static double requireNonNegative(double value, String name) {
    if (value < 0) {
      throw new IllegalArgumentException(name + NOT_NEGATIVE_SUFFIX);
    }
    return value;
  }

  /**
   * Returns {@code value} if it is finite.
   * <p>
   * The message of a thrown exception is {@code "<name> must be finite"}.
   *
   * @param value The argument to check.
   * @param name The parameter name used in the exception message.
   * @return {@code value}, never {@code NaN} or infinite.
   * @throws IllegalArgumentException Thrown if {@code value} is {@code NaN} or infinite.
   */
  public static double requireFinite(double value, String name) {
    if (!Double.isFinite(value)) {
      throw new IllegalArgumentException(name + FINITE_SUFFIX);
    }
    return value;
  }

  /**
   * Checks two arrays that hold corresponding values. Both arrays must be non-empty,
   * have the same length, and contain no {@code null} entries.
   *
   * @param first The first array to check.
   * @param firstName The first parameter name used in exception messages.
   * @param second The second array to check.
   * @param secondName The second parameter name used in exception messages.
   * @throws IllegalArgumentException Thrown if either array is {@code null} or empty,
   *     their lengths differ, or an entry is {@code null}.
   */
  public static void requireNonEmptyParallelArrays(Object[] first, String firstName,
      Object[] second, String secondName) {
    requireNonNullArg(first, firstName);
    requireNonNullArg(second, secondName);
    if (first.length == 0) {
      throw new IllegalArgumentException(firstName + " must not be empty");
    }
    if (first.length != second.length) {
      throw new IllegalArgumentException(firstName + " and " + secondName
          + " must have the same length: " + first.length + " != " + second.length);
    }
    for (int i = 0; i < first.length; i++) {
      if (first[i] == null) {
        throw new IllegalArgumentException(firstName + "[" + i + "]" + NOT_NULL_SUFFIX);
      }
      if (second[i] == null) {
        throw new IllegalArgumentException(secondName + "[" + i + "]" + NOT_NULL_SUFFIX);
      }
    }
  }
}
