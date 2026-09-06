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

import java.io.ByteArrayOutputStream;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

import opennlp.tools.commons.ThreadSafe;
import opennlp.tools.util.StringUtil;

/**
 * An immutable, in-memory Hunspell-format dictionary loaded from user-supplied
 * {@code .aff} and {@code .dic} files. OpenNLP includes no dictionary data.
 *
 * <p>Supported affix features are {@code PFX} and {@code SFX} rules with strip
 * strings, character-class conditions, cross-product combinations, and continuation
 * classes. {@code COMPLEXPREFIXES} selects 2 prefixes and 1 suffix instead of
 * 1 prefix and 2 suffixes. {@code FLAG}, {@code AF}, {@code AM}, and {@code SET}
 * declarations apply throughout the file. Input and output conversions, ignored
 * characters, case restrictions, compound flags and patterns, and word breaks are
 * applied during stemming. The blocking flags
 * {@code NEEDAFFIX} (also named {@code PSEUDOROOT}), {@code ONLYINCOMPOUND}, and
 * {@code FORBIDDENWORD}, plus {@code CIRCUMFIX} and {@code FULLSTRIP}, are also
 * applied.</p>
 *
 * <p>Loading defaults to {@link LoadMode#STRICT}: unsupported affix directives
 * cause an {@link IOException}. Recognized metadata and suggestion settings that
 * do not affect stemming are ignored. {@link LoadMode#ALLOW_PARTIAL} skips other
 * directives and reports them through {@link #getUnsupportedDirectives()}.
 * Morphological stems use {@code st:}, {@code sp:}, and {@code ds:} fields.
 * {@code SYLLABLENUM} supports Hungarian compound syllable adjustments.
 * {@code LEMMA_PRESENT} is accepted but has no effect, consistent with the
 * reference version. Strict loading validates declarations, not equivalence of
 * all results with native Hunspell.</p>
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
   * Inclusive upper bound on bytes buffered from one affix or dictionary stream
   * during {@link #load(InputStream, InputStream)}. The limit is 64 MiB. Larger
   * streams fail with {@link IOException}.
   */
  public static final int MAX_STREAM_BYTES = 64 * 1024 * 1024;

  /** Controls whether unsupported affix directives prevent dictionary loading. */
  public enum LoadMode {
    /** Reject unsupported directives, including unknown directive names. */
    STRICT,
    /** Skip unsupported directives and record their first source locations. */
    ALLOW_PARTIAL
  }

  /**
   * The first occurrence of an unsupported affix directive.
   *
   * @param directive The directive name.
   * @param source The affix file path, or {@code affix stream} for stream loading.
   * @param lineNumber The one-based source line number.
   */
  public record UnsupportedDirective(String directive, String source, int lineNumber) {

    /**
     * Creates a diagnostic with a directive name and source location.
     *
     * @param directive The nonblank directive name.
     * @param source The nonblank source description.
     * @param lineNumber The positive source line number.
     * @throws IllegalArgumentException If a name is null or blank, or the line
     *     number is not positive.
     */
    public UnsupportedDirective {
      if (directive == null || directive.isBlank()) {
        throw new IllegalArgumentException("directive must not be null or blank");
      }
      if (source == null || source.isBlank()) {
        throw new IllegalArgumentException("source must not be null or blank");
      }
      if (lineNumber < 1) {
        throw new IllegalArgumentException("lineNumber must be positive");
      }
    }
  }

  private static final String AFFIX_STREAM = "affix stream";
  private static final String DICTIONARY_STREAM = "dictionary stream";

  /**
   * One parsed affix rule of a {@code PFX} or {@code SFX} block.
   *
   * @param flag The flag naming the rule's block, which an entry carries to accept it.
   * @param crossProduct Whether the rule may combine with an affix of the opposite kind.
   * @param strip The stem material the rule replaces, restored during analysis.
   * @param affix The surface material the rule adds to the stem.
   * @param condition The condition the stem must satisfy for the rule to apply.
   * @param continuation The flags of the further affixes that may stack on this one.
   * @param suffix Whether the rule adds a suffix.
   * @param morphology The expanded morphological fields, empty when the rule declares none.
   * @param flagText The flag as written in the rule, reported as the {@code fl:} field of
   *                 a rule without morphological fields.
   */
  record Affix(int flag, boolean crossProduct, String strip, String affix,
      AffixCondition condition, int[] continuation, boolean suffix, List<String> morphology,
      String flagText) {

    /** {@return the morphological fields, or the {@code fl:} flag field when none are declared} */
    List<String> analysisFields() {
      return morphology.isEmpty() ? List.of(FLAG_FIELD + flagText) : morphology;
    }

    /**
     * Checks whether a further affix may stack on this one.
     *
     * @param otherFlag The stacking affix's flag.
     * @return {@code true} if this affix's continuation classes allow it.
     */
    boolean allowsContinuation(int otherFlag) {
      return contains(continuation, otherFlag);
    }
  }

  /** The place a part takes in a compound, deciding which positional flag admits it. */
  enum CompoundPosition {
    /** The first part. */
    BEGIN,
    /** Any part between the first and the last. */
    MIDDLE,
    /** The last part. */
    END
  }

  /** The shared empty bucket answered for characters no affix rule is keyed under. */
  private static final List<Affix> NO_AFFIXES = List.of();

  /** The line tag of a prefix block and of every rule line inside it. */
  private static final String PREFIX_TAG = "PFX";

  /** The line tag of a suffix block and of every rule line inside it. */
  private static final String SUFFIX_TAG = "SFX";

  /** The directive that selects the file-wide flag representation. */
  private static final String FLAG_TAG = "FLAG";

  /** The directive that defines the file-wide flag alias table. */
  private static final String ALIAS_TAG = "AF";

  private static final String INPUT_CONVERSION_TAG = "ICONV";
  private static final String OUTPUT_CONVERSION_TAG = "OCONV";
  private static final String IGNORE_TAG = "IGNORE";
  private static final String KEEP_CASE_TAG = "KEEPCASE";
  private static final String COMPLEX_PREFIXES_TAG = "COMPLEXPREFIXES";
  private static final String COMPOUND_RULE_TAG = "COMPOUNDRULE";
  private static final String COMPOUND_PATTERN_TAG = "CHECKCOMPOUNDPATTERN";
  private static final String MORPHOLOGY_ALIAS_TAG = "AM";
  private static final String BREAK_TAG = "BREAK";
  private static final String LANGUAGE_TAG = "LANG";
  private static final String WARNING_TAG = "WARN";
  private static final String FORBID_WARNING_TAG = "FORBIDWARN";
  private static final String CHECK_SHARPS_TAG = "CHECKSHARPS";
  private static final String COMPOUND_ROOT_TAG = "COMPOUNDROOT";
  private static final String FORCE_UPPER_CASE_TAG = "FORCEUCASE";
  private static final String COMPOUND_MORE_SUFFIXES_TAG = "COMPOUNDMORESUFFIXES";
  private static final String SIMPLIFIED_TRIPLE_TAG = "SIMPLIFIEDTRIPLE";
  private static final String SYLLABLE_NUMBER_TAG = "SYLLABLENUM";
  private static final String LEMMA_PRESENT_TAG = "LEMMA_PRESENT";
  private static final String COMPOUND_SYLLABLE_TAG = "COMPOUNDSYLLABLE";
  private static final String REPLACEMENT_TAG = "REP";
  private static final String STEM_FIELD = "st:";
  private static final String SURFACE_PREFIX_FIELD = "sp:";
  private static final String DERIVATIONAL_SUFFIX_FIELD = "ds:";
  private static final String FLAG_FIELD = "fl:";

  private static final List<String> DEFAULT_WORD_BREAKS = List.of("-", "^-", "-$");


  /** Prefix used by comment lines. */
  private static final String COMMENT_PREFIX = "#";

  /** The affix-file directive that declares the character encoding. */
  private static final String SET_TAG = "SET";

  /** The {@code SET} directive followed by a space. */
  private static final String SET_PREFIX = SET_TAG + " ";

  /** The {@code SET} directive followed by a tab. */
  private static final String SET_TAB_PREFIX = SET_TAG + "\t";

  /** The affix format's marker for absent strip or affix material. */
  private static final String NO_MATERIAL = "0";

  /**
   * Largest flag value permitted by {@code FLAG num}, as specified by the
   * <a href="https://github.com/hunspell/hunspell/blob/e184e22c51fe213f4490e9b36998f0ad3e5e606b/man/hunspell.5#L133-L139">
   * Hunspell format manual</a>.
   */
  private static final int MAX_NUMERIC_FLAG = 65_000;

  /** Largest {@code COMPOUNDMIN} value that can be doubled without overflow. */
  private static final int MAX_COMPOUND_MIN = Integer.MAX_VALUE / 2;

  private final Map<String, List<int[]>> entries;
  /**
   * The capitalized forms of mixed-case and flagged all-uppercase entries, which the
   * reference implementation adds as hidden homonyms so that all-uppercase input
   * matches them. Keyed like {@link #entries}, sharing the entry flag arrays.
   */
  private final Map<String, List<int[]>> hiddenEntries;
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
  private final String ignoredCharacters;
  private final HunspellConversion inputConversion;
  private final HunspellConversion outputConversion;
  private final Map<int[], List<String>> morphology;
  private final boolean complexPrefixes;
  private final int keepCase;
  private final int warningFlag;
  private final boolean forbidWarn;
  private final boolean checkSharps;
  private final boolean turkicCase;
  private final boolean hungarian;
  private final boolean syllableNumber;
  private final List<HunspellCompoundRule> compoundRules;
  private final List<CompoundPattern> compoundPatterns;
  private final int compoundRoot;
  private final int forceUpperCase;
  private final boolean compoundMoreSuffixes;
  private final boolean simplifiedTriple;
  private final boolean checkCompoundRep;
  private final HunspellConversion replacements;
  private final int maxCompoundSyllables;
  private final String compoundVowels;
  private final List<String> wordBreaks;
  private final List<UnsupportedDirective> unsupportedDirectives;

  /**
   * Initializes the dictionary from the two parsed files.
   *
   * @param entries The words mapped to the flag sets of their entries.
   * @param affix The parsed affix file.
   * @param unsupportedDirectives The skipped directives in encounter order.
   */
  private HunspellDictionary(Map<String, List<int[]>> entries, AffixFile affix,
      List<UnsupportedDirective> unsupportedDirectives) {
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
    this.ignoredCharacters = affix.ignoredCharacters;
    this.inputConversion = affix.inputConversion;
    this.outputConversion = affix.outputConversion;
    this.morphology = Map.copyOf(affix.entryMorphology);
    this.complexPrefixes = affix.complexPrefixes;
    this.keepCase = affix.keepCase;
    this.warningFlag = affix.warningFlag;
    this.forbidWarn = affix.forbidWarn;
    this.checkSharps = affix.checkSharps;
    this.compoundRules = List.copyOf(affix.compoundRules);
    this.compoundPatterns = List.copyOf(affix.compoundPatterns);
    this.compoundRoot = affix.compoundRoot;
    this.forceUpperCase = affix.forceUpperCase;
    this.compoundMoreSuffixes = affix.compoundMoreSuffixes;
    this.simplifiedTriple = affix.simplifiedTriple;
    this.checkCompoundRep = affix.checkCompoundRep;
    this.replacements = affix.checkCompoundRep
        ? affix.replacements.withPhoneticFields(entries, affix.entryMorphology) : affix.replacements;
    this.maxCompoundSyllables = affix.maxCompoundSyllables;
    this.compoundVowels = affix.compoundVowels;
    this.wordBreaks = List.copyOf(affix.wordBreaks);
    this.turkicCase = affix.language.equals("tr") || affix.language.startsWith("tr_")
        || affix.language.equals("az") || affix.language.startsWith("az_")
        || affix.language.equals("crh") || affix.language.startsWith("crh_");
    this.hungarian = affix.language.equals("hu") || affix.language.startsWith("hu_");
    this.syllableNumber = affix.syllableNumber;
    this.entries = entries;
    this.hiddenEntries = hiddenCapitalizedEntries(entries);
    this.unsupportedDirectives = List.copyOf(unsupportedDirectives);
    // A material-bearing rule can only be undone from a word whose boundary
    // character matches its affix material, so bucketing by that character
    // narrows each scan to one bucket plus the strip-only rules.
    final List<Affix> suffixesWithout = new ArrayList<>();
    this.suffixesByLast = bucketByBoundary(affix.suffixes, true, suffixesWithout);
    this.suffixesWithoutMaterial = List.copyOf(suffixesWithout);
    final List<Affix> prefixesWithout = new ArrayList<>();
    this.prefixesByFirst = bucketByBoundary(affix.prefixes, false, prefixesWithout);
    this.prefixesWithoutMaterial = List.copyOf(prefixesWithout);
  }

  /**
   * An immutable index of affix rules keyed by the boundary code point of their affix
   * material, answering each lookup by binary search so the per-word scans in
   * {@link HunspellStemmer} allocate nothing.
   */
  private static final class BoundaryIndex {

    /** The boundary code points, sorted ascending. */
    private final int[] boundaries;
    /** The rule bucket for each boundary, aligned with {@link #boundaries}. */
    private final List<List<Affix>> buckets;

    /**
     * Initializes the index from mutable buckets, freezing each one.
     *
     * @param byBoundary The rule buckets keyed by boundary code point.
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
     * @return The bucket, possibly empty. Never {@code null}.
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
   * @return The rules indexed by their boundary code point. Never {@code null}.
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
   * Loads affix and dictionary files using {@link LoadMode#STRICT}.
   *
   * @param affixFile The {@code .aff} affix file. Must not be {@code null}.
   * @param dictionaryFile The {@code .dic} word list. Must not be {@code null}.
   * @return The loaded dictionary. Never {@code null}.
   * @throws IOException Thrown if reading fails, a file is malformed, or an affix
   *     directive is unsupported.
   * @throws IllegalArgumentException Thrown if a parameter is {@code null}.
   */
  public static HunspellDictionary load(Path affixFile, Path dictionaryFile)
      throws IOException {
    return load(affixFile, dictionaryFile, LoadMode.STRICT);
  }

  /**
   * Loads affix and dictionary files with the selected directive policy.
   *
   * @param affixFile The non-null {@code .aff} affix file.
   * @param dictionaryFile The non-null {@code .dic} word list.
   * @param mode The non-null loading policy.
   * @return The loaded dictionary.
   * @throws IOException If reading fails, a stream exceeds {@link #MAX_STREAM_BYTES},
   *     content is malformed, or strict loading encounters an unsupported directive.
   * @throws IllegalArgumentException If a parameter is null.
   */
  public static HunspellDictionary load(Path affixFile, Path dictionaryFile,
      LoadMode mode) throws IOException {
    if (affixFile == null) {
      throw new IllegalArgumentException("affixFile must not be null");
    }
    if (dictionaryFile == null) {
      throw new IllegalArgumentException("dictionaryFile must not be null");
    }
    if (mode == null) {
      throw new IllegalArgumentException("mode must not be null");
    }
    try (InputStream affix = Files.newInputStream(affixFile);
         InputStream dictionary = Files.newInputStream(dictionaryFile)) {
      return loadStreams(affix, dictionary, mode, affixFile.toString());
    }
  }

  /**
   * Loads affix and dictionary streams using {@link LoadMode#STRICT}.
   * Each stream is buffered up to
   * {@link #MAX_STREAM_BYTES} bytes; a larger stream fails with {@link IOException}.
   *
   * @param affixStream The {@code .aff} affix content. Must not be {@code null}. Not
   *                    closed.
   * @param dictionaryStream The {@code .dic} word list content. Must not be
   *                         {@code null}. Not closed.
   * @return The loaded dictionary. Never {@code null}.
   * @throws IOException Thrown if reading fails, a stream exceeds
   *     {@link #MAX_STREAM_BYTES}, the content is malformed, or an affix directive
   *     is unsupported.
   * @throws IllegalArgumentException Thrown if a parameter is {@code null}.
   */
  public static HunspellDictionary load(InputStream affixStream,
      InputStream dictionaryStream) throws IOException {
    return load(affixStream, dictionaryStream, LoadMode.STRICT);
  }

  /**
   * Loads affix and dictionary streams with the selected directive policy.
   * The streams are not closed.
   *
   * @param affixStream The non-null {@code .aff} affix content.
   * @param dictionaryStream The non-null {@code .dic} word-list content.
   * @param mode The non-null loading policy.
   * @return The loaded dictionary, with diagnostics for skipped unsupported directives.
   * @throws IOException If reading fails, a stream exceeds {@link #MAX_STREAM_BYTES},
   *     content is malformed, or strict loading encounters an unsupported directive.
   * @throws IllegalArgumentException If a parameter is null.
   */
  public static HunspellDictionary load(InputStream affixStream,
      InputStream dictionaryStream, LoadMode mode) throws IOException {
    if (affixStream == null) {
      throw new IllegalArgumentException("affixStream must not be null");
    }
    if (dictionaryStream == null) {
      throw new IllegalArgumentException("dictionaryStream must not be null");
    }
    if (mode == null) {
      throw new IllegalArgumentException("mode must not be null");
    }
    return loadStreams(affixStream, dictionaryStream, mode, AFFIX_STREAM);
  }

  /**
   * Returns skipped unsupported directives, one entry per name in encounter order.
   * Recognized settings outside stemming, such as suggestion tables, are excluded.
   *
   * @return An immutable list; empty for a dictionary loaded in strict mode.
   */
  public List<UnsupportedDirective> getUnsupportedDirectives() {
    return unsupportedDirectives;
  }

  /**
   * Loads validated streams without closing them.
   *
   * @param affixStream The affix input.
   * @param dictionaryStream The word-list input.
   * @param mode The directive policy.
   * @param source The affix source used in unsupported-directive diagnostics.
   * @return The parsed dictionary.
   * @throws IOException If reading, validation, or parsing fails.
   */
  private static HunspellDictionary loadStreams(InputStream affixStream,
      InputStream dictionaryStream, LoadMode mode, String source) throws IOException {
    byte[] affixBytes = readBounded(affixStream, MAX_STREAM_BYTES, AFFIX_STREAM);
    final Charset charset = declaredCharset(affixBytes);
    final List<UnsupportedDirective> unsupported = maskIgnoredAffixLines(
        affixBytes, mode, source);
    final boolean rawUtf8Flags = StandardCharsets.UTF_8.equals(charset)
        && !usesUnicodeOrNumericFlags(affixBytes);
    affixBytes = normalizeUtf8ByteFlags(affixBytes, charset);
    final AffixFile affix = parseAffix(decode(affixBytes, charset, AFFIX_STREAM));
    byte[] dictionaryBytes = readBounded(dictionaryStream, MAX_STREAM_BYTES,
        DICTIONARY_STREAM);
    if (rawUtf8Flags) {
      dictionaryBytes = normalizeDictionaryByteFlags(dictionaryBytes);
    }
    final Map<String, List<int[]>> entries = parseWordList(
        decode(dictionaryBytes, charset, DICTIONARY_STREAM),
        affix);
    return new HunspellDictionary(entries, affix, unsupported);
  }

  /**
   * Replaces comments and unused directive lines with ASCII spaces before strict
   * decoding. Published dictionaries sometimes retain legacy-encoded metadata despite
   * a {@code SET UTF-8} declaration. Line endings and byte positions remain unchanged,
   * while malformed bytes in parsed directives are still reported.
   *
   * @param bytes The buffered affix file, modified in place.
   * @param mode The directive policy applied before masking.
   * @param source The affix source description.
   * @return Unsupported directives skipped in partial mode, in encounter order.
   * @throws IOException If strict loading encounters an unsupported directive.
   */
  private static List<UnsupportedDirective> maskIgnoredAffixLines(byte[] bytes,
      LoadMode mode, String source) throws IOException {
    final Map<String, UnsupportedDirective> unsupported = new LinkedHashMap<>();
    final boolean useReplacements = hasAffixDirective(bytes, "CHECKCOMPOUNDREP");
    int lineStart = 0;
    int lineNumber = 1;
    for (int i = 0; i <= bytes.length; i++) {
      if (i == bytes.length || bytes[i] == '\n' || bytes[i] == '\r') {
        int fieldStart = lineStart;
        if (lineStart == 0 && i >= 3 && bytes[0] == (byte) 0xef
            && bytes[1] == (byte) 0xbb && bytes[2] == (byte) 0xbf) {
          fieldStart = 3;
        }
        while (fieldStart < i && isAsciiFieldSpace(bytes[fieldStart])) {
          fieldStart++;
        }
        int fieldEnd = fieldStart;
        while (fieldEnd < i && !isAsciiFieldSpace(bytes[fieldEnd])) {
          fieldEnd++;
        }
        boolean parsed = false;
        if (fieldStart < fieldEnd && bytes[fieldStart] != '#') {
          final String directive = new String(bytes, fieldStart,
              fieldEnd - fieldStart, StandardCharsets.US_ASCII);
          if (isParsedAffixDirective(directive) && (!REPLACEMENT_TAG.equals(directive) || useReplacements)) {
            maskInlineComment(bytes, fieldStart, i, directive);
            parsed = true;
          } else if (!isIgnoredAffixDirective(directive)) {
            if (mode == LoadMode.STRICT) {
              throw new IOException("unsupported affix directive " + directive
                  + " in " + source + " at line " + lineNumber
                  + "; use LoadMode.ALLOW_PARTIAL to load without this behavior");
            }
            if (!unsupported.containsKey(directive)) {
              unsupported.put(directive,
                  new UnsupportedDirective(directive, source, lineNumber));
            }
          }
        }
        if (!parsed) {
          Arrays.fill(bytes, lineStart, i, (byte) ' ');
        }
        if (i < bytes.length && bytes[i] == '\r'
            && i + 1 < bytes.length && bytes[i + 1] == '\n') {
          i++;
        }
        lineStart = i + 1;
        lineNumber++;
      }
    }
    return List.copyOf(unsupported.values());
  }

  /**
   * Finds a directive before metadata is removed or decoded.
   *
   * @param bytes The affix content.
   * @param directive The requested first field.
   * @return Whether the directive occurs outside a comment.
   */
  private static boolean hasAffixDirective(byte[] bytes, String directive) {
    final int[] starts = new int[1];
    final int[] ends = new int[1];
    int from = bytes.length >= 3 && bytes[0] == (byte) 0xef
        && bytes[1] == (byte) 0xbb && bytes[2] == (byte) 0xbf ? 3 : 0;
    for (int to = from; to <= bytes.length; to++) {
      if (to == bytes.length || bytes[to] == '\r' || bytes[to] == '\n') {
        if (findAsciiFields(bytes, from, to, starts, ends) > 0
            && directive.equals(asciiField(bytes, starts[0], ends[0]))) {
          return true;
        }
        from = to + 1;
      }
    }
    return false;
  }

  /**
   * Identifies metadata, suggestion and command-line settings unused by stemming.
   * REP is parsed separately when CHECKCOMPOUNDREP makes it affect recognition.
   *
   * @param directive The affix directive name.
   * @return Whether the directive can be ignored by the affix stemmer.
   * @see <a href="https://github.com/hunspell/hunspell/blob/e184e22c51fe213f4490e9b36998f0ad3e5e606b/man/hunspell.5">Hunspell format</a>
   */
  private static boolean isIgnoredAffixDirective(String directive) {
    return switch (directive) {
      case "NAME", "HOME", "VERSION", "KEY", "TRY", REPLACEMENT_TAG, "MAP", "PHONE",
          "NOSUGGEST", "MAXCPDSUGS", "MAXNGRAMSUGS", "MAXDIFF", "ONLYMAXDIFF",
          "NOSPLITSUGS", "SUGSWITHDOTS", "SUBSTANDARD", "WORDCHARS",
          "COMPOUNDFIRST", "COMPOUNDLAST", "ONLYROOT", "HU_KOTOHANGZO", "GENERATE" -> true;
      default -> false;
    };
  }

  /**
   * Tests an affix field separator.
   *
   * @param value The input byte.
   * @return Whether the byte separates fields.
   */
  private static boolean isAsciiFieldSpace(byte value) {
    return value == ' ' || value == '\t' || value == '\f';
  }

  /**
   * Replaces a trailing comment with spaces. A number sign starts a comment only in a
   * field the directive does not consume, because the format allows {@code #} as a
   * flag, a separator, affix material, and conversion text. The reference
   * implementation ignores the fields after the ones it reads.
   *
   * @param bytes The mutable file content.
   * @param from The first byte of the directive.
   * @param to The exclusive end of the line.
   * @param directive The directive name.
   */
  private static void maskInlineComment(byte[] bytes, int from, int to, String directive) {
    final List<int[]> fields = new ArrayList<>();
    for (int i = from; i < to;) {
      while (i < to && isAsciiFieldSpace(bytes[i])) {
        i++;
      }
      final int start = i;
      while (i < to && !isAsciiFieldSpace(bytes[i])) {
        i++;
      }
      if (i > start) {
        fields.add(new int[] {start, i});
      }
    }
    final int firstComment = firstCommentField(bytes, fields, directive);
    for (int index = firstComment; index < fields.size(); index++) {
      if (bytes[fields.get(index)[0]] == '#') {
        Arrays.fill(bytes, fields.get(index)[0], to, (byte) ' ');
        return;
      }
    }
  }

  /**
   * Finds the first field index a trailing comment may occupy.
   *
   * @param bytes The file content.
   * @param fields The field boundaries of the line.
   * @param directive The directive name.
   * @return The index after the fields the directive consumes.
   */
  private static int firstCommentField(byte[] bytes, List<int[]> fields, String directive) {
    return switch (directive) {
      case PREFIX_TAG, SUFFIX_TAG -> isAffixHeader(bytes, fields) ? 4 : 5;
      case REPLACEMENT_TAG, INPUT_CONVERSION_TAG, OUTPUT_CONVERSION_TAG,
          COMPOUND_PATTERN_TAG, MORPHOLOGY_ALIAS_TAG -> Integer.MAX_VALUE;
      case COMPOUND_SYLLABLE_TAG -> 3;
      default -> 2;
    };
  }

  /**
   * Distinguishes an affix block header, which carries a cross-product marker and a
   * rule count, from a rule line.
   *
   * @param bytes The file content.
   * @param fields The field boundaries of the line.
   * @return Whether the line is a block header.
   */
  private static boolean isAffixHeader(byte[] bytes, List<int[]> fields) {
    if (fields.size() < 4 || fields.get(2)[1] - fields.get(2)[0] != 1
        || (bytes[fields.get(2)[0]] != 'Y' && bytes[fields.get(2)[0]] != 'N')) {
      return false;
    }
    for (int i = fields.get(3)[0]; i < fields.get(3)[1]; i++) {
      if (bytes[i] < '0' || bytes[i] > '9') {
        return false;
      }
    }
    return true;
  }

  /**
   * Tests whether a directive is supported by the parser.
   *
   * @param directive The directive name.
   * @return Whether the parser processes the directive's fields.
   */
  private static boolean isParsedAffixDirective(String directive) {
    return switch (directive) {
      case SET_TAG, FLAG_TAG, ALIAS_TAG, PREFIX_TAG, SUFFIX_TAG,
          "COMPOUNDFLAG", "COMPOUNDBEGIN", "COMPOUNDMIDDLE", "COMPOUNDEND",
          "COMPOUNDPERMITFLAG", "COMPOUNDFORBIDFLAG", "NEEDAFFIX", "PSEUDOROOT",
          "ONLYINCOMPOUND", "FORBIDDENWORD", "CIRCUMFIX", "COMPOUNDMIN",
          "COMPOUNDWORDMAX", "CHECKCOMPOUNDDUP", "CHECKCOMPOUNDCASE",
          "CHECKCOMPOUNDTRIPLE", "FULLSTRIP", INPUT_CONVERSION_TAG, OUTPUT_CONVERSION_TAG,
          IGNORE_TAG, MORPHOLOGY_ALIAS_TAG, COMPLEX_PREFIXES_TAG, KEEP_CASE_TAG, WARNING_TAG,
          FORBID_WARNING_TAG, LANGUAGE_TAG, CHECK_SHARPS_TAG,
          COMPOUND_RULE_TAG, COMPOUND_ROOT_TAG, FORCE_UPPER_CASE_TAG, COMPOUND_MORE_SUFFIXES_TAG,
          SIMPLIFIED_TRIPLE_TAG, "CHECKCOMPOUNDREP", COMPOUND_PATTERN_TAG,
          COMPOUND_SYLLABLE_TAG, SYLLABLE_NUMBER_TAG, LEMMA_PRESENT_TAG, REPLACEMENT_TAG, BREAK_TAG -> true;
      default -> false;
    };
  }

  /**
   * Converts raw one-byte flags in a UTF-8 affix file to equivalent Unicode code
   * points before decoding. Hunspell's default and {@code long} flag modes operate on
   * bytes, and published UTF-8 dictionaries can therefore contain non-UTF-8 bytes in
   * flag fields. Text, conditions, and affix material remain subject to strict UTF-8
   * decoding.
   *
   * @param bytes The affix file after unused lines have been masked.
   * @param charset The encoding selected by {@code SET}.
   * @return The content with raw flag bytes represented as valid UTF-8.
   */
  private static byte[] normalizeUtf8ByteFlags(byte[] bytes, Charset charset) {
    if (!StandardCharsets.UTF_8.equals(charset) || usesUnicodeOrNumericFlags(bytes)) {
      return bytes;
    }
    final ByteArrayOutputStream normalized = new ByteArrayOutputStream(bytes.length);
    int lineStart = 0;
    for (int i = 0; i <= bytes.length; i++) {
      if (i == bytes.length || bytes[i] == '\n' || bytes[i] == '\r') {
        writeNormalizedFlagLine(normalized, bytes, lineStart, i);
        if (i < bytes.length) {
          normalized.write(bytes[i]);
        }
        lineStart = i + 1;
      }
    }
    return normalized.toByteArray();
  }

  /**
   * Tests for UTF-8 or numeric flag encoding.
   *
   * @param bytes The affix content.
   * @return Whether {@code FLAG UTF-8} or {@code FLAG num} is configured.
   */
  private static boolean usesUnicodeOrNumericFlags(byte[] bytes) {
    final int[] starts = new int[5];
    final int[] fieldEnds = new int[5];
    int lineStart = 0;
    for (int i = 0; i <= bytes.length; i++) {
      if (i == bytes.length || bytes[i] == '\n' || bytes[i] == '\r') {
        final int count = findAsciiFields(bytes, lineStart, i, starts, fieldEnds);
        if (count >= 2 && FLAG_TAG.equals(
            asciiField(bytes, starts[0], fieldEnds[0]))) {
          final String mode = asciiField(bytes, starts[1], fieldEnds[1]);
          return "UTF-8".equals(mode) || "num".equals(mode);
        }
        lineStart = i + 1;
      }
    }
    return false;
  }

  /**
   * Writes one affix line, converting high bytes only within raw flag fields.
   *
   * @param target The converted content destination.
   * @param bytes The source file content.
   * @param lineStart The first byte of the line.
   * @param lineEnd The exclusive end of the line.
   */
  private static void writeNormalizedFlagLine(ByteArrayOutputStream target,
      byte[] bytes, int lineStart, int lineEnd) {
    final int[] starts = new int[5];
    final int[] fieldEnds = new int[5];
    final int count = findAsciiFields(bytes, lineStart, lineEnd, starts, fieldEnds);
    int firstFlagStart = -1;
    int firstFlagEnd = -1;
    int continuationStart = -1;
    int continuationEnd = -1;
    if (count >= 2) {
      final String directive = asciiField(bytes, starts[0], fieldEnds[0]);
      if (ALIAS_TAG.equals(directive) || COMPOUND_RULE_TAG.equals(directive)
          || PREFIX_TAG.equals(directive)
          || SUFFIX_TAG.equals(directive) || isSingleFlagDirective(directive)) {
        firstFlagStart = starts[1];
        firstFlagEnd = fieldEnds[1];
      }
      if (count >= 4 && (PREFIX_TAG.equals(directive) || SUFFIX_TAG.equals(directive))) {
        for (int i = starts[3]; i < fieldEnds[3]; i++) {
          if (bytes[i] == '/') {
            continuationStart = i + 1;
            continuationEnd = fieldEnds[3];
            break;
          }
        }
      }
      if (count >= 3 && COMPOUND_PATTERN_TAG.equals(directive)) {
        for (int i = starts[1]; i < fieldEnds[1]; i++) {
          if (bytes[i] == '/') {
            firstFlagStart = i + 1;
            firstFlagEnd = fieldEnds[1];
            break;
          }
        }
        for (int i = starts[2]; i < fieldEnds[2]; i++) {
          if (bytes[i] == '/') {
            continuationStart = i + 1;
            continuationEnd = fieldEnds[2];
            break;
          }
        }
      }
    }
    for (int i = lineStart; i < lineEnd; i++) {
      final boolean flagByte = i >= firstFlagStart && i < firstFlagEnd
          || i >= continuationStart && i < continuationEnd;
      writeNormalizedByte(target, bytes[i], flagByte);
    }
  }

  /**
   * Converts raw flag bytes after the flag separator of each dictionary entry.
   * Word text and morphology fields remain subject to strict UTF-8 decoding.
   *
   * @param bytes The buffered dictionary file.
   * @return The content with raw flag bytes represented as valid UTF-8.
   */
  private static byte[] normalizeDictionaryByteFlags(byte[] bytes) {
    final ByteArrayOutputStream normalized = new ByteArrayOutputStream(bytes.length);
    int lineStart = 0;
    for (int i = 0; i <= bytes.length; i++) {
      if (i == bytes.length || bytes[i] == '\n' || bytes[i] == '\r') {
        int flagStart = -1;
        int flagEnd = -1;
        for (int cursor = lineStart + 1; cursor < i; cursor++) {
          if (bytes[cursor] == '/' && bytes[cursor - 1] != '\\') {
            flagStart = cursor + 1;
            flagEnd = flagStart;
            while (flagEnd < i && bytes[flagEnd] != ' ' && bytes[flagEnd] != '\t') {
              flagEnd++;
            }
            break;
          }
        }
        for (int cursor = lineStart; cursor < i; cursor++) {
          writeNormalizedByte(normalized, bytes[cursor],
              cursor >= flagStart && cursor < flagEnd);
        }
        if (i < bytes.length) {
          normalized.write(bytes[i]);
        }
        lineStart = i + 1;
      }
    }
    return normalized.toByteArray();
  }

  /**
   * Writes a raw byte, converting a high flag byte to a UTF-8 code point.
   *
   * @param target The converted content destination.
   * @param source The source byte.
   * @param flagByte Whether this byte represents a flag.
   */
  private static void writeNormalizedByte(ByteArrayOutputStream target, byte source,
      boolean flagByte) {
    final int value = source & 0xff;
    if (flagByte && value >= 0x80) {
      target.write(value < 0xc0 ? 0xc2 : 0xc3);
      target.write(value < 0xc0 ? value : value - 0x40);
    } else {
      target.write(value);
    }
  }

  /**
   * Tests for a directive with one flag argument.
   *
   * @param directive The directive name.
   * @return Whether the value is one Hunspell flag.
   */
  private static boolean isSingleFlagDirective(String directive) {
    return switch (directive) {
      case "COMPOUNDFLAG", "COMPOUNDBEGIN", "COMPOUNDMIDDLE", "COMPOUNDEND",
          "COMPOUNDPERMITFLAG", "COMPOUNDFORBIDFLAG", "NEEDAFFIX", "PSEUDOROOT",
          "ONLYINCOMPOUND", "FORBIDDENWORD", "CIRCUMFIX", KEEP_CASE_TAG, WARNING_TAG,
          COMPOUND_ROOT_TAG, FORCE_UPPER_CASE_TAG, LEMMA_PRESENT_TAG -> true;
      default -> false;
    };
  }

  /**
   * Finds the fields needed to classify one raw line.
   *
   * @param bytes The source content.
   * @param from The first byte of the line.
   * @param to The exclusive end of the line.
   * @param starts The destination for field start offsets.
   * @param fieldEnds The destination for exclusive field end offsets.
   * @return The number of fields found, limited to the destination array length.
   */
  private static int findAsciiFields(byte[] bytes, int from, int to,
      int[] starts, int[] fieldEnds) {
    int count = 0;
    int cursor = from;
    while (cursor < to && count < starts.length) {
      while (cursor < to && isAsciiFieldSpace(bytes[cursor])) {
        cursor++;
      }
      if (cursor == to) {
        break;
      }
      starts[count] = cursor;
      while (cursor < to && !isAsciiFieldSpace(bytes[cursor])) {
        cursor++;
      }
      fieldEnds[count] = cursor;
      count++;
    }
    return count;
  }

  /**
   * Extracts one ASCII field.
   *
   * @param bytes The source content.
   * @param from The first byte of the field.
   * @param to The exclusive end of the field.
   * @return The field text.
   */
  private static String asciiField(byte[] bytes, int from, int to) {
    return new String(bytes, from, to - from, StandardCharsets.US_ASCII);
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
   * Reads an input stream into a byte array, failing when more than {@code maxBytes}
   * arrive.
   *
   * @param in The stream to read. Not closed.
   * @param maxBytes The inclusive upper bound on buffered bytes.
   * @param label The stream name used in the error message.
   * @return The buffered bytes. Never {@code null}.
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
   * Looks up a word's flag sets among the listed entries.
   *
   * @param word The word exactly as listed.
   * @return The flag sets of all matching entries, or {@code null} when absent.
   */
  List<int[]> lookup(String word) {
    return lookup(word, false);
  }

  /**
   * Looks up a word's flag sets, optionally including the hidden capitalized forms
   * that all-uppercase input may match.
   *
   * @param word The word exactly as listed or in hidden capitalized form.
   * @param includeHidden Whether hidden capitalized forms count, which the reference
   *                      implementation allows for all-uppercase input outside compounds.
   * @return The flag sets of all matching entries, or {@code null} when absent.
   */
  List<int[]> lookup(String word, boolean includeHidden) {
    final List<int[]> found = entries.get(word);
    final List<int[]> hidden = includeHidden ? hiddenEntries.get(word) : null;
    if (found == null && hidden == null) {
      return null;
    }
    final List<int[]> copy = new ArrayList<>();
    copyFlags(found, copy);
    copyFlags(hidden, copy);
    return copy;
  }

  /**
   * Appends defensive copies of flag sets.
   *
   * @param source The flag sets to copy, or {@code null}.
   * @param target The destination.
   */
  private static void copyFlags(List<int[]> source, List<int[]> target) {
    if (source != null) {
      for (final int[] flags : source) {
        target.add(flags.clone());
      }
    }
  }

  /**
   * The capitalization classes of the reference implementation.
   */
  enum CaseType {
    /** No uppercase letter. */
    NOCAP,
    /** Exactly one uppercase letter, at the start. */
    INITCAP,
    /** Every letter uppercase. */
    ALLCAP,
    /** An uppercase start and a further uppercase letter among lowercase ones. */
    HUHINITCAP,
    /** Uppercase letters after a lowercase start or among lowercase ones. */
    HUHCAP
  }

  /**
   * Classifies a word's capitalization as the reference implementation does, judging
   * the initial by the first character.
   *
   * @param word The word to classify.
   * @return The capitalization class.
   */
  static CaseType caseType(String word) {
    int letters = 0;
    int uppers = 0;
    boolean firstUpper = false;
    for (int i = 0; i < word.length();) {
      final int point = word.codePointAt(i);
      if (Character.isLowerCase(point)) {
        letters++;
      } else if (Character.isUpperCase(point) || Character.isTitleCase(point)) {
        firstUpper |= i == 0;
        uppers++;
        letters++;
      }
      i += Character.charCount(point);
    }
    if (uppers == 0) {
      return CaseType.NOCAP;
    }
    if (uppers == 1 && firstUpper) {
      return CaseType.INITCAP;
    }
    if (uppers == letters) {
      return CaseType.ALLCAP;
    }
    return firstUpper ? CaseType.HUHINITCAP : CaseType.HUHCAP;
  }

  /**
   * Derives the hidden capitalized forms the reference implementation adds for
   * mixed-case entries and for all-uppercase entries carrying flags, so that
   * all-uppercase input such as {@code IPOD} or {@code UNICEF'S} matches them. A form
   * that is itself listed is not added, and forbidden entries contribute none.
   *
   * @param listed The words mapped to the flag sets of their entries.
   * @return The capitalized forms mapped to the flag sets they share with their entries.
   */
  private Map<String, List<int[]>> hiddenCapitalizedEntries(Map<String, List<int[]>> listed) {
    final Map<String, List<int[]>> hidden = new HashMap<>();
    for (final Map.Entry<String, List<int[]>> entry : listed.entrySet()) {
      final CaseType type = caseType(entry.getKey());
      if (type != CaseType.HUHCAP && type != CaseType.HUHINITCAP && type != CaseType.ALLCAP) {
        continue;
      }
      final String capitalized = upperCaseInitial(lowerCase(entry.getKey()));
      if (listed.containsKey(capitalized)) {
        continue;
      }
      for (final int[] flags : entry.getValue()) {
        if ((type == CaseType.ALLCAP && flags.length == 0) || contains(flags, forbiddenWord)) {
          continue;
        }
        hidden.computeIfAbsent(capitalized, key -> new ArrayList<>(1)).add(flags);
      }
    }
    return hidden;
  }

  /**
   * The flag sets of a root's entries, listed or hidden capitalized.
   *
   * @param root The entry spelling a reading selected.
   * @return The flag sets in list order. Never {@code null}.
   */
  private List<int[]> homonyms(String root) {
    final List<int[]> found = entries.get(root);
    return found != null ? found : hiddenEntries.getOrDefault(root, List.of());
  }

  /**
   * The suffix rules whose affix material ends in the given code point, which are the
   * only material-bearing rules that can be undone from a word ending in it.
   *
   * @param last The word's last code point.
   * @return The bucket, possibly empty. Never {@code null}.
   */
  List<Affix> suffixesEndingWith(int last) {
    return suffixesByLast.bucket(last);
  }

  /** {@return the strip-only suffix rules, applicable to any word} Never {@code null}. */
  List<Affix> suffixesWithoutMaterial() {
    return suffixesWithoutMaterial;
  }

  /**
   * The prefix rules whose affix material starts with the given code point, which are
   * the only material-bearing rules that can be undone from a word starting with it.
   *
   * @param first The word's first code point.
   * @return The bucket, possibly empty. Never {@code null}.
   */
  List<Affix> prefixesStartingWith(int first) {
    return prefixesByFirst.bucket(first);
  }

  /** {@return the strip-only prefix rules, applicable to any word} Never {@code null}. */
  List<Affix> prefixesWithoutMaterial() {
    return prefixesWithoutMaterial;
  }

  /** {@return whether the affix file declares any compounding flag at all} */
  boolean compoundsDeclared() {
    return compoundFlag != 0 || compoundBegin != 0 || compoundEnd != 0
        || compoundMiddle != 0 || !compoundRules.isEmpty();
  }

  /** {@return the parsed independent compound flag patterns} */
  List<HunspellCompoundRule> compoundRules() {
    return compoundRules;
  }

  /** {@return the compound-boundary patterns} */
  List<CompoundPattern> compoundPatterns() {
    return compoundPatterns;
  }

  /** {@return whether simplified triple-letter junctions are enabled} */
  boolean simplifiedTriple() {
    return simplifiedTriple;
  }

  /** {@return whether compounds permit an additional suffix level} */
  boolean compoundMoreSuffixes() {
    return compoundMoreSuffixes;
  }

  /** {@return the word separators, with optional start and end anchors} */
  List<String> wordBreaks() {
    return wordBreaks;
  }

  /**
   * Counts an entry toward COMPOUNDWORDMAX.
   *
   * @param flags The selected entry flags.
   * @param affixes The applied rules.
   * @return One unit, or an additional unit for COMPOUNDROOT.
   */
  int compoundUnits(int[] flags, List<Affix> affixes) {
    int units = contains(flags, compoundRoot) ? 2 : 1;
    if (hungarian) {
      for (Affix affix : affixes) {
        if (!affix.suffix() && countSyllables(affix.affix()) > 1) {
          units++;
        }
      }
    }
    return units;
  }

  /**
   * Checks word and syllable limits on a completed compound.
   *
   * @param syllables The syllable count after language adjustments.
   * @param units The compound-word count.
   * @return Whether the limits permit the compound.
   */
  boolean compoundSizeAllowed(int syllables, int units) {
    if (compoundWordMax == 0 || units <= compoundWordMax) {
      return true;
    }
    if (maxCompoundSyllables == 0) {
      return false;
    }
    return syllables <= maxCompoundSyllables;
  }

  /**
   * Calculates a component's contribution to the compound syllable limit.
   *
   * @param part The component text.
   * @param flags The selected entry flags.
   * @param affixes The applied rules in order.
   * @param last Whether this is the closing component.
   * @return The adjusted vowel count.
   */
  int compoundSyllables(String part, int[] flags, List<Affix> affixes, boolean last) {
    if (!hungarian) {
      return last ? countSyllables(part) : 0;
    }
    int result = countSyllables(part);
    if (!last) {
      return result;
    }
    Affix suffix = null;
    for (Affix affix : affixes) {
      if (affix.suffix()) {
        suffix = affix;
      }
    }
    if (affixes.isEmpty() && contains(flags, 'I') && !contains(flags, 'J')) {
      return result - 1;
    }
    if (suffix != null) {
      if (suffix.continuation().length == 0) {
        result -= countSyllables(suffix.affix());
      } else if (suffix.affix().endsWith("i") && !suffix.affix().endsWith("yi")
          && !suffix.affix().endsWith("ti")) {
        result--;
      }
      if (syllableNumber) {
        if (suffix.flag() == 'c') {
          result += 2;
        } else if (suffix.flag() == 'J' || (suffix.flag() == 'I' && contains(flags, 'J'))) {
          result++;
        }
      }
    }
    return result;
  }

  /**
   * Counts code points listed by COMPOUNDSYLLABLE.
   *
   * @param text The text to count.
   * @return The vowel count.
   */
  private int countSyllables(String text) {
    int syllables = 0;
    for (int at = 0; at < text.length();) {
      final int point = text.codePointAt(at);
      if (compoundVowels.indexOf(point) >= 0) {
        syllables++;
      }
      at += Character.charCount(point);
    }
    return syllables;
  }

  /**
   * Checks a closing entry's capitalization requirement.
   *
   * @param flags The closing entry flags.
   * @param word The input compound.
   * @return Whether FORCEUCASE permits the input.
   */
  boolean compoundCaseAllowed(int[] flags, String word) {
    return !contains(flags, forceUpperCase) || Character.isUpperCase(word.codePointAt(0))
        || Character.isTitleCase(word.codePointAt(0));
  }

  /**
   * Tests CHECKCOMPOUNDREP with the configured replacement table.
   *
   * @param word The candidate compound.
   * @param recognized Tests a non-compound form.
   * @return Whether a replacement invalidates the compound reading.
   */
  boolean rejectsCompoundReplacement(String word, Predicate<String> recognized) {
    return checkCompoundRep && replacements.anyReplacement(word, recognized);
  }

  /**
   * A compound-boundary restriction and optional spelling replacement.
   *
   * @param end The left-side ending.
   * @param endFlag The required left flag or zero.
   * @param begin The right-side beginning.
   * @param beginFlag The required right flag or zero.
   * @param unaffixed Whether the left side must have no nonzero affix.
   * @param replacement The simplified junction, or null.
   */
  record CompoundPattern(String end, int endFlag, String begin, int beginFlag,
                         boolean unaffixed, String replacement) {
    /**
     * Checks the selected readings at a junction.
     *
     * @param left The left part spelling.
     * @param leftFlags The left entry flags.
     * @param right The right part spelling.
     * @param rightFlags The right entry flags.
     * @param leftAffixes The left part's affixes.
     * @param rightAffixes The right part's affixes.
     * @return Whether this pattern applies.
     */
    boolean matches(String left, int[] leftFlags, String right, int[] rightFlags,
        List<Affix> leftAffixes, List<Affix> rightAffixes) {
      if (!left.endsWith(end) || !right.startsWith(begin)
          || !hasBoundaryFlag(leftFlags, leftAffixes, endFlag)
          || !hasBoundaryFlag(rightFlags, rightAffixes, beginFlag)) {
        return false;
      }
      if (unaffixed) {
        for (Affix affix : leftAffixes) {
          if (!affix.affix().isEmpty() || !affix.strip().isEmpty()) {
            return false;
          }
        }
      }
      return true;
    }

    /**
     * Tests entry and continuation flags for a boundary condition.
     *
     * @param flags The entry flags.
     * @param affixes The component's rules.
     * @param required The required flag, or zero for an unrestricted condition.
     * @return Whether the selected reading satisfies the condition.
     */
    private boolean hasBoundaryFlag(int[] flags, List<Affix> affixes, int required) {
      if (required == 0 || contains(flags, required)) {
        return true;
      }
      for (Affix affix : affixes) {
        if (affix.allowsContinuation(required)) {
          return true;
        }
      }
      return false;
    }
  }

  /** {@return whether positional or general compound flags are configured} */
  boolean positionalCompoundsDeclared() {
    return compoundFlag != 0 || compoundBegin != 0 || compoundMiddle != 0 || compoundEnd != 0;
  }

  /**
   * Checks blocking flags on a compound-rule part.
   *
   * @param flags The selected entry flags.
   * @param last Whether this is the closing part.
   * @param affixes The applied suffixes.
   * @return Whether the selected entry and affixes can form a compound part.
   */
  boolean acceptsRulePart(int[] flags, boolean last, Affix... affixes) {
    if (contains(flags, forbiddenWord) || (!last && contains(flags, compoundForbid))
        || (affixes.length == 0 && contains(flags, needAffix))) {
      return false;
    }
    for (Affix affix : affixes) {
      if (forbidsInCompound(affix) || circumfixOnly(affix)) {
        return false;
      }
    }
    return affixes.length != 1 || !needsFurtherAffix(affixes[0]);
  }

  /** {@return the smallest length a compound part may have} At least {@code 1}. */
  int compoundMin() {
    return compoundMin;
  }

  /** {@return the largest number of parts a compound may have} {@code 0} is unbounded. */
  int compoundWordMax() {
    return compoundWordMax;
  }

  /** {@return whether {@code CHECKCOMPOUNDDUP} forbids a part repeating its neighbor} */
  boolean checkCompoundDup() {
    return checkCompoundDup;
  }

  /** {@return whether {@code CHECKCOMPOUNDCASE} forbids uppercase at part boundaries} */
  boolean checkCompoundCase() {
    return checkCompoundCase;
  }

  /** {@return whether {@code CHECKCOMPOUNDTRIPLE} forbids triple letters at boundaries} */
  boolean checkCompoundTriple() {
    return checkCompoundTriple;
  }

  /** {@return whether {@code FULLSTRIP} allows an affix rule to strip a whole stem} */
  boolean fullStrip() {
    return fullStrip;
  }

  /**
   * Applies input conversion followed by ignored-character removal.
   *
   * @param word The supplied word.
   * @return The dictionary lookup form.
   */
  String inputForm(String word) {
    return removeIgnored(inputConversion.apply(word), ignoredCharacters);
  }

  /**
   * Applies output conversion to a recognized stem.
   *
   * @param stem The dictionary stem.
   * @return The converted output.
   */
  String outputForm(String stem) {
    return outputConversion.apply(stem);
  }

  /** {@return whether prefix continuation allows an additional prefix level} */
  boolean complexPrefixes() {
    return complexPrefixes;
  }

  /** {@return whether uppercase SS may represent a German sharp s} */
  boolean checkSharps() {
    return checkSharps;
  }

  /**
   * Applies the dictionary's lowercase mapping.
   *
   * @param text The input text.
   * @return The lowercase text.
   */
  String lowerCase(String text) {
    if (!turkicCase) {
      return StringUtil.toLowerCase(text);
    }
    return StringUtil.toLowerCase(text.replace('I', 'ı').replace('İ', 'i'));
  }

  /**
   * Checks case-sensitive and warning restrictions for a selected entry.
   *
   * @param flags The homonym's flags.
   * @param surface The input after conversion.
   * @param variant The case variant under analysis.
   * @param affixes The applied affixes.
   * @return Whether this reading is permitted.
   */
  boolean acceptsCase(int[] flags, String surface, String variant, Affix... affixes) {
    if (forbidWarn && contains(flags, warningFlag)) {
      return false;
    }
    boolean preserveCase = contains(flags, keepCase);
    for (Affix affix : affixes) {
      if (affix.allowsContinuation(forbiddenWord)
          || (forbidWarn && affix.allowsContinuation(warningFlag))) {
        return false;
      }
      preserveCase |= keepCase != 0 && affix.allowsContinuation(keepCase);
    }
    return !preserveCase || surface.equals(variant)
        || (checkSharps && variant.indexOf('ß') >= 0
            && (surface.indexOf('ß') < 0 || Character.isUpperCase(surface.codePointAt(0))));
  }

  /**
   * Returns a stem after applying entry and affix morphology.
   *
   * @param root The dictionary entry spelling.
   * @param flags The selected homonym's flags.
   * @param affixes The affixes in application order.
   * @return The stems of matching homonyms.
   */
  List<String> morphologicalStems(String root, int[] flags, Affix... affixes) {
    final List<String> stems = new ArrayList<>();
    for (int[] entryFlags : homonyms(root)) {
      if (Arrays.equals(entryFlags, flags)) {
        stems.add(morphologicalStem(root, morphology.getOrDefault(entryFlags, List.of()), affixes));
      }
    }
    return stems;
  }

  /**
   * Returns morphological fields for matching dictionary entries and applied rules, in
   * the reference implementation's field order. A prefix without morphological fields
   * contributes its affix text when no suffix follows and its {@code fl:} flag field
   * otherwise; after the entry fields, an entry without fields contributes that prefix's
   * {@code fl:} field. A bare closing compound part without entry fields contributes
   * no stem field.
   *
   * @param root The dictionary entry text.
   * @param flags The selected homonym flags.
   * @param compoundEnd Whether the reading closes a compound.
   * @param affixes The applied rules in order.
   * @return The complete field strings, possibly empty strings.
   */
  List<String> morphologicalAnalyses(String root, int[] flags, boolean compoundEnd,
      Affix... affixes) {
    final List<String> result = new ArrayList<>();
    boolean hasSuffix = false;
    Affix prefixOnly = null;
    for (Affix affix : affixes) {
      hasSuffix |= affix.suffix();
    }
    for (int[] entryFlags : homonyms(root)) {
      if (!Arrays.equals(entryFlags, flags)) {
        continue;
      }
      final List<String> fields = new ArrayList<>();
      for (int i = affixes.length - 1; i >= 0; i--) {
        final Affix prefix = affixes[i];
        if (prefix.suffix()) {
          continue;
        }
        if (hasSuffix || !prefix.morphology().isEmpty()) {
          fields.addAll(prefix.analysisFields());
        } else {
          fields.add(prefix.affix());
        }
        if (!hasSuffix) {
          prefixOnly = prefix;
        }
      }
      final List<String> entry = morphology.getOrDefault(entryFlags, List.of());
      if (compoundEnd && affixes.length == 0 && entry.isEmpty()) {
        result.add("");
        continue;
      }
      if (!hasField(entry, STEM_FIELD)) {
        fields.add(STEM_FIELD + root);
      }
      fields.addAll(entry);
      if (entry.isEmpty() && prefixOnly != null) {
        fields.add(FLAG_FIELD + prefixOnly.flagText());
      }
      for (Affix affix : affixes) {
        if (affix.suffix()) {
          fields.addAll(affix.analysisFields());
        }
      }
      result.add(String.join(" ", fields));
    }
    return result;
  }

  /**
   * Applies morphological stem and affix fields to one dictionary entry.
   *
   * @param root The entry spelling.
   * @param fields The entry's morphological fields.
   * @param affixes The affixes in application order.
   * @return The morphological stem.
   */
  private String morphologicalStem(String root, List<String> fields, Affix... affixes) {
    String result = fieldValue(fields, STEM_FIELD, root);
    if (hasField(fields, DERIVATIONAL_SUFFIX_FIELD)) {
      result = root;
    }
    // A derivational suffix makes the derived form the stem. The reference
    // implementation generates that form from the entry and its suffixes alone; prefix
    // material appears in the stem only through a surface prefix field.
    String derived = root;
    final StringBuilder surfacePrefix = new StringBuilder(fieldValue(fields, SURFACE_PREFIX_FIELD, ""));
    for (Affix affix : affixes) {
      if (affix.suffix()) {
        derived = derived.substring(0, derived.length() - affix.strip().length()) + affix.affix();
        if (hasField(affix.morphology(), DERIVATIONAL_SUFFIX_FIELD)) {
          result = derived;
        }
      }
      surfacePrefix.append(fieldValue(affix.morphology(), SURFACE_PREFIX_FIELD, ""));
    }
    return surfacePrefix.append(result).toString();
  }

  /**
   * Finds a morphological field value.
   *
   * @param fields The expanded fields.
   * @param tag The tag including the colon.
   * @param fallback The value used when the tag is not present.
   * @return The first field value or fallback.
   */
  private String fieldValue(List<String> fields, String tag, String fallback) {
    for (String field : fields) {
      if (field.startsWith(tag)) {
        return field.substring(tag.length());
      }
    }
    return fallback;
  }

  /**
   * Checks for a morphological field.
   *
   * @param fields The expanded fields.
   * @param tag The tag including the colon.
   * @return Whether the tag is present.
   */
  private boolean hasField(List<String> fields, String tag) {
    return fieldValue(fields, tag, null) != null;
  }

  /**
   * Removes characters listed in the affix file's IGNORE setting.
   *
   * @param text The word or affix material.
   * @param ignored The characters to remove.
   * @return Text without the ignored characters.
   */
  private static String removeIgnored(String text, String ignored) {
    if (ignored.isEmpty()) {
      return text;
    }
    final StringBuilder result = new StringBuilder(text.length());
    for (int i = 0; i < text.length();) {
      final int point = text.codePointAt(i);
      if (ignored.indexOf(point) < 0) {
        result.appendCodePoint(point);
      }
      i += Character.charCount(point);
    }
    return result.length() == text.length() ? text : result.toString();
  }

  /**
   * The flag admitting a part at a compound position, next to the general
   * compounding flag.
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
   * Checks whether an affix admits its derived form at a compound position: its
   * continuation classes carry the general compounding flag or the position's
   * dedicated flag. Published dictionaries position their linking forms this way,
   * through zero or dash suffixes whose continuation classes hold the positional
   * flags.
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
   * Checks whether an affix may sit at a compound-internal boundary: it carries the
   * {@code COMPOUNDPERMITFLAG} among its continuation classes. Without the flag a
   * suffix fits only the last part and a prefix only the first.
   *
   * @param affix The affix rule applied to the part.
   * @return {@code true} if the affix may face another part.
   */
  boolean permitsInside(Affix affix) {
    return compoundPermit != 0 && affix.allowsContinuation(compoundPermit);
  }

  /**
   * Checks whether an affix bars its derived form from compounds altogether: it
   * carries the {@code COMPOUNDFORBIDFLAG} among its continuation classes.
   *
   * @param affix The affix rule applied to the part.
   * @return {@code true} if the affixed form may not join a compound.
   */
  boolean forbidsInCompound(Affix affix) {
    return compoundForbid != 0 && affix.allowsContinuation(compoundForbid);
  }

  /**
   * Checks whether an entry marked with {@code COMPOUNDFORBIDFLAG} is barred from
   * this compound position. Hunspell permits such an entry only as the last part, as
   * specified by the
   * <a href="https://github.com/hunspell/hunspell/blob/e184e22c51fe213f4490e9b36998f0ad3e5e606b/man/hunspell.5#L502-L506">
   * format manual</a> and the
   * <a href="https://github.com/hunspell/hunspell/blob/e184e22c51fe213f4490e9b36998f0ad3e5e606b/tests/compoundforbid.aff">
   * regression fixture</a>.
   *
   * @param flags One entry's flag set.
   * @param position The part's place in the compound.
   * @return {@code true} if the entry may not stand at the position.
   */
  private boolean forbiddenAtCompoundPosition(int[] flags, CompoundPosition position) {
    return position != CompoundPosition.END && contains(flags, compoundForbid);
  }

  /**
   * Checks whether a spelling is forbidden as a whole. Only the first listed homonym
   * decides, as in the reference implementation, so a dictionary can list a valid word
   * first and a forbidden compound-only homonym after it.
   *
   * @param flagSets The word's flag sets from {@link #lookup(String)}, in list order.
   * @return {@code true} if the first homonym carries the forbidden-word flag.
   */
  boolean firstForbidden(List<int[]> flagSets) {
    return !flagSets.isEmpty() && contains(flagSets.get(0), forbiddenWord);
  }

  /**
   * Checks whether an affix analysis reaches a forbidden entry, which forbids the
   * affixed spelling and thereby its compound and break readings.
   *
   * @param flagSets The stem's flag sets from {@link #lookup(String)}.
   * @param flag The removed affix's flag.
   * @return {@code true} if a homonym carries both the affix flag and the forbidden flag.
   */
  boolean forbidsAffixed(List<int[]> flagSets, int flag) {
    for (final int[] flags : flagSets) {
      if (contains(flags, flag) && contains(flags, forbiddenWord)) {
        return true;
      }
    }
    return false;
  }

  /**
   * Checks whether a spelling may not open a compound part sequence: its first listed
   * homonym carries {@code COMPOUNDFORBIDFLAG}, which the reference implementation
   * applies to every part but the last, overriding affixed readings of the same
   * spelling.
   *
   * @param flagSets The part's flag sets from {@link #lookup(String)}, in list order.
   * @return {@code true} if the spelling is barred from non-final compound positions.
   */
  boolean forbidsCompoundStart(List<int[]> flagSets) {
    return !flagSets.isEmpty() && contains(flagSets.get(0), compoundForbid);
  }

  /**
   * Converts the initial code point to uppercase with the dictionary's case mapping.
   *
   * @param word The nonempty word.
   * @return The word with an uppercase initial.
   */
  String upperCaseInitial(String word) {
    final int first = word.codePointAt(0);
    final String initial = word.substring(0, Character.charCount(first));
    final String upper = turkicCase && first == 'i' ? "İ"
        : turkicCase && first == 'ı' ? "I" : StringUtil.toUpperCase(initial);
    return upper + word.substring(initial.length());
  }

  /**
   * Converts the initial code point to lowercase with the dictionary's case mapping.
   *
   * @param word The nonempty word.
   * @return The word with a lowercase initial.
   */
  String lowerCaseInitial(String word) {
    final int first = word.codePointAt(0);
    final String initial = word.substring(0, Character.charCount(first));
    return lowerCase(initial) + word.substring(initial.length());
  }

  /**
   * Checks whether any of a word's flag sets carries a flag.
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
   * Checks one flag set for a flag. An undeclared flag, encoded as {@code 0}, is
   * carried by no entry.
   *
   * @param flags One entry's flag set.
   * @param flag The flag to look for.
   * @return {@code true} if the set contains the flag.
   */
  static boolean contains(int[] flags, int flag) {
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
   * Checks whether a listed word is valid on its own: some homonym's flag set carries
   * none of the blocking flags. An entry whose every flag set is marked
   * {@code NEEDAFFIX} is a virtual stem that exists only to be affixed, one marked
   * {@code ONLYINCOMPOUND} appears only inside compounds, and one marked
   * {@code FORBIDDENWORD} is listed to be blocked; none of them is a word by itself.
   *
   * @param flagSets The word's flag sets from {@link #lookup(String)}.
   * @return {@code true} if some homonym stands on its own.
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
   * Checks whether some homonym supports an affix analysis: its flag set carries the
   * affix's flag and is neither compound-only nor forbidden. A {@code NEEDAFFIX} set
   * does support the analysis, because the removed affix is exactly what the virtual
   * stem needs.
   *
   * @param flagSets The stem's flag sets from {@link #lookup(String)}.
   * @param flag The removed affix's flag.
   * @return {@code true} if some homonym carries the flag and may stand affixed.
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
  boolean supportsCrossProduct(List<int[]> flagSets, Affix prefix, Affix suffix) {
    for (final int[] flags : flagSets) {
      if (licensesCrossProduct(flags, prefix, suffix) && !contains(flags, onlyInCompound)
          && !contains(flags, forbiddenWord)) {
        return true;
      }
    }
    return false;
  }

  /**
   * Checks cross-product flags within a single entry.
   *
   * @param flags The selected entry flags.
   * @param prefix The prefix rule.
   * @param suffix The suffix rule.
   * @return Whether the entry and continuation classes permit both rules.
   */
  boolean licensesCrossProduct(int[] flags, Affix prefix, Affix suffix) {
    return (contains(flags, prefix.flag())
        && (contains(flags, suffix.flag()) || prefix.allowsContinuation(suffix.flag())))
        || (contains(flags, suffix.flag()) && suffix.allowsContinuation(prefix.flag()));
  }

  /**
   * Checks a compound cross-product reading.
   *
   * @param flags The selected entry flags.
   * @param position The component position.
   * @param prefix The prefix rule.
   * @param suffix The suffix rule.
   * @param additional Additional continuation-linked rules.
   * @return Whether the entry and rules permit this component.
   */
  boolean supportsCompoundCrossProduct(int[] flags, CompoundPosition position,
      Affix prefix, Affix suffix, Affix... additional) {
    boolean positionAllowed = contains(flags, compoundFlag) || contains(flags, positionalFlag(position))
        || affixAdmits(prefix, position) || affixAdmits(suffix, position);
    for (Affix affix : additional) {
      positionAllowed |= affixAdmits(affix, position);
    }
    return licensesCrossProduct(flags, prefix, suffix) && !contains(flags, forbiddenWord)
        && !forbiddenAtCompoundPosition(flags, position)
        && positionAllowed;
  }

  /**
   * Checks whether a form made with this affix alone is still a virtual stem: the
   * affix carries the {@code NEEDAFFIX} flag among its continuation classes, so a
   * further affix must join before the form is a word.
   *
   * @param affix The affix rule to inspect.
   * @return {@code true} if the affix alone does not finish a word.
   */
  boolean needsFurtherAffix(Affix affix) {
    return needAffix != 0 && affix.allowsContinuation(needAffix);
  }

  /**
   * Checks whether an affix applies only inside compounds: it carries the
   * {@code ONLYINCOMPOUND} flag among its continuation classes.
   *
   * @param affix The affix rule to inspect.
   * @return {@code true} if the affix never applies to a standalone word.
   */
  boolean compoundOnly(Affix affix) {
    return onlyInCompound != 0 && affix.allowsContinuation(onlyInCompound);
  }

  /**
   * Checks whether an affix is one half of a circumfix: it carries the
   * {@code CIRCUMFIX} flag among its continuation classes, so it is only valid on a
   * word that also carries a circumfix-marked affix of the other kind, the German
   * {@code ge...t} participle being the model.
   *
   * @param affix The affix rule to inspect.
   * @return {@code true} if the affix never applies without its other half.
   */
  boolean circumfixOnly(Affix affix) {
    return circumfix != 0 && affix.allowsContinuation(circumfix);
  }

  /**
   * Finds the {@code SET} declaration by scanning the raw affix bytes as ASCII, which
   * is safe because the declaration itself is ASCII in every supported encoding. Both
   * files are then decoded with the declared charset.
   *
   * @param affixBytes The raw affix file content.
   * @return The declared charset, or UTF-8 when no declaration is present.
   * @throws IOException Thrown if the declared encoding name is not supported.
   */
  private static Charset declaredCharset(byte[] affixBytes) throws IOException {
    final String ascii = new String(affixBytes, StandardCharsets.US_ASCII);
    for (final String line : splitLines(ascii)) {
      final String trimmed = trim(line);
      if (trimmed.startsWith(SET_PREFIX) || trimmed.startsWith(SET_TAB_PREFIX)) {
        // the encoding is the first field after the directive; later fields are ignored
        final String name = split(trimmed.substring(SET_PREFIX.length()))[0];
        try {
          return Charset.forName(name);
        } catch (IllegalCharsetNameException | UnsupportedCharsetException e) {
          throw new IOException("unsupported SET encoding: " + name, e);
        }
      }
    }
    return StandardCharsets.UTF_8;
  }

  /** The flag encodings a dictionary may declare with the {@code FLAG} directive. */
  private enum FlagMode {
    /**
     * The default: each single character is one flag. Also what {@code FLAG UTF-8}
     * declares, which asks for single-character flags in a file the {@code SET}
     * declaration already had decoded.
     */
    CHAR,
    /** Declared as {@code FLAG long}: each pair of characters is one flag. */
    LONG,
    /** Declared as {@code FLAG num}: comma-separated decimal numbers are flags. */
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
    private String ignoredCharacters = "";
    private HunspellConversion inputConversion = HunspellConversion.NONE;
    private HunspellConversion outputConversion = HunspellConversion.NONE;
    private final List<List<String>> morphologyAliases = new ArrayList<>();
    private final Map<int[], List<String>> entryMorphology = new HashMap<>();
    private boolean complexPrefixes;
    private int keepCase;
    private int warningFlag;
    private boolean forbidWarn;
    private boolean checkSharps;
    private String language = "";
    private boolean syllableNumber;
    private final List<HunspellCompoundRule> compoundRules = new ArrayList<>();
    private final List<CompoundPattern> compoundPatterns = new ArrayList<>();
    private int compoundRoot;
    private int forceUpperCase;
    private boolean compoundMoreSuffixes;
    private boolean simplifiedTriple;
    private boolean checkCompoundRep;
    private HunspellConversion replacements = HunspellConversion.NONE;
    private int maxCompoundSyllables;
    private String compoundVowels = "";
    private List<String> wordBreaks = DEFAULT_WORD_BREAKS;
  }

  /**
   * Parses the affix file: the {@code FLAG} declaration, the {@code AF} flag alias
   * table, the compound and blocking flag declarations, and the {@code PFX} and
   * {@code SFX} blocks. Other directives are skipped because this class implements
   * affix stemming, not the complete Hunspell spell-checking engine.
   *
   * @param content The decoded affix file content.
   * @return The parsed rules and flag mode. Never {@code null}.
   * @throws IOException Thrown if a supported directive is malformed.
   */
  private static AffixFile parseAffix(String content) throws IOException {
    final AffixFile result = new AffixFile();
    final String[] lines = splitLines(withoutByteOrderMark(content));
    final String[][] fieldsByLine = new String[lines.length][];
    for (int i = 0; i < lines.length; i++) {
      fieldsByLine[i] = split(lines[i]);
    }
    result.flagMode = readFlagMode(fieldsByLine);
    result.flagAliases.addAll(readFlagAliases(fieldsByLine, result.flagMode));
    result.inputConversion = HunspellConversion.parse(fieldsByLine, INPUT_CONVERSION_TAG);
    result.outputConversion = HunspellConversion.parse(fieldsByLine, OUTPUT_CONVERSION_TAG);
    result.morphologyAliases.addAll(readMorphologyAliases(fieldsByLine));
    result.compoundRules.addAll(readCompoundRules(fieldsByLine, result.flagMode));
    result.compoundPatterns.addAll(readCompoundPatterns(fieldsByLine, result.flagMode));
    result.replacements = HunspellConversion.parse(fieldsByLine, REPLACEMENT_TAG);
    result.wordBreaks = readWordBreaks(fieldsByLine);
    for (int line = 0; line < fieldsByLine.length; line++) {
      final String[] fields = fieldsByLine[line];
      if (fields.length > 0 && IGNORE_TAG.equals(fields[0])) {
        if (fields.length != 2) {
          throw new IOException("invalid IGNORE at line " + (line + 1));
        }
        result.ignoredCharacters = fields[1];
      }
    }
    int i = 0;
    while (i < lines.length) {
      final String[] fields = fieldsByLine[i];
      if (fields.length == 0 || fields[0].startsWith(COMMENT_PREFIX)) {
        i++;
        continue;
      }
      switch (fields[0]) {
        case FLAG_TAG:
          // The file-wide declaration was parsed before any rule fields.
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
        case KEEP_CASE_TAG:
        case WARNING_TAG:
        case COMPOUND_ROOT_TAG:
        case FORCE_UPPER_CASE_TAG:
        case LEMMA_PRESENT_TAG:
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
            // PSEUDOROOT is the directive's name before hunspell renamed it
            case "NEEDAFFIX", "PSEUDOROOT" -> result.needAffix = declared;
            case "ONLYINCOMPOUND" -> result.onlyInCompound = declared;
            case "CIRCUMFIX" -> result.circumfix = declared;
            case "FORBIDDENWORD" -> result.forbiddenWord = declared;
            case KEEP_CASE_TAG -> result.keepCase = declared;
            case WARNING_TAG -> result.warningFlag = declared;
            case COMPOUND_ROOT_TAG -> result.compoundRoot = declared;
            case FORCE_UPPER_CASE_TAG -> result.forceUpperCase = declared;
            case LEMMA_PRESENT_TAG -> { }
            default -> throw new IOException(
                "unhandled flag directive " + fields[0] + " at line " + (i + 1));
          }
          i++;
          break;
        case "COMPOUNDMIN":
          final int compoundMin = parseValue(fields, i + 1);
          if (compoundMin < 0) {
            throw new IOException("negative COMPOUNDMIN at line " + (i + 1));
          }
          if (compoundMin > MAX_COMPOUND_MIN) {
            throw new IOException("COMPOUNDMIN exceeds " + MAX_COMPOUND_MIN
                + " at line " + (i + 1));
          }
          result.compoundMin = Math.max(1, compoundMin);
          i++;
          break;
        case "COMPOUNDWORDMAX":
          final int compoundWordMax = parseValue(fields, i + 1);
          if (compoundWordMax < 0) {
            throw new IOException("negative COMPOUNDWORDMAX at line " + (i + 1));
          }
          result.compoundWordMax = compoundWordMax;
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
        case COMPLEX_PREFIXES_TAG:
          result.complexPrefixes = true;
          i++;
          break;
        case COMPOUND_MORE_SUFFIXES_TAG:
          result.compoundMoreSuffixes = true;
          i++;
          break;
        case SIMPLIFIED_TRIPLE_TAG:
          result.simplifiedTriple = true;
          i++;
          break;
        case "CHECKCOMPOUNDREP":
          result.checkCompoundRep = true;
          i++;
          break;
        case COMPOUND_SYLLABLE_TAG:
          result.maxCompoundSyllables = parseValue(fields, i + 1);
          if (fields.length != 3 || result.maxCompoundSyllables < 0) {
            throw new IOException("invalid COMPOUNDSYLLABLE at line " + (i + 1));
          }
          result.compoundVowels = fields[2];
          i++;
          break;
        case FORBID_WARNING_TAG:
          result.forbidWarn = true;
          i++;
          break;
        case CHECK_SHARPS_TAG:
          result.checkSharps = true;
          i++;
          break;
        case LANGUAGE_TAG:
          if (fields.length != 2) {
            throw new IOException("invalid LANG at line " + (i + 1));
          }
          result.language = fields[1];
          i++;
          break;
        case SYLLABLE_NUMBER_TAG:
          if (fields.length != 2) {
            throw new IOException("invalid SYLLABLENUM at line " + (i + 1));
          }
          result.syllableNumber = true;
          i++;
          break;
        case ALIAS_TAG:
          // The file-wide table was parsed before continuation and entry flags.
          i++;
          break;
        case PREFIX_TAG:
        case SUFFIX_TAG:
          i = parseAffixBlock(fieldsByLine, i, fields, result);
          break;
        default:
          i++;
          break;
      }
    }
    return result;
  }

  /**
   * Finds the file-wide flag mode before parsing directives that contain flags.
   *
   * @param fieldsByLine The affix file fields, indexed by source line.
   * @return The selected flag mode, or character mode when no declaration is present.
   * @throws IOException Thrown if the declaration is missing a mode, unsupported, or
   *     repeated.
   */
  private static FlagMode readFlagMode(String[][] fieldsByLine) throws IOException {
    FlagMode mode = FlagMode.CHAR;
    boolean foundMode = false;
    for (int i = 0; i < fieldsByLine.length; i++) {
      final String[] fields = fieldsByLine[i];
      if (fields.length == 0 || fields[0].startsWith(COMMENT_PREFIX)
          || !FLAG_TAG.equals(fields[0])) {
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
   * @param fieldsByLine The affix file fields, indexed by source line.
   * @param mode The file's flag encoding.
   * @return The aliases in their one-based reference order.
   * @throws IOException Thrown if the table header, size, or an alias is malformed.
   */
  private static List<int[]> readFlagAliases(String[][] fieldsByLine, FlagMode mode)
      throws IOException {
    final List<int[]> aliases = new ArrayList<>();
    int expected = -1;
    for (int i = 0; i < fieldsByLine.length; i++) {
      final String[] fields = fieldsByLine[i];
      if (fields.length == 0 || fields[0].startsWith(COMMENT_PREFIX)
          || !ALIAS_TAG.equals(fields[0])) {
        continue;
      }
      if (fields.length < 2) {
        throw new IOException("AF line without a value at line " + (i + 1));
      }
      if (expected < 0) {
        // The AF header gives the alias count. Later AF lines contain flag runs.
        // Numeric dictionary flags reference the one-based position of a run.
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
   * Parses the integer value of a directive that carries exactly one.
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
   * cross-product marker, and the rule count, followed by exactly that many rule
   * lines.
   *
   * @param fieldsByLine All affix file fields, indexed by source line.
   * @param index The line index of the block header.
   * @param header The already-split header fields.
   * @param result The parse target the rules are added to.
   * @return The index of the first line after the block.
   * @throws IOException Thrown if the header or a rule line is malformed.
   */
  private static int parseAffixBlock(String[][] fieldsByLine, int index, String[] header,
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
      if (line >= fieldsByLine.length) {
        throw new IOException("affix block truncated at line " + (line + 1));
      }
      final String[] fields = fieldsByLine[line];
      if (fields.length < 4 || !fields[0].equals(header[0])) {
        throw new IOException("malformed affix rule at line " + (line + 1));
      }
      if (parseFlag(fields[1], result.flagMode, line + 1) != flag) {
        throw new IOException("affix rule flag does not match header at line " + (line + 1));
      }
      final String strip = NO_MATERIAL.equals(fields[2]) ? ""
          : removeIgnored(fields[2], result.ignoredCharacters);
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
      affixText = removeIgnored(affixText, result.ignoredCharacters);
      final boolean hasCondition = fields.length > 4;
      final List<String> morphology = parseMorphology(
          Arrays.copyOfRange(fields, hasCondition ? 5 : 4, fields.length),
          result.morphologyAliases, line + 1);
      final Affix affix = new Affix(flag, crossProduct, strip, affixText,
          AffixCondition.parse(hasCondition ? fields[4] : ".", suffix, line + 1), continuation, suffix,
          morphology, fields[1]);
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
   * morphological fields. The morphological fields are separated
   * first, because the flag separator is only meaningful in what precedes them; a word
   * may itself contain spaces. A slash escaped as {@code \/} belongs to the word itself
   * and is unescaped in the stored key.
   *
   * @param content The decoded word-list content.
   * @param affix The affix settings and destination for entry morphology.
   * @return The words mapped to the flag sets of their entries. Never {@code null}.
   * @throws IOException Thrown if a flag run is malformed or an alias reference is
   *         out of range.
   */
  private static Map<String, List<int[]>> parseWordList(String content,
      AffixFile affix) throws IOException {
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
      int morphology = morphologyIndex(line);
      if (morphology < 0 && !affix.morphologyAliases.isEmpty()) {
        final int separator = line.lastIndexOf(' ');
        if (separator > 0 && isCount(line.substring(separator + 1))) {
          morphology = separator;
        }
      }
      final String entry = morphology < 0 ? line : trim(line.substring(0, morphology));
      String word = entry;
      int[] flags = new int[0];
      final int slash = unescapedSlash(entry);
      if (slash >= 0) {
        word = entry.substring(0, slash);
        String flagRun = entry.substring(slash + 1);
        // The flag run ends at the first space or tabulator, the separators the
        // word-list format defines; whatever follows is a morphological field even
        // when it carries no two-letter tag, which hunspell tolerates and so do we.
        for (int c = 0; c < flagRun.length(); c++) {
          if (isFieldSeparator(flagRun.charAt(c))) {
            flagRun = flagRun.substring(0, c);
            break;
          }
        }
        flags = parseAliasedFlags(flagRun, affix.flagMode, affix.flagAliases, i + 1).clone();
      }
      if (morphology >= 0) {
        affix.entryMorphology.put(flags, parseMorphology(split(line.substring(morphology)),
            affix.morphologyAliases, i + 1));
      }
      entries.computeIfAbsent(removeIgnored(word.replace("\\/", "/"), affix.ignoredCharacters),
          key -> new ArrayList<>(1))
          .add(flags);
    }
    return entries;
  }

  /**
   * Parses the complete AM alias table before entries and affixes.
   *
   * @param lines The affix fields indexed by source line.
   * @return The aliases in reference order.
   * @throws IOException If a count or table entry is malformed.
   */
  private static List<List<String>> readMorphologyAliases(String[][] lines) throws IOException {
    final List<List<String>> aliases = new ArrayList<>();
    final List<HunspellAffixTable.Entry> entries =
        HunspellAffixTable.read(lines, MORPHOLOGY_ALIAS_TAG, 2, Integer.MAX_VALUE);
    if (entries != null) {
      for (HunspellAffixTable.Entry entry : entries) {
        final String[] fields = entry.fields();
        aliases.add(List.of(Arrays.copyOfRange(fields, 1, fields.length)));
      }
    }
    return aliases;
  }

  /**
   * Parses and validates the COMPOUNDRULE table.
   *
   * @param lines The affix fields indexed by line.
   * @param mode The flag encoding.
   * @return The parsed patterns.
   * @throws IOException If a count, pattern, or flag is malformed.
   */
  private static List<HunspellCompoundRule> readCompoundRules(String[][] lines, FlagMode mode)
      throws IOException {
    final List<HunspellCompoundRule> rules = new ArrayList<>();
    final List<HunspellAffixTable.Entry> entries = HunspellAffixTable.read(lines, COMPOUND_RULE_TAG, 2, 2);
    if (entries != null) {
      for (HunspellAffixTable.Entry entry : entries) {
        rules.add(HunspellCompoundRule.parse(entry.fields()[1], entry.line(),
            (text, line) -> parseFlag(text, mode, line)));
      }
    }
    return rules;
  }

  /**
   * Parses CHECKCOMPOUNDPATTERN restrictions and replacements.
   *
   * @param lines The affix fields.
   * @param mode The flag encoding.
   * @return The boundary patterns.
   * @throws IOException If a declaration is malformed.
   */
  private static List<CompoundPattern> readCompoundPatterns(String[][] lines, FlagMode mode)
      throws IOException {
    final List<CompoundPattern> patterns = new ArrayList<>();
    final List<HunspellAffixTable.Entry> entries =
        HunspellAffixTable.read(lines, COMPOUND_PATTERN_TAG, 3, 4);
    if (entries == null) {
      return patterns;
    }
    for (HunspellAffixTable.Entry entry : entries) {
      final String[] fields = entry.fields();
      final int leftSlash = fields[1].indexOf('/');
      final int rightSlash = fields[2].indexOf('/');
      final String left = leftSlash < 0 ? fields[1] : fields[1].substring(0, leftSlash);
      final String right = rightSlash < 0 ? fields[2] : fields[2].substring(0, rightSlash);
      patterns.add(new CompoundPattern("0".equals(left) ? "" : left,
          leftSlash < 0 ? 0 : parseFlag(fields[1].substring(leftSlash + 1), mode, entry.line()),
          right, rightSlash < 0 ? 0 : parseFlag(fields[2].substring(rightSlash + 1), mode, entry.line()),
          "0".equals(left), fields.length == 4 ? fields[3] : null));
    }
    return patterns;
  }

  /**
   * Parses BREAK declarations, using hyphen rules when no table is configured.
   *
   * @param lines The affix fields.
   * @return The separators and anchors.
   * @throws IOException If the table or a separator is malformed.
   */
  private static List<String> readWordBreaks(String[][] lines) throws IOException {
    final List<HunspellAffixTable.Entry> entries = HunspellAffixTable.read(lines, BREAK_TAG, 2, 2);
    if (entries == null) {
      return DEFAULT_WORD_BREAKS;
    }
    final List<String> result = new ArrayList<>();
    for (HunspellAffixTable.Entry entry : entries) {
      final String separator = entry.fields()[1];
      if ("^".equals(separator) || "$".equals(separator) || "^$".equals(separator)) {
        throw new IOException("invalid BREAK at line " + entry.line());
      }
      result.add(separator);
    }
    return result;
  }

  /**
   * Expands an optional morphology alias.
   *
   * @param fields The morphological fields or alias reference.
   * @param aliases The AM table.
   * @param line The source line.
   * @return The expanded immutable fields.
   * @throws IOException If an alias reference is invalid.
   */
  private static List<String> parseMorphology(String[] fields, List<List<String>> aliases,
      int line) throws IOException {
    if (fields.length == 0) {
      return List.of();
    }
    if (!aliases.isEmpty()) {
      try {
        if (fields.length != 1) {
          throw new NumberFormatException();
        }
        final int index = Integer.parseInt(fields[0]);
        if (index < 1 || index > aliases.size()) {
          throw new NumberFormatException();
        }
        return aliases.get(index - 1);
      } catch (NumberFormatException e) {
        throw new IOException("invalid AM alias at line " + line, e);
      }
    }
    return List.of(fields);
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
   * word from its flag run in a word-list entry.
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
   * terminates the word and its flag run. A morphological field is either introduced by
   * a tabulator, the older separator, or written as a two-letter tag followed by
   * {@code :} and preceded by a separator, such as {@code po:verb}. A separator that
   * is not followed by such a tag belongs to the word, because a word-list entry may
   * name several words. The separators are the space and the tabulator, exactly the
   * two characters the reference implementation's {@code hashmgr.cxx} splits on; they
   * are format delimiters of the word-list grammar, not a whitespace judgment, so
   * wider whitespace such as a no-break space stays part of the word by design.
   *
   * @param line The trimmed word-list line to scan.
   * @return The index at which the morphological fields begin, or {@code -1} if the
   *         entry carries none.
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
   * Checks one character against the word-list format's field separators, space and
   * tabulator, the exact set the reference implementation splits morphological fields
   * on.
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
   * @return The text without leading or trailing whitespace. Never {@code null}.
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
   * Parses a flag run according to the declared flag mode: single characters in
   * {@code char} mode, character pairs packed into one {@code int} in {@code long}
   * mode, and comma-separated decimal numbers in {@code num} mode.
   *
   * @param text The flag run without its leading {@code /}. An empty run carries no
   *             flags in every mode.
   * @param mode The declared flag encoding.
   * @param lineNumber The source line, for error messages.
   * @return The parsed flags. Never {@code null}.
   * @throws IOException Thrown if the run does not fit the declared encoding.
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
            throw new IOException("numeric flag outside 1.." + MAX_NUMERIC_FLAG + " at line "
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
        // would split such a flag into a surrogate pair. A variation selector
        // (U+FE00..U+FE0F) only selects a flag character's presentation and is
        // dropped from flag identity.
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
   * Parses a field that must contain exactly one flag, such as the flag name in an
   * affix block header.
   *
   * @param text The flag field.
   * @param mode The declared flag encoding.
   * @param lineNumber The source line, for error messages.
   * @return The single parsed flag.
   * @throws IOException Thrown if the field holds no flag or more than one.
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
   * @return The lines without their terminators. Never {@code null}.
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
   * @return The parts between the separators, empty ones included. Never {@code null}.
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
   * @return The whitespace-separated fields, without empty ones. Never {@code null}.
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
