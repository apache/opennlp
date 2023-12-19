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
import java.util.StringTokenizer;

import opennlp.tools.dictionary.serializer.Attributes;
import opennlp.tools.dictionary.serializer.DictionaryEntryPersistor;
import opennlp.tools.dictionary.serializer.Entry;
import opennlp.tools.util.StringList;
import opennlp.tools.util.model.DictionarySerializer;
import opennlp.tools.util.model.SerializableArtifact;

/**
 * An iterable and serializable dictionary implementation.
 *
 * @see SerializableArtifact
 * @see Iterable
 */
public class Dictionary implements Iterable<StringList>, SerializableArtifact {
  private final Set<StringList> entrySet = new HashSet<>();
  private final boolean isCaseSensitive;
  private int minTokenCount = 99999;
  private int maxTokenCount = 0;

  /**
   * Initializes an empty {@link Dictionary}.
   * By default, the resulting instance will not be case-sensitive.
   */
  public Dictionary() {
    this(false);
  }

  /**
   * Initializes an empty {@link Dictionary}.
   *
   * @param caseSensitive Whether the new instance will operate case-sensitive, or not.
   */
  public Dictionary(boolean caseSensitive) {
    isCaseSensitive = caseSensitive;
  }

  /**
   * Initializes the {@link Dictionary} from an existing dictionary resource.
   *
   * @param in The {@link InputStream} that references the dictionary content.
   *           
   * @throws IOException Thrown if IO errors occurred.
   */
  public Dictionary(InputStream in) throws IOException {
    isCaseSensitive = DictionaryEntryPersistor.create(in, entry -> put(entry.tokens()));
  }

  /**
   * Adds the tokens to the dictionary as one new entry.
   *
   * @param tokens the new entry
   */
  public void put(StringList tokens) {
    entrySet.add(applyCaseSensitivity(tokens));
    minTokenCount = StrictMath.min(minTokenCount, tokens.size());
    maxTokenCount = StrictMath.max(maxTokenCount, tokens.size());
  }

  public int getMinTokenCount() {
    return minTokenCount;
  }

  public int getMaxTokenCount() {
    return maxTokenCount;
  }

  /**
   * Checks if this dictionary has the given entry.
   *
   * @param tokens The query of tokens to be checked for.
   * @return {@code true} if it contains the entry, {@code false} otherwise.
   */
  public boolean contains(StringList tokens) {
    return entrySet.contains(applyCaseSensitivity(tokens));
  }

  /**
   * Removes the given tokens form the current instance.
   *
   * @param tokens The tokens to be filtered out (= removed).
   */
  public void remove(StringList tokens) {
    entrySet.remove(applyCaseSensitivity(tokens));
  }

  /**
   * @return Retrieves a token-{@link Iterator} over all elements.
   */
  @Override
  public Iterator<StringList> iterator() {
    final Iterator<StringList> entries = entrySet.iterator();

    return new Iterator<>() {

      @Override
      public boolean hasNext() {
        return entries.hasNext();
      }

      @Override
      public StringList next() {
        return entries.next();
      }

      @Override
      public void remove() {
        entries.remove();
      }
    };
  }

  /**
   * @return Retrieves the number of tokens in the current instance.
   */
  public int size() {
    return entrySet.size();
  }

  /**
   * Writes the current instance to the given {@link OutputStream}.
   *
   * @param out A valid {@link OutputStream}, ready for serialization.
   * @throws IOException Thrown if IO errors occurred.
   */
  public void serialize(OutputStream out) throws IOException {

    Iterator<Entry> entryIterator = new Iterator<>() {
      private final Iterator<StringList> dictionaryIterator = Dictionary.this.iterator();

      @Override
      public boolean hasNext() {
        return dictionaryIterator.hasNext();
      }

      @Override
      public Entry next() {

        StringList tokens = dictionaryIterator.next();

        return new Entry(tokens, new Attributes());
      }

      @Override
      public void remove() {
        throw new UnsupportedOperationException();
      }

    };

    DictionaryEntryPersistor.serialize(out, entryIterator, isCaseSensitive);
  }

  @Override
  public boolean equals(Object obj) {

    boolean result;

    if (obj == this) {
      result = true;
    }
    else if (obj instanceof Dictionary dictionary) {

      result = entrySet.equals(dictionary.entrySet);
    }
    else {
      result = false;
    }

    return result;
  }

  @Override
  public int hashCode() {
    return entrySet.hashCode();
  }

  @Override
  public String toString() {
    return entrySet.toString();
  }

  /**
   * Reads a {@link Dictionary} which has one entry per line.
   * The tokens inside an entry are whitespace delimited.
   *
   * @param in A {@link Reader} instance used to parse the dictionary from.
   * @return The parsed {@link Dictionary} instance; guaranteed to be non-{@code null}.
   * @throws IOException Thrown if IO errors occurred during read and parse operations.
   */
  public static Dictionary parseOneEntryPerLine(Reader in) throws IOException {
    BufferedReader lineReader = new BufferedReader(in);

    final Dictionary dictionary = new Dictionary();

    String line;

    while ((line = lineReader.readLine()) != null) {
      StringTokenizer whiteSpaceTokenizer = new StringTokenizer(line, " ");

      String[] tokens = new String[whiteSpaceTokenizer.countTokens()];

      if (tokens.length > 0) {
        int tokenIndex = 0;
        while (whiteSpaceTokenizer.hasMoreTokens()) {
          tokens[tokenIndex++] = whiteSpaceTokenizer.nextToken();
        }

        dictionary.put(new StringList(tokens));
      }
    }

    return dictionary;
  }

  /**
   * Converts this {@link Dictionary} to a {@link Set<String>}.
   * <p>
   * Note: Only {@link AbstractSet#iterator()}, {@link AbstractSet#size()} and
   * {@link AbstractSet#contains(Object)} methods are implemented.
   * <p>
   * If this dictionary entries are multi tokens only the first token of the
   * entry will be part of the {@link Set}.
   *
   * @return A {@link Set} containing all entries of this {@link Dictionary}.
   */
  public Set<String> asStringSet() {
    return new AbstractSet<>() {

      @Override
      public Iterator<String> iterator() {
        final Iterator<StringList> entries = entrySet.iterator();

        return new Iterator<>() {
          @Override
          public boolean hasNext() {
            return entries.hasNext();
          }
          @Override
          public String next() {
            return entries.next().getToken(0);
          }
          @Override
          public void remove() {
            throw new UnsupportedOperationException();
          }
        };
      }

      @Override
      public int size() {
        return entrySet.size();
      }

      @Override
      public boolean contains(Object obj) {
        boolean result = false;

        if (obj instanceof String str) {

          result = entrySet.contains(new StringList(isCaseSensitive, str));

        }
        return result;
      }

      @Override
      public boolean equals(Object o) {
        if (! (o instanceof Set)) {
          return false;
        }
        Set<String> toCheck = (Set<String>) o;
        if (entrySet.size() != toCheck.size()) {
          return false;
        }
        Iterator<String> toCheckIter = toCheck.iterator();
        for (StringList entry : entrySet) {
          if (isCaseSensitive) {
            if (!entry.equals(new StringList(true, toCheckIter.next()))) {
              return false;
            }
          } else {
            if (!entry.compareToIgnoreCase(new StringList(false, toCheckIter.next()))) {
              return false;
            }
          }
        }
        return true;
      }

      @Override
      public int hashCode() {
        return entrySet.hashCode();
      }
    };
  }

  /**
   * @return Retrieves the serializer class for {@link Dictionary}
   *
   * @see DictionarySerializer
   */
  @Override
  public Class<?> getArtifactSerializerClass() {
    return DictionarySerializer.class;
  }

  /**
   * @return {@code true}, if this {@link Dictionary} is case-sensitive.
   */
  public boolean isCaseSensitive() {
    return isCaseSensitive;
  }

  private StringList applyCaseSensitivity(StringList list) {
    if (isCaseSensitive) {
      return list.toCaseSensitive();
    } else {
      return list.toCaseInsensitive();
    }
  }
}
