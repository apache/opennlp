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

package opennlp.tools.formats.masc;

import java.util.ArrayList;
import java.util.List;

import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import opennlp.tools.util.Span;

/**
 * A class to parse the sentence segmentation stand-off annotation.
 */
class MascSentenceParser extends DefaultHandler {

  private final List<Span> sentenceAnchors = new ArrayList<>();

  public List<Span> getAnchors() {
    return sentenceAnchors;
  }

  @Override
  public void startElement(String uri, String localName, String qName, Attributes attributes)
      throws SAXException {

    try {
      // create a sentence and put it into the list of sentences
      if (qName.equalsIgnoreCase("region")) {
        String[] anchors = attributes.getValue("anchors").split(" ");

        int left = Integer.parseInt(anchors[0]);
        int right = Integer.parseInt(anchors[1]);

        sentenceAnchors.add(new Span(left, right));
      }

    } catch (Exception e) {
      throw new SAXException("Could not parse the sentence annotation file.");
    }
  }
}
