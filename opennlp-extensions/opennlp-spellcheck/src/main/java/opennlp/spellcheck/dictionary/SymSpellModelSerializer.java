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

package opennlp.spellcheck.dictionary;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.LinkedHashMap;
import java.util.Map;

import opennlp.spellcheck.distance.DamerauOSADistance;
import opennlp.spellcheck.distance.EditDistance;
import opennlp.spellcheck.distance.LevenshteinDistance;
import opennlp.spellcheck.symspell.SymSpellConfig;
import opennlp.tools.util.model.ArtifactSerializer;

/**
 * Binary {@link ArtifactSerializer} for {@link SymSpellModel}.
 *
 * <h2>What is serialized, and why</h2>
 *
 * <p>The serializer writes the model's <b>source dictionary</b> (unigram and bigram
 * counts), its {@link SymSpellConfig configuration}, and its metadata &mdash; <i>not</i>
 * the derived delete index. On {@link #create(InputStream) load} the engine is rebuilt
 * by replaying the source through {@link opennlp.spellcheck.symspell.SymSpell#add} /
 * {@link opennlp.spellcheck.symspell.SymSpell#addBigram}.</p>
 *
 * <p>Rationale (per OPENNLP-1832 guidance):</p>
 * <ul>
 *   <li><b>Size.</b> The delete index of a real dictionary is roughly an order of
 *       magnitude larger than the source word list (each term expands to many delete
 *       keys), so persisting the source yields a far smaller artifact to ship and load.</li>
 *   <li><b>Forward compatibility.</b> The index layout, {@code prefixLength} and
 *       {@code maxDictionaryEditDistance} can change between releases without
 *       invalidating already-published artifacts; the index is a pure function of the
 *       source and config and is regenerated on load.</li>
 *   <li><b>API surface.</b> The engine intentionally exposes only build hooks and no
 *       index getters, so serializing the index would require widening internal state.</li>
 * </ul>
 *
 * <p>Index rebuild cost is linear in the dictionary and small compared to artifact IO,
 * which is why this trade-off is preferred over persisting the index.</p>
 *
 * <h2>Binary layout (big-endian, {@link DataOutputStream})</h2>
 * <pre>
 *   int    magic            = 0x53594D53 ("SYMS")
 *   int    formatVersion    = 1
 *   UTF    language
 *   UTF    name
 *   UTF    version
 *   int    maxDictionaryEditDistance
 *   int    prefixLength
 *   long   countThreshold
 *   UTF    editDistanceId   ("damerau-osa" | "levenshtein")
 *   long   corpusWordCount  (0 = derive N from the dictionary; see SymSpellConfig)
 *   int    unigramCount
 *   repeat unigramCount times: UTF word, vlong count
 *   int    bigramCount
 *   repeat bigramCount  times: UTF w1, UTF w2, vlong count
 * </pre>
 *
 * <p>Counts use an unsigned variable-length encoding ({@link #writeVLong}) to keep the
 * common (small) counts compact while still representing the full {@code long} range.</p>
 *
 * <p><b>Charset.</b> The {@code UTF} fields use {@link DataOutputStream#writeUTF} /
 * {@link DataInputStream#readUTF}, i.e. Java <i>modified</i> UTF-8 (each string prefixed by
 * an unsigned 16-bit byte length, so a single encoded term may not exceed 64&nbsp;KB). This
 * is internally consistent for round-tripping but is <i>not</i> interchangeable with the
 * standard UTF-8 used by the plain-text {@link FrequencyDictionaryLoader}.</p>
 *
 * <p>The serializer has a public no-argument constructor so it can be referenced from
 * {@link SymSpellModel#getArtifactSerializerClass()} and registered with OpenNLP model
 * containers.</p>
 */
public final class SymSpellModelSerializer implements ArtifactSerializer<SymSpellModel> {

  /** Stable identifier for {@link DamerauOSADistance}. */
  public static final String EDIT_DISTANCE_DAMERAU_OSA = "damerau-osa";

  /** Stable identifier for {@link LevenshteinDistance}. */
  public static final String EDIT_DISTANCE_LEVENSHTEIN = "levenshtein";

  /** File magic: ASCII "SYMS". */
  static final int MAGIC = 0x53594D53;

  /** Current binary format version. */
  static final int FORMAT_VERSION = 1;

  /** Public no-arg constructor required by the {@link ArtifactSerializer} contract. */
  public SymSpellModelSerializer() {
  }

  @Override
  public SymSpellModel create(InputStream in) throws IOException {
    final DataInputStream din = new DataInputStream(new BufferedInputStream(in));

    final int magic = din.readInt();
    if (magic != MAGIC) {
      throw new IOException(String.format(
          "not a SymSpell model stream (magic was 0x%08X, expected 0x%08X)", magic, MAGIC));
    }
    final int formatVersion = din.readInt();
    if (formatVersion != FORMAT_VERSION) {
      throw new IOException("unsupported SymSpell model format version: " + formatVersion
          + " (this build reads version " + FORMAT_VERSION + ")");
    }

    final String language = din.readUTF();
    final String name = din.readUTF();
    final String version = din.readUTF();

    final int maxEdit = din.readInt();
    final int prefixLength = din.readInt();
    final long countThreshold = din.readLong();
    final String editDistanceId = din.readUTF();
    final long corpusWordCount = din.readLong();

    final SymSpellConfig config;
    try {
      config = SymSpellConfig.builder()
          .maxDictionaryEditDistance(maxEdit)
          .prefixLength(prefixLength)
          .countThreshold(countThreshold)
          .editDistance(editDistanceFor(editDistanceId))
          .corpusWordCount(corpusWordCount)
          .build();
    } catch (IllegalArgumentException e) {
      // Surface corrupt/out-of-range config fields as an IOException, consistent with the
      // magic/version/count checks rather than leaking an unchecked exception.
      throw new IOException("corrupt SymSpell model config: " + e.getMessage(), e);
    }

    final int unigramCount = din.readInt();
    if (unigramCount < 0) {
      throw new IOException("negative unigram count: " + unigramCount);
    }
    final Map<String, Long> unigrams = LinkedHashMap.newLinkedHashMap(unigramCount);
    for (int i = 0; i < unigramCount; i++) {
      final String word = din.readUTF();
      final long count = readVLong(din);
      unigrams.put(word, count);
    }

    final int bigramCount = din.readInt();
    if (bigramCount < 0) {
      throw new IOException("negative bigram count: " + bigramCount);
    }
    final Map<String, Long> bigrams = LinkedHashMap.newLinkedHashMap(bigramCount);
    for (int i = 0; i < bigramCount; i++) {
      final String w1 = din.readUTF();
      final String w2 = din.readUTF();
      final long count = readVLong(din);
      bigrams.put(w1 + " " + w2, count);
    }

    return new SymSpellModel(language, name, version, config, unigrams, bigrams);
  }

  @Override
  public void serialize(SymSpellModel model, OutputStream out) throws IOException {
    final DataOutputStream dout = new DataOutputStream(new BufferedOutputStream(out));

    dout.writeInt(MAGIC);
    dout.writeInt(FORMAT_VERSION);

    dout.writeUTF(model.getLanguage());
    dout.writeUTF(model.getName());
    dout.writeUTF(model.getVersion());

    final SymSpellConfig config = model.getConfig();
    dout.writeInt(config.maxDictionaryEditDistance());
    dout.writeInt(config.prefixLength());
    dout.writeLong(config.countThreshold());
    dout.writeUTF(idFor(config.editDistance()));
    dout.writeLong(config.corpusWordCount());

    final Map<String, Long> unigrams = model.unigrams();
    dout.writeInt(unigrams.size());
    for (Map.Entry<String, Long> e : unigrams.entrySet()) {
      dout.writeUTF(e.getKey());
      writeVLong(dout, e.getValue());
    }

    final Map<String, Long> bigrams = model.bigrams();
    dout.writeInt(bigrams.size());
    for (Map.Entry<String, Long> e : bigrams.entrySet()) {
      final String key = e.getKey();
      final int space = key.indexOf(' ');
      dout.writeUTF(key.substring(0, space));
      dout.writeUTF(key.substring(space + 1));
      writeVLong(dout, e.getValue());
    }

    // Flush the buffered wrapper without closing the caller's stream.
    dout.flush();
  }

  // ------------------------------------------------------------------
  // Edit-distance id mapping
  // ------------------------------------------------------------------

  private static String idFor(EditDistance editDistance) {
    if (editDistance instanceof DamerauOSADistance) {
      return EDIT_DISTANCE_DAMERAU_OSA;
    }
    if (editDistance instanceof LevenshteinDistance) {
      return EDIT_DISTANCE_LEVENSHTEIN;
    }
    throw new IllegalArgumentException(
        "cannot serialize custom EditDistance implementation: "
            + editDistance.getClass().getName()
            + "; use DamerauOSADistance or LevenshteinDistance");
  }

  private static EditDistance editDistanceFor(String id) throws IOException {
    return switch (id) {
      case EDIT_DISTANCE_DAMERAU_OSA -> DamerauOSADistance.INSTANCE;
      case EDIT_DISTANCE_LEVENSHTEIN -> LevenshteinDistance.INSTANCE;
      default -> throw new IOException("unknown edit-distance id: " + id);
    };
  }

  // ------------------------------------------------------------------
  // Unsigned variable-length long encoding (7 bits per byte, MSB = continuation).
  // ------------------------------------------------------------------

  static void writeVLong(DataOutputStream out, long value) throws IOException {
    long v = value;
    while ((v & ~0x7FL) != 0) {
      out.writeByte((int) ((v & 0x7F) | 0x80));
      v >>>= 7;
    }
    out.writeByte((int) (v & 0x7F));
  }

  static long readVLong(DataInputStream in) throws IOException {
    long value = 0;
    int shift = 0;
    while (true) {
      final int b = in.readUnsignedByte();
      value |= (long) (b & 0x7F) << shift;
      if ((b & 0x80) == 0) {
        return value;
      }
      shift += 7;
      if (shift >= 64) {
        throw new IOException("variable-length long is too long (corrupt stream)");
      }
    }
  }
}
