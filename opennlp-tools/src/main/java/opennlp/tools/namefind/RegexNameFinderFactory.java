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
 * Returns a RegexNameFinder based on A selection of
 * defaults or a configuration and a selection of defaults
 */
public class RegexNameFinderFactory {

  /**
   * Allows for use of selected Defaults as well as regexes from external
   * configuration
   *
   * @param config   a map where the key is a type, and the value is a
   *                 Pattern[]. If the keys clash with default keys, the config
   *                 map will win
   * @param defaults the OpenNLP default regexes
   * @return {@link RegexNameFinder}
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
   * Returns a RegexNamefinder that will utilize specified default regexes.
   *
   * @param defaults the OpenNLP default regexes
   * @return {@link RegexNameFinder}
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
        p[0] = Pattern.compile("([a-z0-9!#$%&'*+/=?^_`{|}~-]+(?:\\.[a-z0-9!#$%&'*+/=?^_`{|}~-]+)*" +
            "|\"([\\x01-\\x08\\x0b\\x0c\\x0e-\\x1f\\x21\\x23-\\x5b\\x5d-\\x7f]|\\\\[\\x01-\\x09" +
            "\\x0b\\x0c\\x0e-\\x7f])*\")@(?:(?:[a-z0-9]([a-z0-9-]*[a-z0-9])?\\.)+[a-z0-9](?:[a-z0-9-]" +
            "*[a-z0-9])?|\\[((25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\.){3}(?:25[0-5]|2[0-4][0-9]|[01]" +
            "?[0-9][0-9]?|[a-z0-9-]*[a-z0-9]:(?:[\\x01-\\x08\\x0b\\x0c\\x0e-\\x1f\\x21-\\x5a\\x53-\\x7f]" +
            "|\\\\[\\x01-\\x09\\x0b\\x0c\\x0e-\\x7f])+)\\])", Pattern.CASE_INSENSITIVE);
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
        p[0] = Pattern.compile("\\b(((ht|f)tp(s?)\\:\\/\\/|~\\/|\\/)|www.)"
            + "(\\w+:\\w+@)?(([-\\w]+\\.)+(com|org|net|gov"
            + "|mil|biz|info|mobi|name|aero|jobs|museum"
            + "|travel|[a-z]{2}))(:[\\d]{1,5})?"
            + "(((\\/([-\\w~!$+|.,=]|%[a-f\\d]{2})+)+|\\/)+|\\?|#)?"
            + "((\\?([-\\w~!$+|.,*:]|%[a-f\\d{2}])+=?"
            + "([-\\w~!$+|.,*:=]|%[a-f\\d]{2})*)"
            + "(&(?:[-\\w~!$+|.,*:]|%[a-f\\d{2}])+=?"
            + "([-\\w~!$+|.,*:=]|%[a-f\\d]{2})*)*)*"
            + "(#([-\\w~!$+|.,*:=]|%[a-f\\d]{2})*)?\\b", Pattern.CASE_INSENSITIVE);
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
