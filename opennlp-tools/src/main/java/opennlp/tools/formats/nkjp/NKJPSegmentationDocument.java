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
import java.util.LinkedHashMap;
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

import opennlp.tools.util.Span;
import opennlp.tools.util.XmlUtil;

public class NKJPSegmentationDocument {

  public static class Pointer {
    String doc;
    String id;
    int offset;
    int length;
    boolean space_after;

    public Pointer(String doc, String id, int offset, int length, boolean space_after) {
      this.doc = doc;
      this.id = id;
      this.offset = offset;
      this.length = length;
      this.space_after = space_after;
    }

    public Span toSpan() {
      return new Span(this.offset, this.offset + this.length);
    }

    @Override
    public String toString() {
      return doc + "#string-range(" + id + "," + Integer.toString(offset)
          + "," + Integer.toString(length) + ")";
    }
  }

  public Map<String, Map<String, Pointer>> getSegments() {
    return segments;
  }

  Map<String, Map<String, Pointer>> segments;

  NKJPSegmentationDocument() {
    this.segments = new LinkedHashMap<>();
  }

  NKJPSegmentationDocument(Map<String, Map<String, Pointer>> segments) {
    this();
    this.segments = segments;
  }

  public static NKJPSegmentationDocument parse(InputStream is) throws IOException {

    Map<String, Map<String, Pointer>> sentences = new LinkedHashMap<>();

    try {
      DocumentBuilder docBuilder = XmlUtil.createDocumentBuilder();;
      Document doc = docBuilder.parse(is);

      XPathFactory xPathfactory = XPathFactory.newInstance();
      XPath xpath = xPathfactory.newXPath();

      final XPathExpression SENT_NODES = xpath.compile("/teiCorpus/TEI/text/body/p/s");
      final XPathExpression SEG_NODES = xpath.compile("./seg|./choice");
      final XPathExpression SEG_NODES_ONLY = xpath.compile("./seg");

      NodeList nl = (NodeList) SENT_NODES.evaluate(doc, XPathConstants.NODESET);

      for (int i = 0; i < nl.getLength(); i++) {
        Node sentnode = nl.item(i);

        String sentid = null;
        if (sentnode.getAttributes().getNamedItem("xml:id") != null) {
          sentid = sentnode.getAttributes().getNamedItem("xml:id").getTextContent();
        }

        Map<String, Pointer> segments = new LinkedHashMap<>();
        NodeList segnl = (NodeList) SEG_NODES.evaluate(sentnode, XPathConstants.NODESET);

        for (int j = 0; j < segnl.getLength(); j++) {
          Node n = segnl.item(j);
          if (n.getNodeName().equals("seg")) {
            String segid = xmlID(n);
            Pointer pointer = fromSeg(n);
            segments.put(segid, pointer);
          } else if (n.getNodeName().equals("choice")) {

            NodeList choices = n.getChildNodes();

            for (int k = 0; k < choices.getLength(); k++) {
              if (choices.item(k).getNodeName().equals("nkjp:paren")) {
                if (!checkRejectedParen(choices.item(k))) {
                  NodeList paren_segs = (NodeList) SEG_NODES_ONLY.evaluate(choices.item(k),
                      XPathConstants.NODESET);

                  for (int l = 0; l < paren_segs.getLength(); l++) {
                    String segid = xmlID(paren_segs.item(l));
                    Pointer pointer = fromSeg(paren_segs.item(l));
                    segments.put(segid, pointer);
                  }
                }
              } else if (choices.item(k).getNodeName().equals("seg")) {
                if (!checkRejected(choices.item(k))) {
                  String segid = xmlID(choices.item(k));
                  Pointer pointer = fromSeg(choices.item(k));
                  segments.put(segid, pointer);
                }
              }
            }
          }
        }

        sentences.put(sentid, segments);
      }

    } catch (SAXException | XPathExpressionException | IOException e) {
      throw new IOException("Failed to parse NKJP document", e);
    }

    return new NKJPSegmentationDocument(sentences);
  }

  static boolean checkRejected(Node n) {
    if (n.getAttributes() == null) {
      return false;
    }
    if (n.getAttributes().getNamedItem("nkjp:rejected") == null) {
      return false;
    }
    String rejected = n.getAttributes().getNamedItem("nkjp:rejected").getTextContent();
    return rejected.equals("true");
  }

  static boolean checkRejectedParen(Node n) {
    if (n.getChildNodes().getLength() == 0) {
      return false;
    } else {
      for (int i = 0; i < n.getChildNodes().getLength(); i++) {
        Node m = n.getChildNodes().item(i);
        if (m.getNodeName().equals("seg")) {
          if (!checkRejected(m)) {
            return false;
          }
        }
      }
      return true;
    }
  }

  static String xmlID(Node n) throws IOException {
    if (n.getAttributes() == null || n.getAttributes().getLength() < 1) {
      throw new IOException("Missing required attributes");
    }

    String id = null;
    if (n.getAttributes().getNamedItem("xml:id") != null) {
      id = n.getAttributes().getNamedItem("xml:id").getTextContent();
    }

    if (id == null) {
      throw new IOException("Missing xml:id attribute");
    }

    return id;
  }

  static Pointer fromSeg(Node n) throws IOException {
    if (n.getAttributes() == null || n.getAttributes().getLength() < 2) {
      throw new IOException("Missing required attributes");
    }

    String ptr = null;
    if (n.getAttributes().getNamedItem("corresp") != null) {
      ptr = n.getAttributes().getNamedItem("corresp").getTextContent();
    }
    String spacing = "";
    if (n.getAttributes().getNamedItem("nkjp:nps") != null) {
      spacing = n.getAttributes().getNamedItem("nkjp:nps").getTextContent();
    }

    if (ptr == null) {
      throw new IOException("Missing required attribute");
    }

    boolean space_after = (ptr.equals("yes"));

    if (!ptr.contains("#") || !ptr.contains("(") || ptr.charAt(ptr.length() - 1) != ')') {
      throw new IOException("String " + ptr + " does not appear to be a valid NKJP corresp attribute");
    }

    int docend = ptr.indexOf('#');
    String document = ptr.substring(0, docend);

    int pointer_start = ptr.indexOf('(') + 1;
    String[] pieces = ptr.substring(pointer_start, ptr.length() - 1).split(",");

    if (pieces.length < 3 || pieces.length > 4) {
      throw new IOException("String " + ptr + " does not appear to be a valid NKJP corresp attribute");
    }

    String docid = pieces[0];
    int offset = 0;
    int length = 0;
    if (pieces.length == 3) {
      offset = Integer.parseInt(pieces[1]);
      length = Integer.parseInt(pieces[2]);
    } else {
      int os1 = Integer.parseInt(pieces[1]);
      int os2 = Integer.parseInt(pieces[2]);
      offset = (os1 * 1000) + os2;
      length = Integer.parseInt(pieces[3]);
    }

    return new Pointer(document, docid, offset, length, space_after);
  }

  static NKJPSegmentationDocument parse(File file) throws IOException {
    try (InputStream in = new FileInputStream(file)) {
      return parse(in);
    }
  }
}
