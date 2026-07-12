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

package opennlp.tools.formats.brat;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import opennlp.tools.tokenize.WhitespaceTokenizer;

/**
 * Encapsulates a type to class mapping for entities, relations, events, etc.
 * <p>
 * Details on how a annotation configuration file should be structured can be found
 * in the  <a href="https://brat.nlplab.org/configuration.html">brat annotation configuration</a>
 * section of the official BRAT documentation.
 */
public class AnnotationConfiguration {

  public static final String SPAN_TYPE = "Span";
  public static final String ENTITY_TYPE = "Entity";
  public static final String RELATION_TYPE = "Relation";
  public static final String ATTRIBUTE_TYPE = "Attribute";
  public static final String EVENT_TYPE = "Event";

  private static final String SYMBOL_HASH = "#";
  private static final String BRACKET_OPEN = "[";
  private static final String BRACKET_CLOSE = "]";

  private final Map<String, String> typeToClassMap;

  /**
   * Initializes an {@link AnnotationConfiguration} with the specified {@code typeToClassMap}.
   * @param typeToClassMap A type to class mapping. Must not be {@code null}.
   */
  public AnnotationConfiguration(Map<String, String> typeToClassMap) {
    this.typeToClassMap = Map.copyOf(typeToClassMap);
  }

  /**
   * @param type The type to get the type class for.
   * @return Retrieves the class for the specified {@code type}, {@code null} if not found.
   */
  public String getTypeClass(String type) {
    return typeToClassMap.get(type);
  }

  /**
   * Parses a given {@link File annConfigFile} into a {@link AnnotationConfiguration}.
   *
   * @param in A valid {@link File annConfigFile} from which the config should
   *           be read. Must not be {@code null} and must be in the correct format,
   *           see: <a href="https://brat.nlplab.org/configuration.html">
   *           Brat annotation configuration</a>
   *
   * @return A valid {@link AnnotationConfiguration} instance.
   * @throws IOException Thrown if IO errors occurred during parsing.
   */
  public static AnnotationConfiguration parse(InputStream in) throws IOException {
    Map<String, String> typeToClassMap = new HashMap<>();

    try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
      // Note: This only supports entities and relations section
      String line;
      String sectionType = null;

      while ((line = reader.readLine()) != null) {
        line = line.trim();

        if (!line.isEmpty()) {
          if (!line.startsWith(SYMBOL_HASH)) {
            if (line.startsWith(BRACKET_OPEN) && line.endsWith(BRACKET_CLOSE)) {
              sectionType = line.substring(line.indexOf('[') + 1, line.indexOf(']'));
            } else {
              String typeName = WhitespaceTokenizer.INSTANCE.tokenize(line)[0];

              switch (sectionType) {
                case "entities":
                  typeToClassMap.put(typeName, AnnotationConfiguration.ENTITY_TYPE);
                  break;
                case "relations":
                  typeToClassMap.put(typeName, AnnotationConfiguration.RELATION_TYPE);
                  break;
                case "attributes":
                  typeToClassMap.put(typeName, AnnotationConfiguration.ATTRIBUTE_TYPE);
                  break;
                case "events":
                  typeToClassMap.put(typeName, AnnotationConfiguration.EVENT_TYPE);
                  break;
                default:
                  break;
              }
            }
          }
        }
      }
    }
    return new AnnotationConfiguration(typeToClassMap);
  }

  /**
   * Parses a given {@link File annConfigFile} into a {@link AnnotationConfiguration}.
   *
   * @param annConfigFile A valid {@link File annConfigFile} from which the config should
   *                      be read. Must not be {@code null} and must be in the correct format,
   *                      see: <a href="https://brat.nlplab.org/configuration.html">
   *                        Brat annotation configuration</a>
   *
   * @return A valid {@link AnnotationConfiguration} instance.
   * @throws IOException Thrown if IO errors occurred during parsing.
   */
  public static AnnotationConfiguration parse(File annConfigFile) throws IOException {
    return parse(new BufferedInputStream(new FileInputStream(annConfigFile)));
  }
}
