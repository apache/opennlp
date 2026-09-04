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

package opennlp.tools.stemmer.hunspell;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.IllegalCharsetNameException;
import java.nio.charset.StandardCharsets;
import java.nio.charset.UnsupportedCharsetException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import opennlp.tools.commons.ThreadSafe;
import opennlp.tools.util.StringUtil;

/**
 * An immutable, in-memory dictionary loaded from a user-supplied Hunspell
 * {@code .aff} and {@code .dic} files. OpenNLP does not include dictionary data.
 *
 * <p>Supported features include prefix and suffix rules, continuation classes, flag
 * modes and aliases, character encodings, compounds, blocking flags, circumfixes, and
 * full-strip rules. Unsupported directives that can alter stemming are rejected during
 * loading. Suggestion-only tables and dictionary morphology fields are ignored.</p>
 *
 * <p>Instances are immutable and safe to share between threads.</p>
 *
 * @see HunspellStemmer
 * @see HunspellStemmerFactory
 * @since 3.0.0
 */
@ThreadSafe
public final class HunspellDictionary {

  /** The file suffix for a Hunspell affix file. */
  public static final String AFFIX_FILE_SUFFIX = ".aff";

  /** The file suffix for a Hunspell word-list file. */
  public static final String DICTIONARY_FILE_SUFFIX = ".dic";

  /**
   * Maximum bytes buffered from one affix or dictionary stream during
   * {@link #load(InputStream, InputStream)}. The limit is 64 MiB. Larger
   * streams fail with {@link IOException}.
   */
  public static final int MAX_STREAM_BYTES = 64 * 1024 * 1024;

  /**
   * One parsed affix rule of a {@code PFX} or {@code SFX} block.
   *
   * @param flag The flag naming the rule's block. An entry must contain this flag to
   *             accept the rule.
   * @param crossProduct Whether the rule may combine with the other affix type.
   * @param strip The stem material the rule replaces, restored during analysis.
   * @param affix The surface material the rule adds to the stem.
   * @param condition The condition the stem must satisfy for the rule to apply.
   * @param continuation The flags of the further affixes that may stack on this one.
   */
  record Affix(int flag, boolean crossProduct, String strip, String affix,
      AffixCondition condition, int[] continuation) {

    /**
     * Checks whether a further affix may stack on this one.
     *
     * @param otherFlag The stacking affix's flag.
     * @return {@code true} if this affix's continuation classes allow it.
     */
    boolean allowsContinuation(int otherFlag) {
      for (final int candidate : continuation) {
        if (candidate == otherFlag) {
          return true;
        }
      }
      return false;
    }
  }

  /** The compound part position, which determines the required flag. */
  enum CompoundPosition {
    /** The first part. */
    BEGIN,
    /** Any part between the first and the last. */
    MIDDLE,
    /** The last part. */
    END
  }

  /** The shared empty bucket for a boundary with no affix rules. */
  private static final List<Affix> NO_AFFIXES = List.of();

  /** The line tag of a prefix block and each rule line inside the block. */
  private static final String PREFIX_TAG = "PFX";

  /** The line tag of a suffix block and each rule line inside the block. */
  private static final String SUFFIX_TAG = "SFX";

  /** Prefix used by comment lines. */
  private static final String COMMENT_PREFIX = "#";

  /** The affix-file directive that specifies the character encoding. */
  private static final String SET_TAG = "SET";

  /** The {@code SET} directive followed by a space. */
  private static final String SET_PREFIX = SET_TAG + " ";

  /** The {@code SET} directive followed by a tab. */
  private static final String SET_TAB_PREFIX = SET_TAG + "\t";

  /** The affix format marker for empty strip or affix material. */
  private static final String NO_MATERIAL = "0";

  /** Largest flag value permitted by {@code FLAG num}. */
  private static final int MAX_NUMERIC_FLAG = 65_000;

  private final Map<String, List<int[]>> entries;
  private final BoundaryIndex suffixesByLast;
  private final List<Affix> suffixesWithoutMaterial;
  private final BoundaryIndex prefixesByFirst;
  private final List<Affix> prefixesWithoutMaterial;
  private final int compoundFlag;
  private final int compoundBegin;
  private final int compoundEnd;
  private final int compoundMin;
  private final int needAffix;
  private final int onlyInCompound;
  private final int forbiddenWord;
  private final int circumfix;
  private final int compoundMiddle;
  private final int compoundPermit;
  private final int compoundForbid;
  private final int compoundWordMax;
  private final boolean checkCompoundDup;
  private final boolean checkCompoundCase;
  private final boolean checkCompoundTriple;
  private final boolean fullStrip;

  /**
   * Initializes the dictionary from parsed affix and word-list files.
   *
   * @param entries The words mapped to the flag sets of their entries.
   * @param affix The parsed affix file.
   */
  private HunspellDictionary(Map<String, List<int[]>> entries, AffixFile affix) {
    this.compoundFlag = affix.compoundFlag;
    this.compoundBegin = affix.compoundBegin;
    this.compoundEnd = affix.compoundEnd;
    this.compoundMin = affix.compoundMin;
    this.needAffix = affix.needAffix;
    this.onlyInCompound = affix.onlyInCompound;
    this.forbiddenWord = affix.forbiddenWord;
    this.circumfix = affix.circumfix;
    this.compoundMiddle = affix.compoundMiddle;
    this.compoundPermit = affix.compoundPermit;
    this.compoundForbid = affix.compoundForbid;
    this.compoundWordMax = affix.compoundWordMax;
    this.checkCompoundDup = affix.checkCompoundDup;
    this.checkCompoundCase = affix.checkCompoundCase;
    this.checkCompoundTriple = affix.checkCompoundTriple;
    this.fullStrip = affix.fullStrip;
    this.entries = entries;
    // A material-bearing rule can be undone only when the word boundary matches
    // the affix material, so bucketing by that character limits each scan to
    // one bucket plus the strip-only rules.
    final List<Affix> suffixesWithout = new ArrayList<>();
    this.suffixesByLast = bucketByBoundary(affix.suffixes, true, suffixesWithout);
    this.suffixesWithoutMaterial = List.copyOf(suffixesWithout);
    final List<Affix> prefixesWithout = new ArrayList<>();
    this.prefixesByFirst = bucketByBoundary(affix.prefixes, false, prefixesWithout);
    this.prefixesWithoutMaterial = List.copyOf(prefixesWithout);
  }

  /**
   * An immutable index of affix rules organized by the boundary code point of the affix
   * material. Each lookup uses binary search, avoiding per-word index allocation in
   * {@link HunspellStemmer}.
   */
  private static final class BoundaryIndex {

    /** The boundary code points, sorted ascending. */
    private final int[] boundaries;
    /** The rule bucket for each boundary, aligned with {@link #boundaries}. */
    private final List<List<Affix>> buckets;

    /**
     * Initializes the index from mutable buckets, freezing each one.
     *
     * @param byBoundary The rule buckets indexed by boundary code point.
     */
    private BoundaryIndex(Map<Integer, List<Affix>> byBoundary) {
      this.boundaries = new int[byBoundary.size()];
      int b = 0;
      for (final Integer boundary : byBoundary.keySet()) {
        boundaries[b++] = boundary;
      }
      Arrays.sort(boundaries);
      this.buckets = new ArrayList<>(boundaries.length);
      for (final int boundary : boundaries) {
        buckets.add(List.copyOf(byBoundary.get(boundary)));
      }
    }

    /**
     * The rules bucketed under a boundary code point.
     *
     * @param codePoint The boundary code point to look up.
     * @return The bucket, possibly empty.
     */
    List<Affix> bucket(int codePoint) {
      final int index = Arrays.binarySearch(boundaries, codePoint);
      return index >= 0 ? buckets.get(index) : NO_AFFIXES;
    }
  }

  /**
   * Buckets affix rules by the boundary code point of their affix material, the last
   * code point for a suffix rule and the first for a prefix rule.
   *
   * @param rules The rules of one kind, in file order.
   * @param suffix Whether the rules are suffix rules.
   * @param withoutMaterial Collects the rules with empty affix material, which no
   *                        boundary code point keys.
   * @return The rules indexed by boundary code point.
   */
  private static BoundaryIndex bucketByBoundary(List<Affix> rules,
      boolean suffix, List<Affix> withoutMaterial) {
    final Map<Integer, List<Affix>> byBoundary = new HashMap<>();
    for (final Affix rule : rules) {
      final String material = rule.affix();
      if (material.isEmpty()) {
        withoutMaterial.add(rule);
      } else {
        final int boundary = suffix
            ? material.codePointBefore(material.length())
            : material.codePointAt(0);
        byBoundary.computeIfAbsent(boundary, key -> new ArrayList<>()).add(rule);
      }
    }
    return new BoundaryIndex(byBoundary);
  }

  /**
   * Loads a dictionary from affix and word-list files.
   *
   * @param affixFile The {@code .aff} affix file. Must not be {@code null}.
   * @param dictionaryFile The {@code .dic} word list. Must not be {@code null}.
   * @return The loaded dictionary.
   * @throws IOException Thrown if reading fails or a file is malformed.
   * @throws IllegalArgumentException Thrown if a parameter is {@code null}.
   */
  public static HunspellDictionary load(Path affixFile, Path dictionaryFile)
      throws IOException {
    if (affixFile == null) {
      throw new IllegalArgumentException("affixFile must not be null");
    }
    if (dictionaryFile == null) {
      throw new IllegalArgumentException("dictionaryFile must not be null");
    }
    try (InputStream affix = Files.newInputStream(affixFile);
         InputStream dictionary = Files.newInputStream(dictionaryFile)) {
      return load(affix, dictionary);
    }
  }

  /**
   * Loads a dictionary from affix and word-list streams. Each stream is buffered up to
   * {@link #MAX_STREAM_BYTES} bytes; a larger stream fails with {@link IOException}.
   *
   * @param affixStream The {@code .aff} affix content. Must not be {@code null}. Not
   *                    closed.
   * @param dictionaryStream The {@code .dic} word list content. Must not be
   *                         {@code null}. Not closed.
   * @return The loaded dictionary.
   * @throws IOException Thrown if reading fails, a stream exceeds
   *     {@link #MAX_STREAM_BYTES}, or the content is malformed.
   * @throws IllegalArgumentException Thrown if a parameter is {@code null}.
   */
  public static HunspellDictionary load(InputStream affixStream,
      InputStream dictionaryStream) throws IOException {
    if (affixStream == null) {
      throw new IllegalArgumentException("affixStream must not be null");
    }
    if (dictionaryStream == null) {
      throw new IllegalArgumentException("dictionaryStream must not be null");
    }
    final byte[] affixBytes = readBounded(affixStream, MAX_STREAM_BYTES, "affix stream");
    final Charset charset = declaredCharset(affixBytes);
    final AffixFile affix = parseAffix(decode(affixBytes, charset, "affix stream"));
    final Map<String, List<int[]>> entries = parseWordList(
        decode(readBounded(dictionaryStream, MAX_STREAM_BYTES, "dictionary stream"),
            charset, "dictionary stream"),
        affix.flagMode, affix.flagAliases);
    return new HunspellDictionary(entries, affix);
  }

  /**
   * Converts file content without replacing malformed or unmappable input.
   *
   * @param bytes The encoded file content.
   * @param charset The selected character encoding.
   * @param label The file label used in the exception message.
   * @return The decoded content.
   * @throws IOException Thrown if {@code bytes} are invalid in {@code charset}.
   */
  private static String decode(byte[] bytes, Charset charset, String label)
      throws IOException {
    try {
      return charset.newDecoder()
          .onMalformedInput(CodingErrorAction.REPORT)
          .onUnmappableCharacter(CodingErrorAction.REPORT)
          .decode(ByteBuffer.wrap(bytes))
          .toString();
    } catch (CharacterCodingException e) {
      throw new IOException(label + " is not valid " + charset.name(), e);
    }
  }

  /**
   * Copies an input stream into a byte array and fails after {@code maxBytes}.
   *
   * @param in The stream to read. Not closed.
   * @param maxBytes The maximum buffered byte count, inclusive.
   * @param label The stream name used in the error message.
   * @return The buffered bytes.
   * @throws IOException Thrown if reading fails or the stream exceeds {@code maxBytes}.
   */
  static byte[] readBounded(InputStream in, int maxBytes, String label)
      throws IOException {
    final byte[] chunk = new byte[8192];
    byte[] buffer = new byte[Math.min(8192, maxBytes)];
    int size = 0;
    int n;
    while ((n = in.read(chunk)) >= 0) {
      if (size + n > maxBytes) {
        throw new IOException(label + " size exceeds safe limit of " + maxBytes);
      }
      if (size + n > buffer.length) {
        buffer = Arrays.copyOf(buffer, Math.min(maxBytes, Math.max(buffer.length * 2, size + n)));
      }
      System.arraycopy(chunk, 0, buffer, size, n);
      size += n;
    }
    return size == buffer.length ? buffer : Arrays.copyOf(buffer, size);
  }

  /**
   * Looks up a word's flag sets.
   *
   * @param word The word as listed.
   * @return The flag sets of all matching entries, or {@code null} when missing.
   */
  List<int[]> lookup(String word) {
    final List<int[]> found = entries.get(word);
    if (found == null) {
      return null;
    }
    final List<int[]> copy = new ArrayList<>(found.size());
    for (final int[] flags : found) {
      copy.add(flags.clone());
    }
    return copy;
  }

  /**
   * Returns suffix rules with affix material ending in the given code point. These
   * are the only material-bearing suffix rules applicable to that word boundary.
   *
   * @param last The word's last code point.
   * @return The bucket, possibly empty.
   */
  List<Affix> suffixesEndingWith(int last) {
    return suffixesByLast.bucket(last);
  }

  /** {@return the strip-only suffix rules, applicable to any word} */
  List<Affix> suffixesWithoutMaterial() {
    return suffixesWithoutMaterial;
  }

  /**
   * Returns prefix rules with affix material starting with the given code point.
   * These are the only material-bearing prefix rules applicable to that boundary.
   *
   * @param first The word's first code point.
   * @return The bucket, possibly empty.
   */
  List<Affix> prefixesStartingWith(int first) {
    return prefixesByFirst.bucket(first);
  }

  /** {@return the strip-only prefix rules, applicable to any word} */
  List<Affix> prefixesWithoutMaterial() {
    return prefixesWithoutMaterial;
  }

  /** {@return whether the affix file contains a compound flag} */
  boolean compoundsDeclared() {
    return compoundFlag != 0 || compoundBegin != 0 || compoundEnd != 0
        || compoundMiddle != 0;
  }

  /** {@return the smallest length a compound part may have} At least {@code 1}. */
  int compoundMin() {
    return compoundMin;
  }

  /** {@return the largest permitted compound part count} {@code 0} means no limit. */
  int compoundWordMax() {
    return compoundWordMax;
  }

  /** {@return whether {@code CHECKCOMPOUNDDUP} rejects adjacent duplicate parts} */
  boolean checkCompoundDup() {
    return checkCompoundDup;
  }

  /** {@return whether {@code CHECKCOMPOUNDCASE} rejects uppercase at part boundaries} */
  boolean checkCompoundCase() {
    return checkCompoundCase;
  }

  /** {@return whether {@code CHECKCOMPOUNDTRIPLE} rejects repeated boundary letters} */
  boolean checkCompoundTriple() {
    return checkCompoundTriple;
  }

  /** {@return whether {@code FULLSTRIP} allows an affix rule to strip a complete stem} */
  boolean fullStrip() {
    return fullStrip;
  }

  /**
   * The flag admitting a part at a compound position, next to the general
   * compound flag.
   *
   * @param position The part's place in the compound.
   * @return The dedicated positional flag, or {@code 0} when undeclared.
   */
  private int positionalFlag(CompoundPosition position) {
    return switch (position) {
      case BEGIN -> compoundBegin;
      case MIDDLE -> compoundMiddle;
      case END -> compoundEnd;
    };
  }

  /**
   * Checks whether a listed word may stand at a compound position: some homonym's
   * flag set contains the general compound flag or the position's dedicated flag
   * and is not forbidden. An {@code ONLYINCOMPOUND} entry is valid here, while a
   * {@code NEEDAFFIX} entry still requires an affix.
   *
   * @param flagSets The word's flag sets from {@link #lookup(String)}.
   * @param position The part's place in the compound.
   * @return {@code true} if the word may stand at the position.
   */
  boolean mayStand(List<int[]> flagSets, CompoundPosition position) {
    final int positional = positionalFlag(position);
    for (final int[] flags : flagSets) {
      if ((contains(flags, compoundFlag) || contains(flags, positional))
          && !contains(flags, forbiddenWord) && !contains(flags, needAffix)
          && !forbiddenAtCompoundPosition(flags, position)) {
        return true;
      }
    }
    return false;
  }

  /**
   * Checks whether some homonym supports an affixed compound part. The flag set
   * contains the removed affix flag, is not forbidden, and either the affix permits
   * the position or the set contains the compound or positional flag.
   *
   * @param flagSets The part stem's flag sets from {@link #lookup(String)}.
   * @param affixFlag The removed affix's flag.
   * @param position The part's place in the compound.
   * @param affixAdmits Whether the affix's continuation classes admit the position,
   *                    from {@link #affixAdmits(Affix, CompoundPosition)}.
   * @return {@code true} if some homonym permits the affixed form at the position.
   */
  boolean supportsPart(List<int[]> flagSets, int affixFlag, CompoundPosition position,
      boolean affixAdmits) {
    final int positional = positionalFlag(position);
    for (final int[] flags : flagSets) {
      if (contains(flags, affixFlag) && !contains(flags, forbiddenWord)
          && !forbiddenAtCompoundPosition(flags, position)
          && (affixAdmits || contains(flags, compoundFlag)
              || contains(flags, positional))) {
        return true;
      }
    }
    return false;
  }

  /**
   * Checks whether an affix permits the resulting form at a compound position. The
   * continuation classes contain the general compound flag or the position's
   * dedicated flag. Published dictionaries position linking forms through zero or dash
   * suffixes with positional continuation flags.
   *
   * @param affix The affix rule applied to the part.
   * @param position The part's place in the compound.
   * @return {@code true} if the affixed form may stand at the position.
   */
  boolean affixAdmits(Affix affix, CompoundPosition position) {
    return (compoundFlag != 0 && affix.allowsContinuation(compoundFlag))
        || (positionalFlag(position) != 0
            && affix.allowsContinuation(positionalFlag(position)));
  }

  /**
   * Checks whether an affix is allowed at a compound-internal boundary: it contains the
   * {@code COMPOUNDPERMITFLAG} among the continuation classes. Without the flag a
   * suffix fits only the last part and a prefix only the first.
   *
   * @param affix The affix rule applied to the part.
   * @return {@code true} if the affix may face another part.
   */
  boolean permitsInside(Affix affix) {
    return compoundPermit != 0 && affix.allowsContinuation(compoundPermit);
  }

  /**
   * Checks whether an affix bars the resulting form from compounds. The continuation
   * classes contain {@code COMPOUNDFORBIDFLAG} when blocked.
   *
   * @param affix The affix rule applied to the part.
   * @return {@code true} if the affixed form may not join a compound.
   */
  boolean forbidsInCompound(Affix affix) {
    return compoundForbid != 0 && affix.allowsContinuation(compoundForbid);
  }

  /**
   * Checks whether an entry marked with {@code COMPOUNDFORBIDFLAG} is barred from
   * this compound position. Hunspell permits such an entry only as the last part.
   *
   * @param flags One entry's flag set.
   * @param position The part's place in the compound.
   * @return {@code true} if the entry may not stand at the position.
   */
  private boolean forbiddenAtCompoundPosition(int[] flags, CompoundPosition position) {
    return position != CompoundPosition.END && contains(flags, compoundForbid);
  }

  /**
   * Checks whether any of a word's flag sets is forbidden, which a dictionary uses
   * to block one specific ill-formed compound while the parts remain productive.
   *
   * @param flagSets The word's flag sets from {@link #lookup(String)}.
   * @return {@code true} if some homonym contains the forbidden-word flag.
   */
  boolean anyForbidden(List<int[]> flagSets) {
    return hasFlag(flagSets, forbiddenWord);
  }

  /**
   * Checks whether any of a word's flag sets contains a flag.
   *
   * @param flagSets The flag sets from {@link #lookup(String)}.
   * @param flag The flag to look for.
   * @return {@code true} if some flag set contains the flag.
   */
  static boolean hasFlag(List<int[]> flagSets, int flag) {
    for (final int[] flags : flagSets) {
      if (contains(flags, flag)) {
        return true;
      }
    }
    return false;
  }

  /**
   * Checks one flag set for a flag. An undeclared flag, encoded as {@code 0}, does not occur in an
   * entry.
   *
   * @param flags One entry's flag set.
   * @param flag The flag to look for.
   * @return {@code true} if the set contains the flag.
   */
  private static boolean contains(int[] flags, int flag) {
    if (flag == 0) {
      return false;
    }
    for (final int candidate : flags) {
      if (candidate == flag) {
        return true;
      }
    }
    return false;
  }

  /**
   * Checks whether a listed word is valid without an affix or compound. A matching flag set must
   * omit {@code NEEDAFFIX}, {@code ONLYINCOMPOUND}, and {@code FORBIDDENWORD}.
   *
   * @param flagSets The word's flag sets from {@link #lookup(String)}.
   * @return {@code true} if some homonym is a standalone word.
   */
  boolean validStandalone(List<int[]> flagSets) {
    for (final int[] flags : flagSets) {
      if (!contains(flags, needAffix) && !contains(flags, onlyInCompound)
          && !contains(flags, forbiddenWord)) {
        return true;
      }
    }
    return false;
  }

  /**
   * Checks whether some homonym supports an affix analysis: the flag set contains the
   * affix's flag and is not compound-only or forbidden. A {@code NEEDAFFIX} set
   * supports the analysis because the removed affix satisfies the virtual stem.
   *
   * @param flagSets The stem's flag sets from {@link #lookup(String)}.
   * @param flag The removed affix's flag.
   * @return {@code true} if some homonym contains the flag and may stand affixed.
   */
  boolean supports(List<int[]> flagSets, int flag) {
    for (final int[] flags : flagSets) {
      if (contains(flags, flag) && !contains(flags, onlyInCompound)
          && !contains(flags, forbiddenWord)) {
        return true;
      }
    }
    return false;
  }

  /**
   * Checks whether some homonym supports a cross-product analysis. The root can contain
   * both affix flags, or one root flag can select an affix with continuation flags that
   * select the other affix.
   *
   * @param flagSets The stem's flag sets from {@link #lookup(String)}.
   * @param prefix The removed prefix.
   * @param suffix The removed suffix.
   * @return {@code true} if some homonym licenses both affixes.
   */
  boolean supports(List<int[]> flagSets, Affix prefix, Affix suffix) {
    for (final int[] flags : flagSets) {
      final boolean rootHasPrefix = contains(flags, prefix.flag());
      final boolean rootHasSuffix = contains(flags, suffix.flag());
      final boolean licensesBoth = (rootHasPrefix
          && (rootHasSuffix || prefix.allowsContinuation(suffix.flag())))
          || (rootHasSuffix && suffix.allowsContinuation(prefix.flag()));
      if (licensesBoth && !contains(flags, onlyInCompound)
          && !contains(flags, forbiddenWord)) {
        return true;
      }
    }
    return false;
  }

  /**
   * Checks whether a form made with only this affix is still a virtual stem. The
   * continuation classes contain {@code NEEDAFFIX}, so a
   * further affix must join before the form is a word.
   *
   * @param affix The affix rule to inspect.
   * @return {@code true} if an additional affix is required.
   */
  boolean needsFurtherAffix(Affix affix) {
    return needAffix != 0 && affix.allowsContinuation(needAffix);
  }

  /**
   * Checks whether an affix applies only inside compounds: it contains the
   * {@code ONLYINCOMPOUND} flag among the continuation classes.
   *
   * @param affix The affix rule to inspect.
   * @return {@code true} if the affix does not apply to a standalone word.
   */
  boolean compoundOnly(Affix affix) {
    return onlyInCompound != 0 && affix.allowsContinuation(onlyInCompound);
  }

  /**
   * Checks whether an affix is a circumfix member. The continuation classes contain
   * the {@code CIRCUMFIX} flag, so it is valid only with a marked affix of the other
   * type. The German {@code ge...t} participle is an example.
   *
   * @param affix The affix rule to inspect.
   * @return {@code true} if another circumfix member is required.
   */
  boolean circumfixOnly(Affix affix) {
    return circumfix != 0 && affix.allowsContinuation(circumfix);
  }

  /**
   * Finds the {@code SET} declaration by scanning the raw affix bytes as ASCII, which
   * is safe because the declaration is ASCII in all supported encodings. Both
   * files are then decoded with the specified charset.
   *
   * @param affixBytes The raw affix file content.
   * @return The specified charset, or UTF-8 when no declaration is present.
   * @throws IOException Thrown if the specified encoding name is not supported.
   */
  private static Charset declaredCharset(byte[] affixBytes) throws IOException {
    final String ascii = new String(affixBytes, StandardCharsets.US_ASCII);
    for (final String line : splitLines(ascii)) {
      final String trimmed = trim(line);
      if (trimmed.startsWith(SET_PREFIX) || trimmed.startsWith(SET_TAB_PREFIX)) {
        final String name = trim(trimmed.substring(SET_PREFIX.length()));
        try {
          return Charset.forName(name);
        } catch (IllegalCharsetNameException | UnsupportedCharsetException e) {
          throw new IOException("unsupported SET encoding: " + name, e);
        }
      }
    }
    return StandardCharsets.UTF_8;
  }

  /** The flag encodings supported by the {@code FLAG} directive. */
  private enum FlagMode {
    /**
     * The default: each single character is one flag. {@code FLAG UTF-8} also selects
     * this mode after the {@code SET} declaration determines file decoding.
     */
    CHAR,
    /** Selected by {@code FLAG long}: each consecutive character combination is one flag. */
    LONG,
    /** Selected by {@code FLAG num}: comma-separated decimal numbers are flags. */
    NUM
  }

  /** The parsed affix file content. */
  private static final class AffixFile {
    private final List<Affix> prefixes = new ArrayList<>();
    private final List<Affix> suffixes = new ArrayList<>();
    private final List<int[]> flagAliases = new ArrayList<>();
    private FlagMode flagMode = FlagMode.CHAR;
    private int compoundFlag;
    private int compoundBegin;
    private int compoundEnd;
    private int compoundMin = 3;
    private int needAffix;
    private int onlyInCompound;
    private int forbiddenWord;
    private int circumfix;
    private int compoundMiddle;
    private int compoundPermit;
    private int compoundForbid;
    private int compoundWordMax;
    private boolean checkCompoundDup;
    private boolean checkCompoundCase;
    private boolean checkCompoundTriple;
    private boolean fullStrip;
  }

  /**
   * Parses the affix file: the {@code FLAG} declaration, the {@code AF} flag alias
   * table, the compound and blocking flag declarations, and the {@code PFX} and
   * {@code SFX} blocks. Unsupported directives that can alter stemming are rejected;
   * suggestion-only directives are ignored.
   *
   * @param content The decoded affix file content.
   * @return The parsed rules and flag mode.
   * @throws IOException Thrown if input is malformed or an unsupported directive
   *     affects analysis.
   */
  private static AffixFile parseAffix(String content) throws IOException {
    final AffixFile result = new AffixFile();
    final String[] lines = splitLines(withoutByteOrderMark(content));
    result.flagMode = readFlagMode(lines);
    result.flagAliases.addAll(readFlagAliases(lines, result.flagMode));
    int i = 0;
    while (i < lines.length) {
      final String[] fields = split(lines[i]);
      if (fields.length == 0 || fields[0].startsWith(COMMENT_PREFIX)) {
        i++;
        continue;
      }
      switch (fields[0]) {
        case "FLAG":
          i++;
          break;
        case "COMPOUNDFLAG":
        case "COMPOUNDBEGIN":
        case "COMPOUNDMIDDLE":
        case "COMPOUNDEND":
        case "COMPOUNDPERMITFLAG":
        case "COMPOUNDFORBIDFLAG":
        case "NEEDAFFIX":
        case "PSEUDOROOT":
        case "ONLYINCOMPOUND":
        case "FORBIDDENWORD":
        case "CIRCUMFIX":
          if (fields.length < 2) {
            throw new IOException(fields[0] + " line without a flag at line " + (i + 1));
          }
          final int declared = parseFlag(fields[1], result.flagMode, i + 1);
          switch (fields[0]) {
            case "COMPOUNDFLAG" -> result.compoundFlag = declared;
            case "COMPOUNDBEGIN" -> result.compoundBegin = declared;
            case "COMPOUNDMIDDLE" -> result.compoundMiddle = declared;
            case "COMPOUNDEND" -> result.compoundEnd = declared;
            case "COMPOUNDPERMITFLAG" -> result.compoundPermit = declared;
            case "COMPOUNDFORBIDFLAG" -> result.compoundForbid = declared;
            case "NEEDAFFIX", "PSEUDOROOT" -> result.needAffix = declared;
            case "ONLYINCOMPOUND" -> result.onlyInCompound = declared;
            case "CIRCUMFIX" -> result.circumfix = declared;
            case "FORBIDDENWORD" -> result.forbiddenWord = declared;
            default -> throw new IOException(
                "unhandled flag directive " + fields[0] + " at line " + (i + 1));
          }
          i++;
          break;
        case "COMPOUNDMIN":
          result.compoundMin = parseValue(fields, i + 1);
          if (result.compoundMin < 0) {
            throw new IOException("negative COMPOUNDMIN at line " + (i + 1));
          }
          result.compoundMin = Math.max(1, result.compoundMin);
          i++;
          break;
        case "COMPOUNDWORDMAX":
          result.compoundWordMax = parseValue(fields, i + 1);
          if (result.compoundWordMax < 0) {
            throw new IOException("negative COMPOUNDWORDMAX at line " + (i + 1));
          }
          i++;
          break;
        case "CHECKCOMPOUNDDUP":
          result.checkCompoundDup = true;
          i++;
          break;
        case "CHECKCOMPOUNDCASE":
          result.checkCompoundCase = true;
          i++;
          break;
        case "CHECKCOMPOUNDTRIPLE":
          result.checkCompoundTriple = true;
          i++;
          break;
        case "FULLSTRIP":
          result.fullStrip = true;
          i++;
          break;
        case "AF":
          i++;
          break;
        case PREFIX_TAG:
        case SUFFIX_TAG:
          i = parseAffixBlock(lines, i, fields, result);
          break;
        case "ICONV":
        case "OCONV":
        case "COMPLEXPREFIXES":
        case "COMPOUNDMORESUFFIXES":
        case "COMPOUNDROOT":
        case "CHECKCOMPOUNDREP":
        case "SIMPLIFIEDTRIPLE":
        case "CHECKCOMPOUNDPATTERN":
        case "FORCEUCASE":
        case "COMPOUNDSYLLABLE":
        case "SYLLABLENUM":
        case "LANG":
        case "CHECKSHARPS":
        case "BREAK":
        case "FORBIDWARN":
        // COMPOUNDRULE licenses pattern compounds, IGNORE removes characters before
        // matching, and KEEPCASE rejects the case variants this stemmer analyzes;
        // ignoring any of them would change stems with no signal
        case "COMPOUNDRULE":
        case "IGNORE":
        case "KEEPCASE":
          throw new IOException("unsupported affix directive '" + fields[0]
              + "' at line " + (i + 1));
        case "SET":
        case "AM":
        case "KEY":
        case "TRY":
        case "NOSUGGEST":
        case "MAXCPDSUGS":
        case "MAXNGRAMSUGS":
        case "MAXDIFF":
        case "ONLYMAXDIFF":
        case "NOSPLITSUGS":
        case "SUGSWITHDOTS":
        case "REP":
        case "MAP":
        case "PHONE":
        case "WARN":
        case "LEMMA_PRESENT":
        case "SUBSTANDARD":
        case "WORDCHARS":
          i++;
          break;
        default:
          throw new IOException("unsupported affix directive '" + fields[0]
              + "' at line " + (i + 1));
      }
    }
    return result;
  }

  /**
   * Finds the file-wide flag mode before parsing directives that contain flags.
   *
   * @param lines The affix file lines.
   * @return The selected flag mode, or character mode when no declaration is present.
   * @throws IOException Thrown if the declaration is missing a mode, unsupported, or
   *     repeated.
   */
  private static FlagMode readFlagMode(String[] lines) throws IOException {
    FlagMode mode = FlagMode.CHAR;
    boolean foundMode = false;
    for (int i = 0; i < lines.length; i++) {
      final String[] fields = split(lines[i]);
      if (fields.length == 0 || fields[0].startsWith(COMMENT_PREFIX)
          || !"FLAG".equals(fields[0])) {
        continue;
      }
      if (foundMode) {
        throw new IOException("multiple FLAG directives at line " + (i + 1));
      }
      if (fields.length < 2) {
        throw new IOException("FLAG line without a mode at line " + (i + 1));
      }
      mode = switch (fields[1]) {
        case "long" -> FlagMode.LONG;
        case "num" -> FlagMode.NUM;
        case "UTF-8" -> FlagMode.CHAR;
        default -> throw new IOException(
            "unsupported FLAG mode '" + fields[1] + "' at line " + (i + 1));
      };
      foundMode = true;
    }
    return mode;
  }

  /**
   * Parses the file-wide flag alias table before parsing affix continuation flags.
   *
   * @param lines The affix file lines.
   * @param mode The file's flag encoding.
   * @return The aliases in their one-based reference order.
   * @throws IOException Thrown if the table header, size, or an alias is malformed.
   */
  private static List<int[]> readFlagAliases(String[] lines, FlagMode mode)
      throws IOException {
    final List<int[]> aliases = new ArrayList<>();
    int expected = -1;
    for (int i = 0; i < lines.length; i++) {
      final String[] fields = split(lines[i]);
      if (fields.length == 0 || fields[0].startsWith(COMMENT_PREFIX)
          || !"AF".equals(fields[0])) {
        continue;
      }
      if (fields.length < 2) {
        throw new IOException("AF line without a value at line " + (i + 1));
      }
      if (expected < 0) {
        expected = parseValue(fields, i + 1);
        if (expected < 0) {
          throw new IOException("negative AF count at line " + (i + 1));
        }
      } else {
        aliases.add(parseFlags(fields[1], mode, i + 1));
      }
    }
    if (expected >= 0 && aliases.size() != expected) {
      throw new IOException("AF header specifies " + expected + " aliases but found "
          + aliases.size());
    }
    return aliases;
  }

  /**
   * Parses the integer following a directive name.
   *
   * @param fields The already-split directive line.
   * @param lineNumber The source line, for error messages.
   * @return The parsed value.
   * @throws IOException Thrown if the value is missing or is not an integer.
   */
  private static int parseValue(String[] fields, int lineNumber) throws IOException {
    if (fields.length < 2) {
      throw new IOException(fields[0] + " line without a value at line " + lineNumber);
    }
    try {
      return Integer.parseInt(fields[1]);
    } catch (NumberFormatException e) {
      throw new IOException("malformed " + fields[0] + " at line " + lineNumber, e);
    }
  }

  /**
   * Parses one {@code PFX} or {@code SFX} block: the header line naming the flag, the
   * cross-product marker, and the rule count, followed by the matching number of
   * rule lines.
   *
   * @param lines All lines of the affix file.
   * @param index The line index of the block header.
   * @param header The already-split header fields.
   * @param result The parse target the rules are added to.
   * @return The index of the first line after the block.
   * @throws IOException Thrown if the header or a rule line is malformed.
   */
  private static int parseAffixBlock(String[] lines, int index, String[] header,
      AffixFile result) throws IOException {
    if (header.length < 4) {
      throw new IOException("malformed affix header at line " + (index + 1));
    }
    final boolean suffix = SUFFIX_TAG.equals(header[0]);
    final int flag = parseFlag(header[1], result.flagMode, index + 1);
    if (!"Y".equals(header[2]) && !"N".equals(header[2])) {
      throw new IOException("invalid cross-product marker at line " + (index + 1));
    }
    final boolean crossProduct = "Y".equals(header[2]);
    final int count;
    try {
      count = Integer.parseInt(header[3]);
    } catch (NumberFormatException e) {
      throw new IOException("malformed affix rule count at line " + (index + 1), e);
    }
    if (count < 0) {
      throw new IOException("negative affix rule count at line " + (index + 1));
    }
    int line = index + 1;
    for (int rule = 0; rule < count; rule++, line++) {
      if (line >= lines.length) {
        throw new IOException("affix block truncated at line " + (line + 1));
      }
      final String[] fields = split(lines[line]);
      if (fields.length < 5 || !fields[0].equals(header[0])) {
        throw new IOException("malformed affix rule at line " + (line + 1));
      }
      if (parseFlag(fields[1], result.flagMode, line + 1) != flag) {
        throw new IOException("affix rule flag does not match header at line " + (line + 1));
      }
      final String strip = NO_MATERIAL.equals(fields[2]) ? "" : fields[2];
      String affixText = fields[3];
      int[] continuation = new int[0];
      final int slash = affixText.indexOf('/');
      if (slash >= 0) {
        continuation = parseAliasedFlags(affixText.substring(slash + 1),
            result.flagMode, result.flagAliases, line + 1);
        affixText = affixText.substring(0, slash);
      }
      if (NO_MATERIAL.equals(affixText)) {
        affixText = "";
      }
      final Affix affix = new Affix(flag, crossProduct, strip, affixText,
          AffixCondition.parse(fields[4], suffix, line + 1), continuation);
      if (suffix) {
        result.suffixes.add(affix);
      } else {
        result.prefixes.add(affix);
      }
    }
    return line;
  }

  /**
   * Parses the word list: an optional leading entry count, then one entry per line
   * consisting of the word, an optional {@code /flags} run, and optional trailing
   * morphological fields, which are ignored. The morphological fields are cut off
   * first, because the flag separator is only meaningful in what precedes them; a word
   * may contain spaces. A slash escaped as {@code \/} is part of the word
   * and is unescaped in the stored key.
   *
   * @param content The decoded word-list content.
   * @param flagMode The flag encoding selected by the affix file.
   * @param flagAliases The affix file's {@code AF} alias table, possibly empty. When
   *                    it is not empty, a purely numeric flag field is a 1-based
   *                    reference into the table instead of an independent flag run.
   * @return The words mapped to entry flag sets.
   * @throws IOException Thrown if a flag run is malformed or an alias reference is
   *         out of range.
   */
  private static Map<String, List<int[]>> parseWordList(String content,
      FlagMode flagMode, List<int[]> flagAliases) throws IOException {
    final String[] lines = splitLines(withoutByteOrderMark(content));
    final Map<String, List<int[]>> entries = new HashMap<>();
    int start = 0;
    if (lines.length > 0 && isCount(trim(lines[0]))) {
      start = 1;
    }
    for (int i = start; i < lines.length; i++) {
      final String line = trim(lines[i]);
      if (line.isEmpty()) {
        continue;
      }
      final int morphology = morphologyIndex(line);
      final String entry = morphology < 0 ? line : trim(line.substring(0, morphology));
      String word = entry;
      int[] flags = new int[0];
      final int slash = unescapedSlash(entry);
      if (slash >= 0) {
        word = entry.substring(0, slash);
        String flagRun = entry.substring(slash + 1);
        // The flag run terminates at the first space or tabulator. Remaining text is
        // a morphological field even without a tag.
        for (int c = 0; c < flagRun.length(); c++) {
          if (isFieldSeparator(flagRun.charAt(c))) {
            flagRun = flagRun.substring(0, c);
            break;
          }
        }
        flags = parseAliasedFlags(flagRun, flagMode, flagAliases, i + 1);
      }
      entries.computeIfAbsent(word.replace("\\/", "/"), key -> new ArrayList<>(1))
          .add(flags);
    }
    return entries;
  }

  /**
   * Removes a Unicode byte-order mark decoded at the start of a file.
   *
   * @param content The decoded file content.
   * @return The content without an initial byte-order mark.
   */
  private static String withoutByteOrderMark(String content) {
    return !content.isEmpty() && content.charAt(0) == '\uFEFF'
        ? content.substring(1) : content;
  }

  /**
   * Checks whether a line consists purely of decimal digits, which identifies the
   * optional entry-count header of a word list.
   *
   * @param line The trimmed line to inspect.
   * @return {@code true} if the line is a non-empty digit run.
   */
  private static boolean isCount(String line) {
    if (line.isEmpty()) {
      return false;
    }
    for (int i = 0; i < line.length(); i++) {
      if (line.charAt(i) < '0' || line.charAt(i) > '9') {
        return false;
      }
    }
    return true;
  }

  /**
   * Resolves a numeric {@code AF} alias or parses a direct flag run when no alias
   * applies.
   *
   * @param text The flag field without the leading slash.
   * @param mode The selected flag encoding.
   * @param aliases The affix file's alias table.
   * @param lineNumber The source line, for error messages.
   * @return The resolved or parsed flags.
   * @throws IOException Thrown if the alias is malformed or outside the table, or the
   *         direct flags do not fit {@code mode}.
   */
  private static int[] parseAliasedFlags(String text, FlagMode mode,
      List<int[]> aliases, int lineNumber) throws IOException {
    if (!aliases.isEmpty() && isCount(text)) {
      final int alias;
      try {
        alias = Integer.parseInt(text);
      } catch (NumberFormatException e) {
        throw new IOException("malformed flag alias '" + text + "' at line "
            + lineNumber, e);
      }
      if (alias < 1 || alias > aliases.size()) {
        throw new IOException("flag alias " + alias + " at line " + lineNumber
            + " is outside the AF table of " + aliases.size() + " aliases");
      }
      return aliases.get(alias - 1);
    }
    return parseFlags(text, mode, lineNumber);
  }

  /**
   * Finds the first {@code /} that is not escaped as {@code \/}, which separates the
   * word from the flag run in a word-list entry.
   *
   * @param line The word-list line to scan.
   * @return The index of the separator, or {@code -1} when the entry has no flags.
   */
  private static int unescapedSlash(String line) {
    for (int i = 1; i < line.length(); i++) {
      if (line.charAt(i) == '/' && line.charAt(i - 1) != '\\') {
        return i;
      }
    }
    return -1;
  }

  /**
   * Finds where the trailing morphological fields of a word-list entry begin, which
   * terminates the word and flag run. A morphological field is introduced by a
   * tabulator or written as a short tag followed by
   * {@code :} and preceded by a separator, such as {@code po:verb}. A separator that
   * is not followed by such a tag is part of the word, because a word-list entry may
   * name several words. Space and tab are the word-list field delimiters, not a general
   * whitespace classification, so
   * wider whitespace such as a no-break space remains part of the word.
   *
   * @param line The trimmed word-list line to scan.
   * @return The index at which the morphological fields begin, or {@code -1} if the
   *         entry contains none.
   */
  private static int morphologyIndex(String line) {
    int cut = -1;
    for (int i = 4; i < line.length(); i++) {
      if (line.charAt(i) == ':' && isFieldSeparator(line.charAt(i - 3))) {
        int fieldStart = i - 3;
        while (fieldStart > 0 && isFieldSeparator(line.charAt(fieldStart - 1))) {
          fieldStart--;
        }
        // a tag with no word in front of it is not a morphological field
        cut = fieldStart == 0 ? -1 : fieldStart;
        break;
      }
    }
    final int tab = line.indexOf('\t');
    if (tab >= 0 && (cut < 0 || tab < cut)) {
      cut = tab;
    }
    return cut;
  }

  /**
   * Checks one character for the word-list field separators, space and tabulator.
   *
   * @param c The character to test.
   * @return {@code true} if {@code c} separates fields in the word-list format.
   */
  private static boolean isFieldSeparator(char c) {
    return c == ' ' || c == '\t';
  }

  /**
   * Removes leading and trailing whitespace, using the whitespace definition the rest
   * of the parser scans with.
   *
   * @param text The text to trim.
   * @return The text without leading or trailing whitespace.
   */
  private static String trim(String text) {
    int start = 0;
    int end = text.length();
    while (start < end && StringUtil.isWhitespace(text.charAt(start))) {
      start++;
    }
    while (end > start && StringUtil.isWhitespace(text.charAt(end - 1))) {
      end--;
    }
    return text.substring(start, end);
  }

  /**
   * Parses a flag run according to the selected flag mode: single characters in
   * {@code char} mode, consecutive characters packed into one {@code int} in {@code long}
   * mode, and comma-separated decimal numbers in {@code num} mode.
   *
   * @param text The flag run without the leading {@code /}. An empty run contains no
   *             flags in any mode.
   * @param mode The selected flag encoding.
   * @param lineNumber The source line, for error messages.
   * @return The parsed flags.
   * @throws IOException Thrown if the run does not fit the selected encoding.
   */
  private static int[] parseFlags(String text, FlagMode mode, int lineNumber)
      throws IOException {
    if (text.isEmpty()) {
      return new int[0];
    }
    switch (mode) {
      case NUM: {
        final String[] parts = splitOn(text, ',');
        final int[] flags = new int[parts.length];
        for (int i = 0; i < parts.length; i++) {
          final String value = trim(parts[i]);
          try {
            flags[i] = Integer.parseInt(value);
          } catch (NumberFormatException e) {
            throw new IOException("malformed numeric flag at line " + lineNumber, e);
          }
          if (flags[i] < 1 || flags[i] > MAX_NUMERIC_FLAG) {
            throw new IOException("numeric flag outside 1..65000 at line "
                + lineNumber + ": " + value);
          }
        }
        return flags;
      }
      case LONG: {
        if (text.length() % 2 != 0) {
          throw new IOException("odd long-flag run at line " + lineNumber);
        }
        final int[] flags = new int[text.length() / 2];
        for (int i = 0; i < flags.length; i++) {
          flags[i] = (text.charAt(2 * i) << 16) | text.charAt(2 * i + 1);
        }
        return flags;
      }
      default: {
        // One flag per code point: published dictionaries name affix rules with
        // supplementary characters under FLAG UTF-8, and reading per UTF-16 unit
        // would split such a flag into surrogate code units. A variation selector
        // (U+FE00..U+FE0F) only selects a flag character's presentation and is
        // removed from flag identity.
        final int[] buffer = new int[text.codePointCount(0, text.length())];
        int f = 0;
        for (int i = 0; i < text.length(); ) {
          final int codePoint = text.codePointAt(i);
          i += Character.charCount(codePoint);
          if (codePoint >= 0xFE00 && codePoint <= 0xFE0F) {
            continue;
          }
          buffer[f++] = codePoint;
        }
        return f == buffer.length ? buffer : Arrays.copyOf(buffer, f);
      }
    }
  }

  /**
   * Parses a field that must contain a single flag, such as the flag name in an
   * affix block header.
   *
   * @param text The flag field.
   * @param mode The selected flag encoding.
   * @param lineNumber The source line, for error messages.
   * @return The single parsed flag.
   * @throws IOException Thrown if the field does not encode a single flag.
   */
  private static int parseFlag(String text, FlagMode mode, int lineNumber)
      throws IOException {
    final int[] flags = parseFlags(text, mode, lineNumber);
    if (flags.length != 1) {
      throw new IOException("expected exactly one flag at line " + lineNumber);
    }
    return flags[0];
  }

  /**
   * Splits text into lines with a single character scan, tolerating CRLF endings.
   *
   * @param content The text to split.
   * @return The lines without terminators.
   */
  private static String[] splitLines(String content) {
    final List<String> lines = new ArrayList<>();
    int start = 0;
    for (int i = 0; i <= content.length(); i++) {
      if (i == content.length() || content.charAt(i) == '\n') {
        int end = i;
        if (end > start && content.charAt(end - 1) == '\r') {
          end--;
        }
        lines.add(content.substring(start, end));
        start = i + 1;
      }
    }
    return lines.toArray(new String[0]);
  }

  /**
   * Splits text on a separator character with a single character scan.
   *
   * @param text The text to split.
   * @param separator The separator character.
   * @return The parts between separators, including empty parts.
   */
  private static String[] splitOn(String text, char separator) {
    final List<String> parts = new ArrayList<>();
    int start = 0;
    for (int i = 0; i <= text.length(); i++) {
      if (i == text.length() || text.charAt(i) == separator) {
        parts.add(text.substring(start, i));
        start = i + 1;
      }
    }
    return parts.toArray(new String[0]);
  }

  /**
   * Splits a line on whitespace with a single character scan.
   *
   * @param line The line to split.
   * @return The non-empty whitespace-separated fields.
   */
  private static String[] split(String line) {
    final List<String> parts = new ArrayList<>();
    int start = -1;
    for (int i = 0; i <= line.length(); i++) {
      if (i == line.length() || StringUtil.isWhitespace(line.charAt(i))) {
        if (start >= 0) {
          parts.add(line.substring(start, i));
          start = -1;
        }
      } else if (start < 0) {
        start = i;
      }
    }
    return parts.toArray(new String[0]);
  }
}
