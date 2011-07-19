/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreemnets.  See the NOTICE file distributed with
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

package opennlp.tools.dictionary;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Reader;
import java.util.AbstractSet;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import opennlp.tools.dictionary.serializer.Attributes;
import opennlp.tools.dictionary.serializer.DictionarySerializer;
import opennlp.tools.dictionary.serializer.Entry;
import opennlp.tools.dictionary.serializer.EntryInserter;
import opennlp.tools.util.InvalidFormatException;
import opennlp.tools.util.StringList;

/**
 * Abbreviation dictionary to be used in Sentence Detector and Tokenizer.
 */
public class AbbreviationDictionary extends AbstractSet<String> {

  /**
   * Wraps a string to handle case sensitivity
   *
   */
  private static class StringWrapper {

    private final String string;
    private final boolean isCaseSensitive;

    private StringWrapper(String string, boolean isCaseSensitive) {
      this.string = string;
      this.isCaseSensitive = isCaseSensitive;
    }

    private String getString() {
      return string;
    }

    public boolean equals(Object obj) {

      boolean result = false;

      if (obj == this) {
        result = true;
      } else if (obj instanceof StringWrapper) {
        StringWrapper other = (StringWrapper) obj;

        if (isCaseSensitive) {
          result = this.string.equals(other.string);
        } else {
          if (this.string.compareToIgnoreCase(other.string) == 0)
            result = true;
        }
      }

      return result;
    }

    public int hashCode() {
      // if lookup is too slow optimize this
      if (this.isCaseSensitive)
        return this.string.hashCode();
      return this.string.toLowerCase().hashCode();
    }

    public String toString() {
      return this.string;
    }
  }

  private boolean caseSensitive;
  private Set<StringWrapper> entrySet = new HashSet<StringWrapper>();

  /**
   * Initializes an empty case insensitive {@link AbbreviationDictionary}.
   */
  public AbbreviationDictionary() {
    this(false);
  }

  /**
   * Initializes an empty {@link AbbreviationDictionary}
   * 
   * @param caseSensitive
   *          true if the dictionary is case sensitive
   */
  public AbbreviationDictionary(boolean caseSensitive) {
    this.caseSensitive = caseSensitive;
  }

  /**
   * Initializes a case insensitive {@link AbbreviationDictionary} from an existing
   * dictionary resource.
   * 
   * @param in
   *          the XML dictionary
   * @throws IOException
   * @throws InvalidFormatException
   */
  public AbbreviationDictionary(InputStream in) throws IOException,
      InvalidFormatException {
    this(in, false);
  }

  /**
   * Initializes a case insensitive {@link AbbreviationDictionary} from an existing
   * dictionary resource.
   * 
   * @param in
   *          the XML dictionary
   * @param caseSensitive
   *          true if the dictionary is case sensitive
   * @throws IOException
   * @throws InvalidFormatException
   */
  public AbbreviationDictionary(InputStream in, boolean caseSensitive)
      throws IOException, InvalidFormatException {
    this.caseSensitive = caseSensitive;
    DictionarySerializer.create(in, new EntryInserter() {
      public void insert(Entry entry) throws InvalidFormatException {
        put(entry.getTokens());
      }
    });
  }

  /**
   * Adds the abbreviation to the dictionary as one new entry.
   * 
   * @param abb
   *          the new entry
   * @throws InvalidFormatException
   */
  private void put(StringList abb) throws InvalidFormatException {
    if (abb.size() != 1)
      throw new InvalidFormatException(
          "Each entry must have exactly one token! " + abb);
    entrySet.add(new StringWrapper(abb.getToken(0), caseSensitive));
  }

  @Override
  public boolean add(String abbreviation) {
    return this.entrySet
        .add(new StringWrapper(abbreviation, this.caseSensitive));
  }

  @Override
  public Iterator<String> iterator() {
    final Iterator<StringWrapper> entries = entrySet.iterator();

    return new Iterator<String>() {

      public boolean hasNext() {
        return entries.hasNext();
      }

      public String next() {
        return entries.next().getString();
      }

      public void remove() {
        entries.remove();
      }
    };
  }

  @Override
  public int size() {
    return this.entrySet.size();
  }
  
  @Override
  public boolean contains(Object obj) {
    boolean result = false;

    if (obj instanceof String) {
      String str = (String) obj;

      if (this.caseSensitive) {
        result = super.contains(str);
      } else {
        result = super.contains(str.toLowerCase());
      }
    }

    return result;
  }

  /**
   * Writes the current instance to the given {@link OutputStream}.
   * 
   * @param out
   * @throws IOException
   */
  public void serialize(OutputStream out) throws IOException {

    Iterator<Entry> entryIterator = new Iterator<Entry>() {
      private Iterator<String> dictionaryIterator = AbbreviationDictionary.this
          .iterator();

      public boolean hasNext() {
        return dictionaryIterator.hasNext();
      }

      public Entry next() {

        String token = dictionaryIterator.next();

        return new Entry(new StringList(token), new Attributes());
      }

      public void remove() {
        throw new UnsupportedOperationException();
      }

    };

    DictionarySerializer.serialize(out, entryIterator);
  }

  /**
   * Reads a dictionary which has one entry per line.
   * 
   * @param in
   * 
   * @return the parsed dictionary
   * 
   * @throws IOException
   */
  public static AbbreviationDictionary parseOneEntryPerLine(Reader in)
      throws IOException {
    BufferedReader lineReader = new BufferedReader(in);

    AbbreviationDictionary dictionary = new AbbreviationDictionary();

    String line;

    while ((line = lineReader.readLine()) != null) {
      line = line.trim();

      if (line.length() > 0) {
        dictionary.put(new StringList(line));
      }
    }

    return dictionary;
  }
}
