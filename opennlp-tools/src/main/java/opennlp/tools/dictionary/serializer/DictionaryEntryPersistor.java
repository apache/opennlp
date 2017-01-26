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
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

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
import org.xml.sax.helpers.XMLReaderFactory;

import opennlp.tools.util.InvalidFormatException;
import opennlp.tools.util.StringList;
import opennlp.tools.util.model.UncloseableInputStream;

/**
 * This class is used by for reading and writing dictionaries of all kinds.
 */
public class DictionaryEntryPersistor {

  // TODO: should check for invalid format, make it save
  private static class DictionaryContenthandler implements ContentHandler {

    private EntryInserter mInserter;

    //    private boolean mIsInsideDictionaryElement;
    //    private boolean mIsInsideEntryElement;
    private boolean mIsInsideTokenElement;
    private boolean mIsCaseSensitiveDictionary;

    private List<String> mTokenList = new LinkedList<>();

    private StringBuilder token = new StringBuilder();

    private Attributes mAttributes;

    private DictionaryContenthandler(EntryInserter inserter) {
      mInserter = inserter;
      mIsCaseSensitiveDictionary = true;
    }

    /**
     * Not implemented.
     */
    public void processingInstruction(String target, String data)
        throws SAXException {
    }

    /**
     * Not implemented.
     */
    public void startDocument() throws SAXException {
    }

    public void startElement(String uri, String localName, String qName,
        org.xml.sax.Attributes atts) throws SAXException {
      if (DICTIONARY_ELEMENT.equals(localName)) {

        mAttributes = new Attributes();

        for (int i = 0; i < atts.getLength(); i++) {
          mAttributes.setValue(atts.getLocalName(i), atts.getValue(i));
        }
        /* get the attribute here ... */
        if (mAttributes.getValue(ATTRIBUTE_CASE_SENSITIVE) != null) {
          mIsCaseSensitiveDictionary = Boolean.valueOf(mAttributes.getValue(ATTRIBUTE_CASE_SENSITIVE));
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
    public void endElement(String uri, String localName, String qName)
        throws SAXException {

      if (TOKEN_ELEMENT.equals(localName)) {
        mTokenList.add(token.toString().trim());
        token.setLength(0);
        mIsInsideTokenElement = false;
      }
      else if (ENTRY_ELEMENT.equals(localName)) {

        String[] tokens = mTokenList.toArray(
            new String[mTokenList.size()]);

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
    public void endDocument() throws SAXException {
    }

    /**
     * Not implemented.
     */
    public void endPrefixMapping(String prefix) throws SAXException {
    }

    /**
     * Not implemented.
     */
    public void ignorableWhitespace(char[] ch, int start, int length)
        throws SAXException {
    }

    /**
     * Not implemented.
     */
    public void setDocumentLocator(Locator locator) {
    }

    /**
     * Not implemented.
     */
    public void skippedEntity(String name) throws SAXException {
    }

    /**
     * Not implemented.
     */
    public void startPrefixMapping(String prefix, String uri)
        throws SAXException {
    }
  }

  private static final String CHARSET = "UTF-8";

  private static final String DICTIONARY_ELEMENT = "dictionary";
  private static final String ENTRY_ELEMENT = "entry";
  private static final String TOKEN_ELEMENT = "token";
  private static final String ATTRIBUTE_CASE_SENSITIVE = "case_sensitive";


  /**
   * Creates {@link Entry}s from the given {@link InputStream} and
   * forwards these {@link Entry}s to the {@link EntryInserter}.
   *
   * After creation is finished the provided {@link InputStream} is closed.
   *
   * @param in stream to read entries from
   * @param inserter inserter to forward entries to
   *
   * @return isCaseSensitive attribute for Dictionary
   *
   * @throws IOException
   * @throws InvalidFormatException
   */
  public static boolean create(InputStream in, EntryInserter inserter)
      throws IOException {

    DictionaryContenthandler profileContentHandler =
        new DictionaryContenthandler(inserter);

    XMLReader xmlReader;
    try {
      xmlReader = XMLReaderFactory.createXMLReader();
      xmlReader.setContentHandler(profileContentHandler);
      xmlReader.parse(new InputSource(new UncloseableInputStream(in)));
    }
    catch (SAXException e) {
      throw new InvalidFormatException("The profile data stream has " +
          "an invalid format!", e);
    }
    return profileContentHandler.mIsCaseSensitiveDictionary;
  }

  /**
   * Serializes the given entries to the given {@link OutputStream}.
   *
   * After the serialization is finished the provided
   * {@link OutputStream} remains open.
   *
   * @param out stream to serialize to
   * @param entries entries to serialize
   *
   * @throws IOException If an I/O error occurs
   * @deprecated Use
   *     {@link DictionaryEntryPersistor#serialize(java.io.OutputStream, java.util.Iterator, boolean)} instead
   */
  @Deprecated
  public static void serialize(OutputStream out, Iterator<Entry> entries)
      throws IOException {
    DictionaryEntryPersistor.serialize(out, entries, true);
  }

  /**
   * Serializes the given entries to the given {@link OutputStream}.
   *
   * After the serialization is finished the provided
   * {@link OutputStream} remains open.
   *
   * @param out stream to serialize to
   * @param entries entries to serialize
   * @param casesensitive indicates if the written dictionary
   *        should be case sensitive or case insensitive.
   *
   * @throws IOException If an I/O error occurs
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
      throw new AssertionError("The Transformer configuration must be valid!");
    }

    Transformer serializer = hd.getTransformer();
    serializer.setOutputProperty(OutputKeys.ENCODING, CHARSET);
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

    for (Iterator<String> it = entry.getAttributes().iterator(); it.hasNext();) {
      String key = it.next();

      entryAttributes.addAttribute("", "", key,
          "", entry.getAttributes().getValue(key));
    }

    hd.startElement("", "", ENTRY_ELEMENT, entryAttributes);

    StringList tokens = entry.getTokens();

    for (String token : tokens) {

      hd.startElement("", "", TOKEN_ELEMENT, new AttributesImpl());

      hd.characters(token.toCharArray(), 0, token.length());

      hd.endElement("", "", TOKEN_ELEMENT);
    }

    hd.endElement("", "", ENTRY_ELEMENT);
  }
}
