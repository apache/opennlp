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

package opennlp.tools.formats.muc;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.Stack;

import opennlp.tools.namefind.NameSample;
import opennlp.tools.tokenize.Tokenizer;
import opennlp.tools.util.InvalidFormatException;
import opennlp.tools.util.Span;

public class MucNameContentHandler extends SgmlParser.ContentHandler {

  private static final String ENTITY_ELEMENT_NAME = "ENAMEX";
  private static final String TIME_ELEMENT_NAME = "TIMEX";
  private static final String NUM_ELEMENT_NAME = "NUMEX";

  private static final Set<String> NAME_ELEMENT_NAMES;

  private static final Set<String> EXPECTED_TYPES;

  static {
    Set<String> types = new HashSet<>();

    types.add("PERSON");
    types.add("ORGANIZATION");
    types.add("LOCATION");
    types.add("DATE");
    types.add("TIME");
    types.add("MONEY");
    types.add("PERCENT");

    EXPECTED_TYPES = Collections.unmodifiableSet(types);

    Set<String> nameElements = new HashSet<>();
    nameElements.add(ENTITY_ELEMENT_NAME);
    nameElements.add(TIME_ELEMENT_NAME);
    nameElements.add(NUM_ELEMENT_NAME);
    NAME_ELEMENT_NAMES = Collections.unmodifiableSet(nameElements);
  }

  private final Tokenizer tokenizer;
  private final List<NameSample> storedSamples;

  private boolean isInsideContentElement = false;
  private final List<String> text = new ArrayList<>();
  private boolean isClearAdaptiveData = false;
  private final Stack<Span> incompleteNames = new Stack<>();

  private List<Span> names = new ArrayList<>();

  public MucNameContentHandler(Tokenizer tokenizer,
      List<NameSample> storedSamples) {
    this.tokenizer = tokenizer;
    this.storedSamples = storedSamples;
  }

  @Override
  public void startElement(String name, Map<String, String> attributes)
      throws InvalidFormatException {

    if (MucElementNames.DOC_ELEMENT.equals(name)) {
      isClearAdaptiveData = true;
    }

    if (MucElementNames.CONTENT_ELEMENTS.contains(name)) {
      isInsideContentElement = true;
    }

    if (NAME_ELEMENT_NAMES.contains(name)) {

      String nameType = attributes.get("TYPE");

      if (!EXPECTED_TYPES.contains(nameType)) {
        throw new InvalidFormatException("Unknown timex, numex or namex type: "
            + nameType + ", expected one of " + EXPECTED_TYPES);
      }

      incompleteNames.add(new Span(text.size(), text.size(), nameType.toLowerCase(Locale.ENGLISH)));
    }
  }

  @Override
  public void characters(CharSequence chars) {
    if (isInsideContentElement) {
      String tokens [] = tokenizer.tokenize(chars.toString());
      text.addAll(Arrays.asList(tokens));
    }
  }

  @Override
  public void endElement(String name) {

    if (NAME_ELEMENT_NAMES.contains(name)) {
      Span nameSpan = incompleteNames.pop();
      nameSpan = new Span(nameSpan.getStart(), text.size(), nameSpan.getType());
      names.add(nameSpan);
    }

    if (MucElementNames.CONTENT_ELEMENTS.contains(name)) {
      storedSamples.add(new NameSample(text.toArray(new String[text.size()]),
          names.toArray(new Span[names.size()]), isClearAdaptiveData));

      if (isClearAdaptiveData) {
        isClearAdaptiveData = false;
      }

      text.clear();
      names.clear();
      isInsideContentElement = false;
    }
  }
}
