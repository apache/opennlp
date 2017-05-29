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

package opennlp.tools.formats.irishsentencebank;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.StringBuilder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import opennlp.tools.tokenize.TokenSample;
import opennlp.tools.util.Span;

/**
 * A structure to hold an Irish Sentence Bank document, which is a collection
 * of tokenized sentences.
 * <p>
 * The sentence bank can be downloaded from, and is described
 * <a href="http://www.lexiconista.com/datasets/sentencebank-ga/">here</a>
 */
public class IrishSentenceBankDocument {

  public static class IrishSentenceBankFlex {
    String surface;
    String[] flex;
    public String getSurface() {
      return surface;
    }
    public String[] getFlex() {
      return flex;
    }
    public IrishSentenceBankFlex(String sf, String[] fl) {
      this.surface = sf;
      this.flex = fl;
    }
  }

  public static class IrishSentenceBankSentence {
    private String source;
    private String translation;
    private String original;
    private Span[] tokens;
    private IrishSentenceBankFlex[] flex;
    public String getSource() {
      return source;
    }
    public String getTranslation() {
      return translation;
    }
    public String getOriginal() {
      return original;
    }
    public Span[] getTokens() {
      return tokens;
    }
    public IrishSentenceBankFlex[] getFlex() {
      return flex;
    }
    public TokenSample getTokenSample() {
      return new TokenSample(original, tokens);
    }
    public IrishSentenceBankSentence(String src, String trans, String orig, 
                                     Span[] toks, IrishSentenceBankFlex[] flx) {
      this.source = src;
      this.translation = trans;
      this.original = orig;
      this.tokens = toks;
      this.flex = flx;
    }
  }

  private List<IrishSentenceBankSentence> sentences;

  public IrishSentenceBankDocument() {
    sentences = new ArrayList<IrishSentenceBankSentence>();
  }

  public void add(IrishSentenceBankSentence sent) {
    this.sentences.add(sent);
  }

  public List<IrishSentenceBankSentence> getSentences() {
    return Collections.unmodifiableList(sentences);
  }

  /**
   * Helper to adjust the span of punctuation tokens: ignores spaces to the left of the string
   * @param s the string to check
   * @param start the offset of the start of the string
   * @return the offset adjusted to ignore spaces to the left
   */
  private static int advanceLeft(String s, int start) {
    int ret = start;
    for (char c : s.toCharArray()) {
      if (c == ' ') {
        ret++;
      } else {
        return ret;
      }
    }
    return ret;
  }

  /**
   * Helper to adjust the span of punctuation tokens: ignores spaces to the right of the string
   * @param s the string to check
   * @param start the offset of the start of the string
   * @return the offset of the end of the string, adjusted to ignore spaces to the right
   */
  private static int advanceRight(String s, int start) {
    int end = s.length() - 1;
    int ret = start + end + 1;
    for (int i = end; i > 0; i--) {
      if (s.charAt(i) == ' ') {
        ret--;
      } else {
        return ret;
      }
    }
    return ret;
  }

  public static IrishSentenceBankDocument parse(InputStream is) throws IOException {
    IrishSentenceBankDocument document = new IrishSentenceBankDocument();

    try {
      DocumentBuilderFactory docBuilderFactory = DocumentBuilderFactory.newInstance();
      DocumentBuilder docBuilder = docBuilderFactory.newDocumentBuilder();
      Document doc = docBuilder.parse(is);

      String root = doc.getDocumentElement().getNodeName();
      if (!root.equalsIgnoreCase("sentences")) {
        throw new IOException("Expected root node " + root);
      }

      NodeList nl = doc.getDocumentElement().getChildNodes();
      for (int i = 0; i < nl.getLength(); i++) {
        Node sentnode = nl.item(i);
        if (sentnode.getNodeName().equals("sentence")) {
          String src = sentnode.getAttributes().getNamedItem("source").getNodeValue();
          String trans = "";
          Map<Integer, String> toks = new HashMap<>();
          Map<Integer, List<String>> flx = new HashMap<>();
          List<Span> spans = new ArrayList<>();
          NodeList sentnl = sentnode.getChildNodes();
          int flexes = 1;
          StringBuilder orig = new StringBuilder();

          for (int j = 0; j < sentnl.getLength(); j++) {
            final String name = sentnl.item(j).getNodeName();
            switch (name) {
              case "flex":
                String slottmpa = sentnl.item(j).getAttributes().getNamedItem("slot").getNodeValue();
                Integer flexslot = Integer.parseInt(slottmpa);
                if (flexslot > flexes) {
                  flexes = flexslot;
                }

                flx.computeIfAbsent(flexslot, k -> new ArrayList<>());
                String tkn = sentnl.item(j).getAttributes().getNamedItem("lemma").getNodeValue();
                flx.get(flexslot).add(tkn);
                break;

              case "translation":
                trans = sentnl.item(j).getFirstChild().getTextContent();
                break;

              case "original":
                int last = 0;
                NodeList orignl = sentnl.item(j).getChildNodes();
                for (int k = 0; k < orignl.getLength(); k++) {
                  switch (orignl.item(k).getNodeName()) {
                    case "token":
                      String tmptok = orignl.item(k).getFirstChild().getTextContent();
                      spans.add(new Span(last, last + tmptok.length()));

                      String slottmpb = orignl.item(k).getAttributes().getNamedItem("slot").getNodeValue();
                      Integer tokslot = Integer.parseInt(slottmpb);
                      if (tokslot > flexes) {
                        flexes = tokslot;
                      }

                      toks.put(tokslot, tmptok);
                      orig.append(tmptok);
                      last += tmptok.length();
                      break;

                    case "#text":
                      String tmptxt = orignl.item(k).getTextContent();
                      orig.append(tmptxt);

                      if (!" ".equals(tmptxt)) {
                        spans.add(new Span(advanceLeft(tmptxt, last), advanceRight(tmptxt, last)));
                      }

                      last += tmptxt.length();
                      break;

                    default:
                      throw new IOException("Unexpected node: " + orignl.item(k).getNodeName());
                  }
                }
                break;

              case "#text":
              case "#comment":
                break;

              default:
                throw new IOException("Unexpected node: " + name);
            }
          }
          IrishSentenceBankFlex[] flexa = new IrishSentenceBankFlex[flexes];
          for (Integer flexidx : toks.keySet()) {
            String left = toks.get(flexidx);
            if (flx.get(flexidx) == null) {
              flexa = null;
              break;
            }
            int rsize = flx.get(flexidx).size();
            String[] right = new String[rsize];
            right = flx.get(flexidx).toArray(right);
            flexa[flexidx - 1] = new IrishSentenceBankFlex(left, right);
          }

          Span[] spanout = new Span[spans.size()];
          spanout = spans.toArray(spanout);
          document.add(new IrishSentenceBankSentence(src, trans, orig.toString(), spanout, flexa));
        } else if (!sentnode.getNodeName().equals("#text") && !sentnode.getNodeName().equals("#comment")) {
          throw new IOException("Unexpected node: " + sentnode.getNodeName());
        }
      }
      return document;
    } catch (ParserConfigurationException e) {
      throw new IllegalStateException(e);
    } catch (SAXException e) {
      throw new IOException("Failed to parse IrishSentenceBank document", e);
    }
  }

  static IrishSentenceBankDocument parse(File file) throws IOException {
    try (InputStream in = new FileInputStream(file)) {
      return parse(in);
    }
  }
}
