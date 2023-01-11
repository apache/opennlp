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

import java.util.HashMap;
import java.util.Map;
import java.util.Stack;

import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

/**
 * A class for parsing MASC's Penn tagging/tokenization stand-off annotation
 */
public class MascPennTagParser extends DefaultHandler {

  private final Map<Integer, int[]> tokenToQuarks = new HashMap<>();
  private final Map<Integer, String> tokenToTag = new HashMap<>();
  private final Map<Integer, String> tokenToBase = new HashMap<>();
  private final Stack<Integer> tokenStack = new Stack<>();
  private final Stack<Integer> tokenStackTag = new Stack<>();

  public Map<Integer, String> getTags() {
    return tokenToTag;
  }

  public Map<Integer, String> getBases() {
    return tokenToBase;
  }

  public Map<Integer, int[]> getTokenToQuarks() {
    return tokenToQuarks;
  }

  @Override
  public void startElement(String uri, String localName, String qName, Attributes attributes)
      throws SAXException {

    try {
      //get the link between region and Penn tag
      if (qName.equals("node")) {
        tokenStack.push(Integer.parseInt(attributes.getValue("xml:id")
            .replaceFirst("penn-n", "")));
      }

      if (qName.equals("link")) {
        if (tokenStack.isEmpty()) {
          throw new SAXException("The linking of tokens to quarks is broken.");
        }

        String[] targets = attributes.getValue("targets")
            .replace("seg-r", "").split(" ");

        int[] regions = new int[targets.length];
        for (int i = 0; i < targets.length; i++) {
          int region = Integer.parseInt(targets[i]);
          regions[i] = region;
        }
        tokenToQuarks.put(tokenStack.pop(), regions);
      }

      if (qName.equals("a")) {
        tokenStackTag.push(Integer.parseInt(attributes.getValue("ref")
            .replaceFirst("penn-n", "")));
      }

      if (qName.equals("f")) {
        String type = attributes.getValue("name");
        if (tokenStackTag.isEmpty()) {
          throw new SAXException("The linking of tokens to their tags/bases is broken.");
        }

        if (type.equals("msd")) {
          tokenToTag.put(tokenStackTag.peek(), attributes.getValue("value"));
        } else if (type.equals("base")) {
          tokenToBase.put(tokenStackTag.peek(), attributes.getValue("value"));
        }
      }

    } catch (Exception e) {
      throw new SAXException("Could not parse the Penn-POS annotation file.\n" + e.getMessage(), e);
    }
  }


  @Override
  public void endElement(String uri, String localName, String qName) throws SAXException {

    // we can forget the current node
    if (qName.equals("a")) {
      tokenStackTag.pop();
    }

  }
}
