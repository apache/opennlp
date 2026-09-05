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

package opennlp.tools.namefind;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 *
 * Returns a {@link RegexNameFinder} based on a selection of
 * defaults or a configuration and a selection of defaults.
 * <p>
 * Please note: RegEx is not allowed in the processing path of OpenNLP.
 * However, a RegEx step is an obvious exception.
 */
public class RegexNameFinderFactory {

  /**
   * Allows for use of selected Defaults as well as regexes from external
   * configuration.
   *
   * @param config   A {@link Map} where the key is a type, and the value is a
   *                 {@link Pattern[]}. If a key clashes with one of the default keys,
   *                 the config map entry will be taken.
   * @param defaults One or more of the default {@link DEFAULT_REGEX_NAME_FINDER} enum values.
   * @return A {@link RegexNameFinder} instance.
   */
  public static synchronized RegexNameFinder getDefaultRegexNameFinders(
      Map<String, Pattern[]> config, DEFAULT_REGEX_NAME_FINDER... defaults) {
    Objects.requireNonNull(config, "config must not be null");

    Map<String, Pattern[]> defaultsToMap = new HashMap<>();
    if (defaults != null) {
      defaultsToMap = defaultsToMap(defaults);
    }
    defaultsToMap.putAll(config);
    return new RegexNameFinder(defaultsToMap);
  }

  /**
   * Retrieves a {@link RegexNameFinder} that will utilize specified default regexes.
   *
   * @param defaults One or more of the default {@link DEFAULT_REGEX_NAME_FINDER} enum values.
   * @return A {@link RegexNameFinder} instance.
   */
  public static synchronized RegexNameFinder getDefaultRegexNameFinders(
      DEFAULT_REGEX_NAME_FINDER... defaults) {
    Objects.requireNonNull(defaults, "defaults must not be null");
    return new RegexNameFinder(defaultsToMap(defaults));
  }

  private synchronized static Map<String, Pattern[]> defaultsToMap(
      DEFAULT_REGEX_NAME_FINDER... defaults) {
    Map<String, Pattern[]> regexMap = new HashMap<>();
    for (DEFAULT_REGEX_NAME_FINDER def : defaults) {
      regexMap.putAll(def.getRegexMap());
    }
    return regexMap;
  }

  public interface RegexAble {

    Map<String, Pattern[]> getRegexMap();

    String getType();
  }

  /**
   * Enumeration of typical regex expressions available in OpenNLP.
   */
  public enum DEFAULT_REGEX_NAME_FINDER implements RegexAble {

    USA_PHONE_NUM {
      @Override
      public Map<String, Pattern[]> getRegexMap() {
        Pattern[] p = new Pattern[1];
        // p[0] = Pattern.compile("([\\+(]?(\\d){2,}[)]?[- \\.]?(\\d){2,}[- \\.]?(\\d){2,}[- \\.]?
        // (\\d){2,}[- \\.]?(\\d){2,})|([\\+(]?(\\d){2,}[)]?[- \\.]?(\\d){2,}[- \\.]?(\\d){2,}[-
        // \\.]?(\\d){2,})|([\\+(]?(\\d){2,}[)]?[- \\.]?(\\d){2,}[- \\.]?(\\d){2,})",
        // Pattern.CASE_INSENSITIVE);
        p[0] = Pattern.compile("((\\(\\d{3}\\) ?)|(\\d{3}-))?\\d{3}-\\d{4}");
        Map<String, Pattern[]> regexMap = new HashMap<>();
        regexMap.put(getType(), p);
        return regexMap;
      }

      @Override
      public String getType() {
        return "PHONE_NUM";
      }
    },
    EMAIL {
      @Override
      public Map<String, Pattern[]> getRegexMap() {
        Pattern[] p = new Pattern[1];
        // Every quantifier is bounded by a constant, which removes both the exponential
        // backtracking and the recursion depth that made the old pattern a ReDoS vector.
        // Limits follow RFC 5321: local part <= 64 chars, each domain label <= 63 chars.
        p[0] = Pattern.compile(
            "(?<![a-z0-9!#$%&'*+/=?^_`{|}~.-])" +
            "(?:[a-z0-9!#$%&'*+/=?^_`{|}~-]{1,64}(?:\\.[a-z0-9!#$%&'*+/=?^_`{|}~-]{1,64}){0,10}" +
            "|\"(?:[\\x01-\\x08\\x0b\\x0c\\x0e-\\x1f\\x21\\x23-\\x5b\\x5d-\\x7f]" +
            "|\\\\[\\x01-\\x09\\x0b\\x0c\\x0e-\\x7f]){0,255}\")" +
            "@(?:(?:[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?\\.){1,20}" +
            "[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?" +
            "|\\[(?:(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\.){3}" +
            "(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\])", Pattern.CASE_INSENSITIVE);
        Map<String, Pattern[]> regexMap = new HashMap<>();
        regexMap.put(getType(), p);
        return regexMap;
      }

      @Override
      public String getType() {
        return "EMAIL";
      }
    },
    URL {
      @Override
      public Map<String, Pattern[]> getRegexMap() {
        Pattern[] p = new Pattern[1];
        // Flattened to single-level groups and every quantifier bounded by a constant.
        // The bounds alone remove the nested-quantifier backtracking and the recursion
        // depth that caused the StackOverflowError; they must stay bounded ({0,255} /
        // {1,63} rather than * / +) or a long path segment reintroduces the overflow.
        p[0] = Pattern.compile("\\b(?:(?:ht|f)tps?://|~/|/|www\\.)"
            + "(?:\\w{1,63}:\\w{1,63}@)?"
            + "(?:[-\\w]{1,63}\\.){1,20}(?:com|org|net|gov"
            + "|mil|biz|info|mobi|name|aero|jobs|museum"
            + "|travel|[a-z]{2})(?::\\d{1,5})?"
            + "(?:/(?:[-\\w~!$+|.,=]|%[a-f\\d]{2}){0,255}){0,50}"
            + "(?:\\?(?:[-\\w~!$+|.,*:=]|%[a-f\\d]{2}){0,255}"
            + "(?:&(?:[-\\w~!$+|.,*:=]|%[a-f\\d]{2}){0,255}){0,50})?"
            + "(?:#(?:[-\\w~!$+|.,*:=]|%[a-f\\d]{2}){0,255})?\\b", Pattern.CASE_INSENSITIVE);
        Map<String, Pattern[]> regexMap = new HashMap<>();
        regexMap.put(getType(), p);
        return regexMap;
      }

      @Override
      public String getType() {
        return "URL";
      }
    },
    MGRS {
      @Override
      public Map<String, Pattern[]> getRegexMap() {
        Pattern[] p = new Pattern[1];
        p[0] = Pattern.compile("\\d{1,2}[A-Za-z]\\s*[A-Za-z]{2}\\s*\\d{1,5}\\s*\\d{1,5}",
            Pattern.CASE_INSENSITIVE);
        Map<String, Pattern[]> regexMap = new HashMap<>();
        regexMap.put(getType(), p);
        return regexMap;
      }

      @Override
      public String getType() {
        return "MGRS";
      }
    },
    DEGREES_MIN_SEC_LAT_LON {
      @Override
      public Map<String, Pattern[]> getRegexMap() {
        Pattern[] p = new Pattern[1];
        p[0] = Pattern.compile("([-|\\+]?\\d{1,3}[d|D|\\u00B0|\\s](\\s*\\d{1,2}['|\\u2019|\\s])" +
            "?(\\s*\\d{1,2}[\\\"|\\u201d])?\\s*[N|n|S|s]?)(\\s*|,|,\\s*)([-|\\+]?\\d{1,3}[d|D|\\u00B0|" +
            "\\s](\\s*\\d{1,2}['|\\u2019|\\s])?(\\s*\\d{1,2}[\\\"|\\u201d])?\\s*[E|e|W|w]?)",
            Pattern.CASE_INSENSITIVE);
        Map<String, Pattern[]> regexMap = new HashMap<>();
        regexMap.put(getType(), p);
        return regexMap;
      }

      @Override
      public String getType() {
        return "DEGREES_MIN_SEC_LAT_LON";
      }
    }
  }
}
