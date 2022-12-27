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

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;

import opennlp.tools.formats.AbstractFormatTest;

public abstract class AbstractBratTest extends AbstractFormatTest {

  protected static final String BRAT_TYPE_PERSON = "Person";
  protected static final String BRAT_TYPE_LOCATION = "Location";
  protected static final String BRAT_TYPE_ORGANIZATION = "Organization";
  protected static final String BRAT_TYPE_DATE = "Date";
  protected static final String BRAT_TYPE_RELATED = "Related";
  protected static final String BRAT_TYPE_ANNOTATION = "#AnnotationNote";

  protected final Map<String, String> typeToClassMap = new HashMap<>();

  protected File directory;

  @BeforeEach
  public void setup() throws IOException {
    directory = getBratDir();
    Assertions.assertNotNull(directory);

    typeToClassMap.put(BRAT_TYPE_PERSON, AnnotationConfiguration.ENTITY_TYPE);
    typeToClassMap.put(BRAT_TYPE_LOCATION, AnnotationConfiguration.ENTITY_TYPE);
    typeToClassMap.put(BRAT_TYPE_ORGANIZATION, AnnotationConfiguration.ENTITY_TYPE);
    typeToClassMap.put(BRAT_TYPE_DATE, AnnotationConfiguration.ENTITY_TYPE);
  }

  private String getDirectoryAsString() {
    return getResource("brat/").getFile();
  }

  protected File getBratDir() {
    return new File(getDirectoryAsString());
  }
}
