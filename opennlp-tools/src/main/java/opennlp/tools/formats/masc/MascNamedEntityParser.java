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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

/**
 * A class to process the MASC Named entity stand-off annotation file
 */
public class MascNamedEntityParser extends DefaultHandler {

  private static final Logger logger = LoggerFactory.getLogger(MascNamedEntityParser.class);

  private final Map<Integer, String> entityIDtoEntityType = new HashMap<>();
  private final Map<Integer, List<Integer>> entityIDsToTokens = new HashMap<>();
  private final Map<Integer, String> tokenToEntity = new HashMap<>();

  public Map<Integer, String> getEntityIDtoEntityType() {
    return entityIDtoEntityType;
  }

  public Map<Integer, List<Integer>> getEntityIDsToTokens() {
    return entityIDsToTokens;
  }

  @Override
  public void startElement(String uri, String localName, String qName, Attributes attributes)
      throws SAXException {

    try {
      if (qName.equals("a")) {
        int entityID = Integer.parseInt(
            attributes.getValue("ref").replaceFirst("ne-n", ""));
        String label = attributes.getValue("label");
        if (entityIDtoEntityType.containsKey(entityID)) {
          throw new SAXException("Multiple labels for one named entity");
        } else {
          entityIDtoEntityType.put(entityID, label);
        }
      }

      if (qName.equals("edge")) {
        int entityID = Integer.parseInt(
            attributes.getValue("from").replaceFirst("ne-n", ""));
        int tokenID = Integer.parseInt(
            attributes.getValue("to").replaceFirst("penn-n", ""));

        if (!entityIDsToTokens.containsKey(entityID)) {
          List<Integer> tokens = new ArrayList<>();
          tokens.add(tokenID);
          entityIDsToTokens.put(entityID, tokens);
        } else {
          entityIDsToTokens.get(entityID).add(tokenID);
        }

/*      Not sure what to do with this. There might be multiple entity links to one token.
       E.g. Colorado will be one token with the entities "city" and "province".
       For now, we'll only raise alarm when one TokenID should be assigned
       to different top-level labels, e.g. person & location (since we are dropping the low-level
       annotations at the moment). To make this work in OpenNLP (does not allow overlaps), we'll
       keep only the first named entity type.
 */
        //todo: Do we want to give the user control over which types have priority?
        String type = entityIDtoEntityType.get(entityID);
        if (tokenToEntity.containsKey(tokenID) && !type.equals(tokenToEntity.get(tokenID))) {
          logger.warn("One token assigned to different named entity types.\n" +
              "\tPenn-TokenID: {}\n\tToken types: \"{}\", \"{}\"\n\tKeeping only " + "\"type\"",
              tokenID, type, tokenToEntity.get(tokenID));
        }
        tokenToEntity.put(tokenID, type);
      }

    } catch (Exception e) {
      throw new SAXException("Could not parse the named entity annotation file.\n" +
          e.getMessage(), e);
    }
  }


}
