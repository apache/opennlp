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
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import opennlp.tools.tokenize.WhitespaceTokenizer;

public class AnnotationConfiguration {

  public static final String SPAN_TYPE = "Span";
  public static final String ENTITY_TYPE = "Entity";
  public static final String RELATION_TYPE = "Relation";
  public static final String ATTRIBUTE_TYPE = "Attribute";
  public static final String EVENT_TYPE = "Event";

  private final Map<String, String> typeToClassMap;

  public AnnotationConfiguration(Map<String, String> typeToClassMap) {

    this.typeToClassMap = Collections.unmodifiableMap(new HashMap<>(typeToClassMap));
  }

  public String getTypeClass(String type) {
    return typeToClassMap.get(type);
  }


  public static AnnotationConfiguration parse(InputStream in) throws IOException {
    Map<String, String> typeToClassMap = new HashMap<>();

    BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));

    // Note: This only supports entities and relations section
    String line;
    String sectionType = null;

    while ((line = reader.readLine()) != null) {
      line = line.trim();

      if (!line.isEmpty()) {
        if (!line.startsWith("#")) {
          if (line.startsWith("[") && line.endsWith("]")) {
            sectionType = line.substring(line.indexOf('[') + 1, line.indexOf(']'));
          }
          else {
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

    return new AnnotationConfiguration(typeToClassMap);
  }

  public static AnnotationConfiguration parse(File annConfigFile) throws IOException {
    try (InputStream in = new BufferedInputStream(new FileInputStream(annConfigFile))) {
      return parse(in);
    }
  }
}
