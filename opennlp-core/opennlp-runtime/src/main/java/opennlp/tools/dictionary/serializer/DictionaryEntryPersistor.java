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

package opennlp.tools.dictionary.serializer;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParserFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.sax.SAXTransformerFactory;
import javax.xml.transform.sax.TransformerHandler;
import javax.xml.transform.stream.StreamResult;

import org.xml.sax.ContentHandler;
import org.xml.sax.InputSource;
import org.xml.sax.Locator;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.AttributesImpl;

import opennlp.tools.dictionary.Dictionary;
import opennlp.tools.util.InvalidFormatException;
import opennlp.tools.util.StringList;
import opennlp.tools.util.model.UncloseableInputStream;

/**
 * A persistor used by for reading and writing {@link Dictionary dictionaries}
 * of all kinds.
 *
 * @see Dictionary
 */
public class DictionaryEntryPersistor {
  
  private static final SAXParserFactory SAX_PARSER_FACTORY = SAXParserFactory.newInstance();
  private static final String SAX_FEATURE_NAMESPACES = "http://xml.org/sax/features/namespaces";

  // TODO: should check for invalid format, make it save
  private static class DictionaryContenthandler implements ContentHandler {

    private final EntryInserter mInserter;

    private boolean mIsInsideTokenElement;
    private boolean mIsCaseSensitiveDictionary;

    private final List<String> mTokenList = new LinkedList<>();

    private final StringBuilder token = new StringBuilder();

    private Attributes mAttributes;

    private DictionaryContenthandler(EntryInserter inserter) {
      mInserter = inserter;
      mIsCaseSensitiveDictionary = true;
    }

    /**
     * Not implemented.
     */
    @Override
    public void processingInstruction(String target, String data)
        throws SAXException {
    }

    /**
     * Not implemented.
     */
    @Override
    public void startDocument() throws SAXException {
    }

    @Override
    public void startElement(String uri, String localName, String qName,
        org.xml.sax.Attributes atts) throws SAXException {
      if (DICTIONARY_ELEMENT.equals(localName)) {

        mAttributes = new Attributes();

        for (int i = 0; i < atts.getLength(); i++) {
          mAttributes.setValue(atts.getLocalName(i), atts.getValue(i));
        }
        /* get the attribute here ... */
        if (mAttributes.getValue(ATTRIBUTE_CASE_SENSITIVE) != null) {
          mIsCaseSensitiveDictionary = Boolean.parseBoolean(mAttributes.getValue(ATTRIBUTE_CASE_SENSITIVE));
        }
        mAttributes = null;
      }
      else if (ENTRY_ELEMENT.equals(localName)) {

        mAttributes = new Attributes();

        for (int i = 0; i < atts.getLength(); i++) {
          mAttributes.setValue(atts.getLocalName(i), atts.getValue(i));
        }
      }
      else if (TOKEN_ELEMENT.equals(localName)) {
        mIsInsideTokenElement = true;
      }
    }

    @Override
    public void characters(char[] ch, int start, int length)
        throws SAXException {
      if (mIsInsideTokenElement) {
        token.append(ch, start, length);
      }
    }

    /**
     * Creates the Profile object after processing is complete
     * and switches mIsInsideNgramElement flag.
     */
    @Override
    public void endElement(String uri, String localName, String qName)
        throws SAXException {

      if (TOKEN_ELEMENT.equals(localName)) {
        mTokenList.add(token.toString().trim());
        token.setLength(0);
        mIsInsideTokenElement = false;
      }
      else if (ENTRY_ELEMENT.equals(localName)) {

        String[] tokens = mTokenList.toArray(
                new String[0]);

        Entry entry = new Entry(new StringList(tokens), mAttributes);

        try {
          mInserter.insert(entry);
        } catch (InvalidFormatException e) {
          throw new SAXException("Invalid dictionary format!", e);
        }

        mTokenList.clear();
        mAttributes = null;
      }
    }

    /**
     * Not implemented.
     */
    @Override
    public void endDocument() throws SAXException {
    }

    /**
     * Not implemented.
     */
    @Override
    public void endPrefixMapping(String prefix) throws SAXException {
    }

    /**
     * Not implemented.
     */
    @Override
    public void ignorableWhitespace(char[] ch, int start, int length)
        throws SAXException {
    }

    /**
     * Not implemented.
     */
    @Override
    public void setDocumentLocator(Locator locator) {
    }

    /**
     * Not implemented.
     */
    @Override
    public void skippedEntity(String name) throws SAXException {
    }

    /**
     * Not implemented.
     */
    @Override
    public void startPrefixMapping(String prefix, String uri)
        throws SAXException {
    }
  }

  private static final String DICTIONARY_ELEMENT = "dictionary";
  private static final String ENTRY_ELEMENT = "entry";
  private static final String TOKEN_ELEMENT = "token";
  private static final String ATTRIBUTE_CASE_SENSITIVE = "case_sensitive";

  /**
   * Creates {@link Entry}s from the given {@link InputStream} and
   * forwards these {@link Entry}s to the {@link EntryInserter}.
   * <p>
   * <b>Note:</b>
   * After creation is finished the provided {@link InputStream} is closed.
   *
   * @param in The open {@link InputStream} to read entries from.
   * @param inserter inserter to forward entries to
   *
   * @return The {@code isCaseSensitive} attribute of a {@link Dictionary}.
   *
   * @throws IOException Thrown if IO errors occurred.
   * @throws InvalidFormatException Thrown if parameters were invalid.
   */
  public static boolean create(InputStream in, EntryInserter inserter)
      throws IOException {

    DictionaryContenthandler profileContentHandler = new DictionaryContenthandler(inserter);

    XMLReader xmlReader;
    try {
      xmlReader = SAX_PARSER_FACTORY.newSAXParser().getXMLReader();
      // Note:
      // There is a compatibility problem here: JAXP default is false while SAX 2 default is true!
      // OpenNLP requires it activated!
      xmlReader.setFeature(SAX_FEATURE_NAMESPACES, true);
      xmlReader.setContentHandler(profileContentHandler);
      xmlReader.parse(new InputSource(new UncloseableInputStream(in)));
    }
    catch (ParserConfigurationException | SAXException e) {
      throw new InvalidFormatException("The profile data stream has " +
          "an invalid format!", e);
    }
    return profileContentHandler.mIsCaseSensitiveDictionary;
  }

  /**
   * Serializes the given entries to the given {@link OutputStream}.
   * <p>
   * <b>Note:</b>
   * After the serialization is finished the provided
   * {@link OutputStream} remains open.
   *
   * @param out The {@link OutputStream} to serialize to.
   * @param entries The {@link Entry entries} to serialize.
   * @param casesensitive Indicates if the written dictionary should be
   *                      case-sensitive, or not.
   *
   * @throws IOException Thrown if IO errors occurred.
   * @throws InvalidFormatException Thrown if parameters were invalid.
   */
  public static void serialize(OutputStream out, Iterator<Entry> entries,
      boolean casesensitive) throws IOException {
    StreamResult streamResult = new StreamResult(out);
    SAXTransformerFactory tf = (SAXTransformerFactory)
        SAXTransformerFactory.newInstance();

    TransformerHandler hd;
    try {
      hd = tf.newTransformerHandler();
    } catch (TransformerConfigurationException e) {
      throw new InvalidFormatException("The Transformer configuration must be valid!");
    }

    Transformer serializer = hd.getTransformer();
    serializer.setOutputProperty(OutputKeys.ENCODING, StandardCharsets.UTF_8.name());
    serializer.setOutputProperty(OutputKeys.INDENT, "yes");

    hd.setResult(streamResult);

    try {
      hd.startDocument();

      AttributesImpl dictionaryAttributes = new AttributesImpl();
      dictionaryAttributes.addAttribute("", "", ATTRIBUTE_CASE_SENSITIVE,
          "", String.valueOf(casesensitive));
      hd.startElement("", "", DICTIONARY_ELEMENT, dictionaryAttributes);

      while (entries.hasNext()) {
        Entry entry = entries.next();
        serializeEntry(hd, entry);
      }

      hd.endElement("", "", DICTIONARY_ELEMENT);
      hd.endDocument();
    }
    catch (SAXException e) {
      throw new IOException("Error during serialization: " + e.getMessage(), e);
    }
  }

  private static void serializeEntry(TransformerHandler hd, Entry entry)
      throws SAXException {

    AttributesImpl entryAttributes = new AttributesImpl();

    for (Iterator<String> it = entry.attributes().iterator(); it.hasNext();) {
      String key = it.next();

      entryAttributes.addAttribute("", "", key,
          "", entry.attributes().getValue(key));
    }

    hd.startElement("", "", ENTRY_ELEMENT, entryAttributes);

    StringList tokens = entry.tokens();

    for (String token : tokens) {

      hd.startElement("", "", TOKEN_ELEMENT, new AttributesImpl());

      hd.characters(token.toCharArray(), 0, token.length());

      hd.endElement("", "", TOKEN_ELEMENT);
    }

    hd.endElement("", "", ENTRY_ELEMENT);
  }
}
