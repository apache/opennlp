/*
 * Copyright 2014 The Apache Software Foundation.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
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
import java.util.regex.Pattern;

import opennlp.tools.util.Span;

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
   * @return
   */
  public static synchronized RegexNameFinder getDefaultRegexNameFinders(Map<String, Pattern[]> config, DEFAULT_REGEX_NAME_FINDER... defaults) {
    if (config == null) {
      throw new IllegalArgumentException("config Map cannot be null");
    }
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
   * @return
   */
  public static synchronized RegexNameFinder getDefaultRegexNameFinders(DEFAULT_REGEX_NAME_FINDER... defaults) {
    if (defaults == null) {
      throw new IllegalArgumentException("defaults cannot be null");
    }
    return new RegexNameFinder(defaultsToMap(defaults));
  }

  private synchronized static Map<String, Pattern[]> defaultsToMap(DEFAULT_REGEX_NAME_FINDER... defaults) {
    Map<String, Pattern[]> regexMap = new HashMap<>();
    for (DEFAULT_REGEX_NAME_FINDER def : defaults) {
      regexMap.putAll(def.getRegexMap());
    }
    return regexMap;
  }

  public static void main(String[] args) {
    String text = "my email is opennlp@gmail.com and my phone num is 123-234-5678 and i like https://www.google.com and I visited MGRS  11sku528111 AKA  11S KU 528 111 and DMS 45N 123W AKA  +45.1234, -123.12 AKA  45.1234N 123.12W AKA 45 30 N 50 30 W";
    String[] tokens = text.split(" ");
    RegexNameFinder regexNameFinder = RegexNameFinderFactory.getDefaultRegexNameFinders(
        DEFAULT_REGEX_NAME_FINDER.DEGREES_MIN_SEC_LAT_LON,
        DEFAULT_REGEX_NAME_FINDER.EMAIL,
        DEFAULT_REGEX_NAME_FINDER.MGRS,
        DEFAULT_REGEX_NAME_FINDER.USA_PHONE_NUM,
        DEFAULT_REGEX_NAME_FINDER.URL);


    Span[] find = regexNameFinder.find(tokens);
    String[] spansToStrings = Span.spansToStrings(find, tokens);
    for (int i = 0; i < spansToStrings.length; i++) {
      System.out.println(find[i].getType() + " @ " + find[i].toString() + " = " + spansToStrings[i]);
    }
    System.out.println("With String, not String[]");

    Span[] find2 = regexNameFinder.find(text);
    String[] hits = new String[find2.length];
    for (int x = 0; x < find2.length; x++) {
      hits[x] = text.substring(find2[x].getStart(), find2[x].getEnd());
    }

    for (int i = 0; i < hits.length; i++) {
      System.out.println(find2[i].getType() + " @ " + find2[i].toString() + " = " + hits[i]);
    }
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
        // p[0] = Pattern.compile("([\\+(]?(\\d){2,}[)]?[- \\.]?(\\d){2,}[- \\.]?(\\d){2,}[- \\.]?(\\d){2,}[- \\.]?(\\d){2,})|([\\+(]?(\\d){2,}[)]?[- \\.]?(\\d){2,}[- \\.]?(\\d){2,}[- \\.]?(\\d){2,})|([\\+(]?(\\d){2,}[)]?[- \\.]?(\\d){2,}[- \\.]?(\\d){2,})", Pattern.CASE_INSENSITIVE);
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
        p[0] = Pattern.compile("([a-z0-9!#$%&'*+/=?^_`{|}~-]+(?:\\.[a-z0-9!#$%&'*+/=?^_`{|}~-]+)*|\"([\\x01-\\x08\\x0b\\x0c\\x0e-\\x1f\\x21\\x23-\\x5b\\x5d-\\x7f]|\\\\[\\x01-\\x09\\x0b\\x0c\\x0e-\\x7f])*\")@(?:(?:[a-z0-9]([a-z0-9-]*[a-z0-9])?\\.)+[a-z0-9](?:[a-z0-9-]*[a-z0-9])?|\\[((25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\.){3}(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?|[a-z0-9-]*[a-z0-9]:(?:[\\x01-\\x08\\x0b\\x0c\\x0e-\\x1f\\x21-\\x5a\\x53-\\x7f]|\\\\[\\x01-\\x09\\x0b\\x0c\\x0e-\\x7f])+)\\])", Pattern.CASE_INSENSITIVE);
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
        p[0] = Pattern.compile("\\d{1,2}[A-Za-z]\\s*[A-Za-z]{2}\\s*\\d{1,5}\\s*\\d{1,5}", Pattern.CASE_INSENSITIVE);
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
        p[0] = Pattern.compile("([-|\\+]?\\d{1,3}[d|D|\\u00B0|\\s](\\s*\\d{1,2}['|\\u2019|\\s])?(\\s*\\d{1,2}[\\\"|\\u201d])?\\s*[N|n|S|s]?)(\\s*|,|,\\s*)([-|\\+]?\\d{1,3}[d|D|\\u00B0|\\s](\\s*\\d{1,2}['|\\u2019|\\s])?(\\s*\\d{1,2}[\\\"|\\u201d])?\\s*[E|e|W|w]?)", Pattern.CASE_INSENSITIVE);
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
