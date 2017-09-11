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

package opennlp.tools.formats.nkjp;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import opennlp.tools.util.XmlUtil;

public class NKJPTextDocument {

  Map<String, String> divtypes;

  Map<String, Map<String, Map<String, String>>> texts;

  NKJPTextDocument() {
    divtypes = new HashMap<>();
    texts = new HashMap<>();
  }

  NKJPTextDocument(Map<String, String> divtypes, Map<String, Map<String, Map<String, String>>> texts) {
    this();
    this.divtypes = divtypes;
    this.texts = texts;
  }

  public static NKJPTextDocument parse(InputStream is) throws IOException {
    Map<String, String> divtypes = new HashMap<>();
    Map<String, Map<String, Map<String, String>>> texts = new HashMap<>();

    try {
      DocumentBuilder docBuilder = XmlUtil.createDocumentBuilder();;
      Document doc = docBuilder.parse(is);

      XPathFactory xPathfactory = XPathFactory.newInstance();
      XPath xpath = xPathfactory.newXPath();

      final XPathExpression TEXT_NODES_EXAMPLE = xpath.compile("/teiCorpus/TEI/text/group/text");
      final XPathExpression TEXT_NODES_SAMPLE = xpath.compile("/teiCorpus/TEI/text");
      final XPathExpression DIV_NODES = xpath.compile("./body/div");
      final XPathExpression PARA_NODES = xpath.compile("./p|./ab");

      doc.getDocumentElement().normalize();
      String root = doc.getDocumentElement().getNodeName();

      if (!root.equalsIgnoreCase("teiCorpus")) {
        throw new IOException("Expected root node " + root);
      }

      String current_text = "";
      NodeList textnl = (NodeList) TEXT_NODES_EXAMPLE.evaluate(doc, XPathConstants.NODESET);
      if (textnl.getLength() == 0) {
        textnl = (NodeList) TEXT_NODES_SAMPLE.evaluate(doc, XPathConstants.NODESET);
      }

      for (int i = 0; i < textnl.getLength(); i++) {
        Node textnode = textnl.item(i);
        current_text = attrib(textnode, "xml:id", true);

        Map<String, Map<String, String>> current_divs = new HashMap<>();
        NodeList divnl = (NodeList) DIV_NODES.evaluate(textnode, XPathConstants.NODESET);
        for (int j = 0; j < divnl.getLength(); j++) {
          Node divnode = divnl.item(j);
          String divtype = attrib(divnode, "type", false);
          String divid = attrib(divnode, "xml:id", true);
          divtypes.put(divid, divtype);

          Map<String, String> current_paras = new HashMap<>();
          NodeList paranl = (NodeList) PARA_NODES.evaluate(divnode, XPathConstants.NODESET);

          for (int k = 0; k < paranl.getLength(); k++) {
            Node pnode = paranl.item(k);
            String pid = attrib(pnode, "xml:id", true);

            if (pnode.getChildNodes().getLength() != 1
                && !pnode.getFirstChild().getNodeName().equals("#text")) {
              throw new IOException("Unexpected content in p element " + pid);
            }

            String ptext = pnode.getTextContent();
            current_paras.put(pid, ptext);
          }

          current_divs.put(divid, current_paras);
        }

        texts.put(current_text, current_divs);
      }

    } catch (SAXException | XPathExpressionException | IOException e) {
      throw new IOException("Failed to parse NKJP document", e);
    }
    return new NKJPTextDocument(divtypes, texts);
  }

  static NKJPTextDocument parse(File file) throws IOException {
    try (InputStream in = new FileInputStream(file)) {
      return parse(in);
    }
  }

  Map<String, String> getDivtypes() {
    return Collections.unmodifiableMap(this.divtypes);
  }

  Map<String, Map<String, Map<String, String>>> getTexts() {
    return Collections.unmodifiableMap(this.texts);
  }

  /**
   * Segmentation etc. is done only in relation to the paragraph,
   * which are unique within a document. This is to simplify
   * working with the paragraphs within the document
   * @return a map of paragaph IDs and their text
   */
  Map<String, String> getParagraphs() {
    Map<String, String> paragraphs = new HashMap<>();
    for (String dockey : texts.keySet()) {
      for (String divkey : texts.get(dockey).keySet()) {
        for (String pkey : texts.get(dockey).get(divkey).keySet()) {
          paragraphs.put(pkey, texts.get(dockey).get(divkey).get(pkey));
        }
      }
    }
    return paragraphs;
  }

  /**
   * Helper method to get the value of an attribute
   * @param n The node being processed
   * @param attrib The name of the attribute
   * @param required Whether or not the attribute is required
   * @return The value of the attribute, or null if not required and not present
   * @throws Exception
   */
  private static String attrib(Node n, String attrib, boolean required) throws IOException {
    if (required && (n.getAttributes() == null || n.getAttributes().getLength() == 0)) {
      throw new IOException("Missing required attributes in node " + n.getNodeName());
    }
    if (n.getAttributes().getNamedItem(attrib) != null) {
      return n.getAttributes().getNamedItem(attrib).getTextContent();
    } else {
      if (required) {
        throw new IOException("Required attribute \"" + attrib + "\" missing in node " + n.getNodeName());
      } else {
        return null;
      }
    }
  }
}
