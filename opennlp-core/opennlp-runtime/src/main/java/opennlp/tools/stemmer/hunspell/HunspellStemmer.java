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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import opennlp.tools.commons.ThreadSafe;
import opennlp.tools.stemmer.Stemmer;
import opennlp.tools.stemmer.hunspell.HunspellDictionary.Affix;
import opennlp.tools.stemmer.hunspell.HunspellDictionary.CompoundPattern;
import opennlp.tools.stemmer.hunspell.HunspellDictionary.CompoundPosition;

/**
 * A dictionary-backed {@link Stemmer} over a {@link HunspellDictionary}: a surface form
 * is reduced to the dictionary words it can be derived from by removing one suffix, one
 * prefix, a cross-product combination of both, or an additional affix licensed by a
 * continuation class. Compound and break-separated forms return the stems of their
 * recognized parts.
 *
 * <p>{@link #stem(CharSequence)} returns the first analysis, preferring the word's own
 * dictionary entry; {@link #stemAll(CharSequence)} returns every distinct analysis. A
 * word with no analysis is returned unchanged, so the stemmer degrades to identity on
 * unknown input. Title-case and all-uppercase forms also use permitted case
 * variants, subject to the dictionary's case restrictions.
 * Entries the dictionary marks as virtual stems ({@code NEEDAFFIX}), compound-only
 * parts ({@code ONLYINCOMPOUND}), or forbidden words ({@code FORBIDDENWORD}) do not
 * count as standalone analyses.</p>
 *
 * <p>Compound part search is capped at {@value #PART_CHECK_BUDGET} part-licensing
 * attempts per input word; beyond that budget further compound analyses are skipped.
 * The {@link Stemmer} interface leaves thread safety to the implementation. This
 * implementation reads only the immutable dictionary state, so a single instance is
 * safe to share between threads.</p>
 *
 * @since 3.0.0
 */
@ThreadSafe
public final class HunspellStemmer implements Stemmer {

  /**
   * Maximum candidate checks per compound search or recursive word-break search.
   */
  private static final int PART_CHECK_BUDGET = 2048;

  /** Maximum parts in one compound analysis. */
  private static final int MAX_COMPOUND_PARTS = 64;

  /** Maximum case variants considered for CHECKSHARPS. */
  private static final int MAX_CASE_VARIANTS = 64;

  /** Maximum distinct morphological readings returned for one input. */
  private static final int MAX_ANALYSES = 2048;

  private final HunspellDictionary dictionary;

  /**
   * Initializes the stemmer.
   *
   * @param dictionary The dictionary to analyze against. Must not be {@code null}.
   * @throws IllegalArgumentException Thrown if {@code dictionary} is {@code null}.
   */
  public HunspellStemmer(HunspellDictionary dictionary) {
    if (dictionary == null) {
      throw new IllegalArgumentException("dictionary must not be null");
    }
    this.dictionary = dictionary;
  }

  /**
   * {@inheritDoc}
   *
   * <p>Returns the first analysis, which prefers the word's own dictionary entry.</p>
   */
  @Override
  public CharSequence stem(CharSequence word) {
    final List<CharSequence> analyses = stemAll(word);
    return analyses.get(0);
  }

  /**
   * {@inheritDoc}
   *
   * <p>Returns every distinct analysis, or a single-element list of the unchanged word
   * when it has none.</p>
   */
  @Override
  public List<CharSequence> stemAll(CharSequence word) {
    if (word == null) {
      throw new IllegalArgumentException("word must not be null");
    }
    final String surface = word.toString();
    final List<String> analyses = findWord(dictionary.inputForm(surface), false);
    if (analyses.isEmpty()) {
      return List.of(surface);
    }
    final Set<String> converted = new LinkedHashSet<>();
    for (String analysis : analyses) {
      converted.add(dictionary.outputForm(analysis));
    }
    return List.copyOf(converted);
  }

  /**
   * Returns morphological analyses as space-separated Hunspell fields.
   * Each result describes a complete accepted reading. Entries without an explicit
   * {@code st:} field use the dictionary entry as their stem. Compound components
   * begin with {@code pa:}; entry and affix fields follow in application order.
   * Results preserve dictionary field text without output conversion.
   *
   * @param word The input to analyze. Must not be {@code null}.
   * @return An immutable list of distinct analyses, or an empty list for unknown input.
   *     At most {@value #MAX_ANALYSES} analyses are returned.
   * @throws IllegalArgumentException If {@code word} is {@code null}.
   */
  public List<String> analyze(CharSequence word) {
    if (word == null) {
      throw new IllegalArgumentException("word must not be null");
    }
    return findWord(dictionary.inputForm(word.toString()), true);
  }

  /**
   * Finds the readings of a complete input. Trailing periods are removed first, as
   * the reference implementation does for abbreviations; when the shortened form has
   * no reading, one period is restored for entries listed with it.
   *
   * @param input The input after conversion.
   * @param morphological Whether results contain morphological fields.
   * @return Recognized stems or analyses, or an empty list.
   */
  private List<String> findWord(String input, boolean morphological) {
    int end = input.length();
    while (end > 0 && input.charAt(end - 1) == '.') {
      end--;
    }
    if (end == 0) {
      // a zero-length word has no morphology; without this guard a strip-only rule
      // could restore its strip string onto nothing and answer a non-empty stem
      return List.of();
    }
    final List<String> analyses = findStems(input.substring(0, end), 0,
        new int[] {PART_CHECK_BUDGET}, new HashMap<>(), morphological);
    if (analyses.isEmpty() && end < input.length()) {
      return findStems(input.substring(0, end + 1), 0,
          new int[] {PART_CHECK_BUDGET}, new HashMap<>(), morphological);
    }
    return analyses;
  }

  /** Accumulates stems or complete morphology readings for one request. */
  private final class Results {
    private final boolean morphological;
    private final Set<String> values = new LinkedHashSet<>();
    /**
     * Whether a direct or affixed reading reached a forbidden entry, which blocks the
     * compound and break readings of the same input.
     */
    private boolean forbidden;
    /**
     * Whether compound decomposition follows the Hungarian moving rule for the part of
     * a word before a hyphen.
     */
    private boolean hyphenatedFirstPart;

    /**
     * Selects the output representation.
     *
     * @param morphological Whether results contain fields instead of stems.
     */
    private Results(boolean morphological) {
      this.morphological = morphological;
    }

    /** {@return whether a reading has not been found} */
    private boolean isEmpty() {
      return values.isEmpty();
    }

    /**
     * Adds distinct results up to the output limit.
     *
     * @param additions The accepted results.
     */
    private void addAll(List<String> additions) {
      for (String value : additions) {
        if (values.size() >= MAX_ANALYSES) {
          return;
        }
        values.add(value);
      }
    }

    /**
     * Adds a complete compound analysis, preserving homonym alternatives.
     *
     * @param parts The selected component entries and affixes.
     */
    private void addCompound(List<CompoundPart> parts) {
      if (!morphological) {
        for (CompoundPart part : parts) {
          addAll(part.stems());
        }
        return;
      }
      List<String> accumulated = List.of("");
      for (CompoundPart part : parts) {
        final List<String> next = new ArrayList<>();
        for (String fields : dictionary.morphologicalAnalyses(part.root(), part.flags(),
            part == parts.get(parts.size() - 1), part.affixes().toArray(Affix[]::new))) {
          for (String prior : accumulated) {
            if (next.size() >= MAX_ANALYSES) {
              break;
            }
            next.add(new StringBuilder(prior).append(prior.isEmpty() ? "" : " ")
                .append("pa:").append(part.surface())
                .append(fields.isEmpty() ? "" : " ").append(fields).toString());
          }
        }
        accumulated = next;
      }
      addAll(accumulated);
    }

    /**
   * Combines accepted readings before and after a word break.
     *
     * @param leftText The opening text.
     * @param left The opening readings.
     * @param rightText The closing text.
     * @param right The closing readings.
     */
    private void addBroken(String leftText, List<String> left, String rightText, List<String> right) {
      if (!morphological) {
        addAll(left);
        addAll(right);
        return;
      }
      for (String first : left) {
        for (String last : right) {
          if (values.size() >= MAX_ANALYSES) {
            return;
          }
          values.add(new StringBuilder()
              .append(first.startsWith("pa:") ? "" : "pa:" + leftText + " ").append(first)
              .append(' ').append(last.startsWith("pa:") ? "" : "pa:" + rightText + " ")
              .append(last).toString());
        }
      }
    }
  }

  /**
   * Finds recognized stems without returning identity for unrecognized pieces.
   *
   * @param input The normalized input or a break-separated component.
   * @param depth The current break depth.
   * @param budget The remaining break attempts.
   * @param cache Results for completed pieces of this input.
   * @param morphological Whether results contain morphological fields.
   * @return Recognized stems, or an empty list.
   */
  private List<String> findStems(String input, int depth, int[] budget,
      Map<String, List<String>> cache, boolean morphological) {
    if (input.isEmpty() || depth >= MAX_COMPOUND_PARTS || budget[0] <= 0) {
      return List.of();
    }
    final List<String> cached = cache.get(input);
    if (cached != null) {
      return cached;
    }
    final List<int[]> entries = dictionary.lookup(input);
    if (entries != null && dictionary.firstForbidden(entries)) {
      return List.of();
    }
    final Results analyses = new Results(morphological);
    final boolean allCaps = HunspellDictionary.caseType(input) == HunspellDictionary.CaseType.ALLCAP;
    for (final String variant : variants(input)) {
      analyze(variant, new Analysis(input, variant, analyses, allCaps));
    }
    // a forbidden direct or affixed reading forbids the spelling as a whole, so no
    // compound or break reading is attempted, as in the reference implementation
    if (analyses.isEmpty() && !analyses.forbidden && dictionary.compoundsDeclared()) {
      for (final String variant : variants(input)) {
        decompose(variant, input, analyses);
      }
    }
    if (analyses.isEmpty() && !analyses.forbidden) {
      for (String declaration : dictionary.wordBreaks()) {
        if (budget[0] <= 0) {
          break;
        }
        final boolean start = declaration.startsWith("^");
        final boolean end = declaration.endsWith("$");
        final String separator = declaration.substring(start ? 1 : 0,
            declaration.length() - (end ? 1 : 0));
        for (int at = input.indexOf(separator); at >= 0 && budget[0] > 0;
            at = input.indexOf(separator, at + separator.length())) {
          final int after = at + separator.length();
          if ((start && at != 0) || (end && after != input.length())
              || (!start && at == 0) || (!end && after == input.length())) {
            continue;
          }
          budget[0]--;
          if (start && !end) {
            analyses.addAll(findStems(input.substring(after), depth + 1, budget, cache, morphological));
          } else if (end && !start) {
            analyses.addAll(findStems(input.substring(0, at), depth + 1, budget, cache, morphological));
          } else if (!start) {
            List<String> left = findStems(input.substring(0, at), depth + 1,
                budget, cache, morphological);
            if (left.isEmpty() && "-".equals(separator) && dictionary.hyphenMovingRule()) {
              left = hyphenatedFirstPart(input.substring(0, at), morphological);
            }
            if (!left.isEmpty()) {
              final List<String> right = findStems(input.substring(after), depth + 1,
                  budget, cache, morphological);
              if (!right.isEmpty()) {
                analyses.addBroken(input.substring(0, at), left, input.substring(after), right);
              }
            }
          }
        }
      }
    }
    final List<String> result = List.copyOf(analyses.values);
    cache.put(input, result);
    return result;
  }

  /**
   * Finds the readings of the part of a Hungarian word before a hyphen, which the
   * reference implementation accepts as a listed word ending in the hyphen or as a
   * compound under the moving rule.
   *
   * @param text The part before the hyphen.
   * @param morphological Whether results contain morphological fields.
   * @return Recognized stems or analyses, or an empty list.
   */
  private List<String> hyphenatedFirstPart(String text, boolean morphological) {
    final Results analyses = new Results(morphological);
    final String hyphenated = text + "-";
    final boolean allCaps = HunspellDictionary.caseType(text) == HunspellDictionary.CaseType.ALLCAP;
    for (final String variant : variants(hyphenated)) {
      analyze(variant, new Analysis(hyphenated, variant, analyses, allCaps));
    }
    if (analyses.isEmpty() && !analyses.forbidden && dictionary.compoundsDeclared()) {
      analyses.hyphenatedFirstPart = true;
      for (final String variant : variants(text)) {
        decompose(variant, text, analyses);
      }
    }
    return List.copyOf(analyses.values);
  }

  /**
   * Collects the case variants to analyze: the surface form first, then its lowercase
   * form when the two differ. A capitalized word with a further inner capital, such as a
   * mixed-case word at the start of a sentence, is also tried with a lowercase initial.
   * An all-uppercase word containing an apostrophe is also tried with the part after
   * the apostrophe capitalized, for the elided articles of Catalan, French, and Italian.
   * Ordering matters because the first analysis found wins in {@link #stem(CharSequence)}.
   *
   * @param surface The surface form.
   * @return The variants in analysis order. Never {@code null} or empty.
   */
  private List<String> variants(String surface) {
    final Set<String> variants = new LinkedHashSet<>();
    variants.add(surface);
    boolean upper = false;
    boolean lowerAfterFirst = true;
    boolean allUpper = true;
    boolean firstUpper = false;
    int uppers = 0;
    int letters = 0;
    for (int i = 0; i < surface.length();) {
      final int point = surface.codePointAt(i);
      if (Character.isLowerCase(point)) {
        allUpper = false;
        letters++;
      } else if (Character.isUpperCase(point) || Character.isTitleCase(point)) {
        if (letters > 0) {
          lowerAfterFirst = false;
        }
        firstUpper |= letters == 0;
        upper = true;
        uppers++;
        letters++;
      }
      i += Character.charCount(point);
    }
    if (upper && (allUpper || lowerAfterFirst)) {
      final String lowered = dictionary.lowerCase(surface);
      final int apostrophe = lowered.indexOf('\'');
      if (allUpper && apostrophe > 0 && apostrophe < lowered.length() - 1) {
        final String elided = lowered.substring(0, apostrophe + 1)
            + initialUpper(lowered.substring(apostrophe + 1));
        variants.add(elided);
        variants.add(initialUpper(elided));
      }
      variants.add(lowered);
      if (allUpper) {
        variants.add(initialUpper(lowered));
        if (dictionary.checkSharps()) {
          addSharpVariants(lowered, 0, variants);
        }
      }
    } else if (firstUpper && uppers > 1 && !allUpper) {
      variants.add(dictionary.lowerCaseInitial(surface));
    }
    return List.copyOf(variants);
  }

  /**
   * Converts the initial code point to uppercase with the dictionary's case mapping.
   *
   * @param word The nonempty word.
   * @return The capitalized form.
   */
  private String initialUpper(String word) {
    return dictionary.upperCaseInitial(word);
  }

  /**
   * Collects sharp-s alternatives with a fixed search limit.
   *
   * @param word The lowercase candidate.
   * @param from The next character position to search.
   * @param variants The resulting forms.
   */
  private void addSharpVariants(String word, int from, Set<String> variants) {
    if (variants.size() >= MAX_CASE_VARIANTS) {
      return;
    }
    for (int at = word.indexOf("ss", from); at >= 0; at = word.indexOf("ss", at + 1)) {
      final String changed = word.substring(0, at) + "ß" + word.substring(at + 2);
      variants.add(changed);
      variants.add(initialUpper(changed));
      addSharpVariants(changed, at + 1, variants);
      if (variants.size() >= MAX_CASE_VARIANTS) {
        return;
      }
    }
  }

  /** One case-variant analysis with request-local result storage. */
  private final class Analysis {
    private final String surface;
    private final String variant;
    private final Results stems;
    /**
     * Whether the input is all uppercase, in which case the reference implementation
     * also matches the hidden capitalized forms of mixed-case entries.
     */
    private final boolean allCaps;

    /**
     * Creates an analysis context.
     *
     * @param surface The input after conversion.
     * @param variant The current case variant.
     * @param stems The destination for recognized stems.
     * @param allCaps Whether the input is all uppercase.
     */
    private Analysis(String surface, String variant, Results stems, boolean allCaps) {
      this.surface = surface;
      this.variant = variant;
      this.stems = stems;
      this.allCaps = allCaps;
    }

    /**
     * Looks up a spelling, including hidden capitalized forms for all-uppercase input.
     *
     * @param word The spelling to look up.
     * @return The flag sets, or {@code null} when absent.
     */
    private List<int[]> lookup(String word) {
      return dictionary.lookup(word, allCaps);
    }

    /**
     * Adds morphology-aware stems after case and warning checks.
     *
     * @param root The entry spelling.
     * @param flags The selected flags.
     * @param affixes The rules in application order.
     */
    private void add(String root, int[] flags, Affix... affixes) {
      if (dictionary.acceptsCase(flags, surface, variant, affixes)) {
        stems.addAll(stems.morphological
            ? dictionary.morphologicalAnalyses(root, flags, false, affixes)
            : dictionary.morphologicalStems(root, flags, affixes));
      }
    }

    /**
     * Records that an affix analysis reached a forbidden entry.
     *
     * @param flagSets The stem's flag sets.
     * @param flag The removed affix's flag.
     */
    private void noteForbidden(List<int[]> flagSets, int flag) {
      if (dictionary.forbidsAffixed(flagSets, flag)) {
        stems.forbidden = true;
      }
    }
  }

  /**
   * Adds every analysis of one case variant to the result set: the word's own
   * dictionary entry, single suffix removal, twofold suffix removal through
   * continuation classes, single prefix removal, and cross-product removal of one
   * prefix together with one suffix and an optional continuation suffix. Insertion
   * order into the set fixes the
   * preference order reported by {@link #stemAll(CharSequence)}.
   *
   * @param word The case variant to analyze.
   * @param analyses The mutable, insertion-ordered set collecting the stems found.
   */
  private void analyze(String word, Analysis analyses) {
    final List<int[]> entries = analyses.lookup(word);
    if (entries != null) {
      if (dictionary.firstForbidden(entries)) {
        analyses.stems.forbidden = true;
        return;
      }
      for (int[] flags : entries) {
        if (dictionary.validStandalone(List.of(flags))) {
          analyses.add(word, flags);
        }
      }
    }
    for (final Affix suffix : dictionary.suffixesEndingWith(
        word.codePointBefore(word.length()))) {
      undoSuffix(word, suffix, analyses);
    }
    for (final Affix suffix : dictionary.suffixesWithoutMaterial()) {
      undoSuffix(word, suffix, analyses);
    }
    for (final Affix prefix : dictionary.prefixesStartingWith(word.codePointAt(0))) {
      undoPrefix(word, prefix, analyses);
    }
    for (final Affix prefix : dictionary.prefixesWithoutMaterial()) {
      undoPrefix(word, prefix, analyses);
    }
  }

  /**
   * Searches compound components after standalone analysis fails. Entries and
   * affixes must permit the selected component positions and junctions. Recognized
   * spaced forms prevent concatenation. The candidate budget applies to compound
   * decomposition; output follows component order.
   *
   * @param word The case variant to decompose.
   * @param surface The surface form the variant was derived from; character case at
   *                junctions is checked using this input for {@code CHECKCOMPOUNDCASE}.
   * @param analyses The mutable, insertion-ordered set collecting the part stems.
   */
  private void decompose(String word, String surface, Results analyses) {
    final List<int[]> entries = dictionary.lookup(word);
    if (entries != null && dictionary.firstForbidden(entries)) {
      return;
    }
    if (rejectsCompoundText(word)) {
      return;
    }
    // lowercasing may change the length in exceptional mappings, in which case the
    // offsets no longer align and the variant itself is the only usable case source
    final String caseSource = surface.length() == word.length() ? surface : word;
    final int[] budget = {PART_CHECK_BUDGET};
    if (dictionary.positionalCompoundsDeclared()) {
      searchSpelling(word, caseSource, analyses, budget, List.of());
      for (CompoundPattern pattern : dictionary.compoundPatterns()) {
        if (pattern.replacement() == null || pattern.replacement().isEmpty()) {
          continue;
        }
        for (int at = word.indexOf(pattern.replacement()); at >= 0 && budget[0] > 0;
            at = word.indexOf(pattern.replacement(), at + 1)) {
          final int end = at + pattern.replacement().length();
          final String inserted = pattern.end() + pattern.begin();
          searchSpelling(word.substring(0, at) + inserted + word.substring(end),
              caseSource.substring(0, at) + inserted + caseSource.substring(end),
              analyses, budget, List.of(new Junction(at + pattern.end().length(), pattern)));
        }
      }
      if (dictionary.simplifiedTriple()) {
        searchTriples(word, caseSource, 0, List.of(), analyses, budget);
      }
    }
    for (HunspellCompoundRule rule : dictionary.compoundRules()) {
      searchRule(word, caseSource, 0, rule, new ArrayList<>(), new ArrayList<>(), analyses, budget);
    }
  }

  /**
   * Applies the text-level compound checks the reference implementation runs on the
   * text every compound level splits: a {@code CHECKCOMPOUNDREP} replacement or a
   * space inserted at any position must not produce a recognized non-compound form.
   *
   * @param text The complete input or the remainder a compound level splits.
   * @return {@code true} if a check forbids splitting the text.
   */
  private boolean rejectsCompoundText(String text) {
    if (dictionary.rejectsCompoundReplacement(text, this::isNoncompoundForm)) {
      return true;
    }
    for (int at = Character.charCount(text.codePointAt(0)); at < text.length();
        at += Character.charCount(text.codePointAt(at))) {
      if (isNoncompoundForm(text.substring(0, at) + " " + text.substring(at))) {
        return true;
      }
    }
    return false;
  }

  /**
   * A required boundary after a compound spelling substitution.
   *
   * @param offset The boundary's UTF-16 offset.
   * @param pattern The pattern permitting this boundary, or null for a repeated letter.
   */
  private record Junction(int offset, CompoundPattern pattern) { }

  /**
   * Restores simplified triple letters at independently checked boundaries.
   *
   * @param word The current text.
   * @param surface The aligned case source.
   * @param from The next position eligible for restoration.
   * @param junctions The required boundaries accumulated so far.
   * @param analyses The destination for stems.
   * @param budget The remaining search attempts.
   */
  private void searchTriples(String word, String surface, int from, List<Junction> junctions,
      Results analyses, int[] budget) {
    if (junctions.size() >= MAX_COMPOUND_PARTS - 1) {
      return;
    }
    for (int at = from; at < word.length() && budget[0] > 0;) {
      final int point = word.codePointAt(at);
      final int width = Character.charCount(point);
      final int next = at + width;
      if (next < word.length() && word.codePointAt(next) == point
          && (at == 0 || word.codePointBefore(at) != point)
          && (next + width == word.length() || word.codePointAt(next + width) != point)) {
        final String inserted = new String(Character.toChars(point));
        final String expanded = new StringBuilder(word).insert(next, inserted).toString();
        final String expandedCase = new StringBuilder(surface).insert(next, inserted).toString();
        for (int boundary : new int[] {next, next + width}) {
          if (budget[0]-- <= 0) {
            return;
          }
          final List<Junction> required = new ArrayList<>(junctions);
          required.add(new Junction(boundary, null));
          searchSpelling(expanded, expandedCase, analyses, budget, required);
          searchTriples(expanded, expandedCase, next + 2 * width, required, analyses, budget);
        }
      }
      at = next;
    }
  }

  /**
   * Searches one spelling, optionally requiring a restored compound junction.
   *
   * @param word The spelling with any compound substitution expanded.
   * @param surface The aligned case source.
   * @param analyses The destination for stems.
   * @param budget The remaining candidate checks across forms.
   * @param junctions Required boundaries for spelling substitutions.
   */
  private void searchSpelling(String word, String surface, Results analyses, int[] budget,
      List<Junction> junctions) {
    final int count = word.codePointCount(0, word.length());
    if (count < 2 * dictionary.compoundMin()) {
      return;
    }
    for (Junction junction : junctions) {
      if (junction.offset() <= 0 || junction.offset() >= word.length()) {
        return;
      }
    }
    final int[] offsets = new int[count + 1];
    int offset = 0;
    for (int i = 0; i < count; i++) {
      offsets[i] = offset;
      offset += Character.charCount(word.codePointAt(offset));
    }
    offsets[count] = word.length();
    search(word, surface, offsets, 0, new ArrayList<>(), analyses, budget, junctions,
        new byte[count + 1]);
  }

  /**
   * One selected compound-part reading.
   *
   * @param surface The part as written.
   * @param root The entry spelling.
   * @param flags The selected homonym's flags.
   * @param affixes The rules in application order.
   * @param stems The morphology-aware stems.
   */
  private record CompoundPart(String surface, String root, int[] flags,
                              List<Affix> affixes, List<String> stems) { }

  /**
   * Searches compound-rule paths without combining flags from different homonyms.
   *
   * @param word The input case variant.
   * @param surface The input used for case restrictions.
   * @param from The current UTF-16 offset.
   * @param rule The compound pattern.
   * @param parts The selected readings.
   * @param flags The flags corresponding to the readings.
   * @param analyses The destination for stems.
   * @param budget The remaining candidate checks.
   */
  private void searchRule(String word, String surface, int from, HunspellCompoundRule rule,
      List<CompoundPart> parts, List<int[]> flags, Results analyses, int[] budget) {
    if (parts.size() >= MAX_COMPOUND_PARTS
        || word.codePointCount(from, word.length()) < dictionary.compoundMin()) {
      return;
    }
    int end = word.offsetByCodePoints(from, dictionary.compoundMin());
    while (end <= word.length() && budget[0] > 0) {
      final boolean last = end == word.length();
      if (!(from == 0 && last)) {
        budget[0]--;
        final String part = word.substring(from, end);
        final String caseSource = surface.substring(from, end);
        for (CompoundPart candidate : ruleParts(part, caseSource, last)) {
          if (last && dictionary.checkCompoundDup() && !parts.isEmpty()
              && parts.get(parts.size() - 1).root().equals(candidate.root())) {
            continue;
          }
          parts.add(candidate);
          flags.add(candidate.flags());
          if (rule.matches(flags, last)) {
            if (last) {
              analyses.addCompound(parts);
            } else {
              searchRule(word, surface, end, rule, parts, flags, analyses, budget);
            }
          }
          parts.remove(parts.size() - 1);
          flags.remove(flags.size() - 1);
        }
      }
      if (last) {
        break;
      }
      end += Character.charCount(word.codePointAt(end));
    }
  }

  /**
   * Finds selected dictionary entries for a compound-rule part.
   *
   * @param part The part's case variant.
   * @param surface The part's supplied case.
   * @param last Whether suffix removal is permitted at this position.
   * @return The permitted readings.
   */
  private List<CompoundPart> ruleParts(String part, String surface, boolean last) {
    final List<CompoundPart> result = new ArrayList<>();
    addRulePart(part, part, surface, last, result);
    if (last) {
      for (Affix suffix : dictionary.suffixesEndingWith(part.codePointBefore(part.length()))) {
        final String root = removeSuffixAllowingIdentity(part, suffix);
        if (root != null) {
          addRulePart(part, root, surface, true, result, suffix);
        }
      }
      for (Affix suffix : dictionary.suffixesWithoutMaterial()) {
        final String root = removeSuffixAllowingIdentity(part, suffix);
        if (root != null) {
          addRulePart(part, root, surface, true, result, suffix);
        }
      }
    }
    return result;
  }

  /**
   * Adds homonyms permitted by their blocking and affix flags.
   *
   * @param part The component text.
   * @param root The restored entry spelling.
   * @param surface The supplied part spelling.
   * @param last Whether the part closes the compound.
   * @param result The candidate destination.
   * @param affixes The applied suffixes.
   */
  private void addRulePart(String part, String root, String surface, boolean last,
      List<CompoundPart> result, Affix... affixes) {
    final List<int[]> entries = dictionary.lookup(root);
    if (entries == null || dictionary.firstForbidden(entries)) {
      return;
    }
    for (int[] entry : entries) {
      if (dictionary.acceptsRulePart(entry, last, affixes)
          && dictionary.acceptsCase(entry, surface, part, affixes)
          && (affixes.length == 0 || HunspellDictionary.hasFlag(List.of(entry), affixes[0].flag()))) {
        result.add(new CompoundPart(part, root, entry, List.of(affixes),
            dictionary.morphologicalStems(root, entry, affixes)));
      }
    }
  }

  /**
   * Extends a partial decomposition with the part starting at {@code fromPoint}, trying
   * every admissible length and recursing on the remainder. The boundary into this
   * part honors the {@code CHECKCOMPOUNDCASE} and {@code CHECKCOMPOUNDTRIPLE}
   * declarations, a part repeating its left neighbor honors
   * {@code CHECKCOMPOUNDDUP}, and a completed decomposition flushes every part's
   * stems into the analyses in part order.
   *
   * @param word The case variant under decomposition.
   * @param caseSource The character-case source for junction checks, the surface
   *                   form when its offsets align with the variant.
   * @param codePointOffsets UTF-16 offsets for each code point boundary.
   * @param fromPoint The code point index where the next part starts.
   * @param parts The selected part readings.
   * @param analyses The mutable, insertion-ordered set collecting the part stems.
   * @param budget The remaining part-licensing attempts, counted down in place.
   * @param junctions The required boundaries for substitutions.
   * @param remainderChecks The cached outcome of {@link #rejectsCompoundText(String)}
   *                        per code point index: {@code 0} unknown, {@code 1} allowed,
   *                        {@code 2} rejected.
   */
  private void search(String word, String caseSource, int[] codePointOffsets,
      int fromPoint, List<CompoundPart> parts, Results analyses, int[] budget,
      List<Junction> junctions, byte[] remainderChecks) {
    final int from = codePointOffsets[fromPoint];
    Junction current = null;
    for (Junction junction : junctions) {
      if (junction.offset() == from) {
        current = junction;
      }
    }
    if (parts.size() >= MAX_COMPOUND_PARTS
        || (from > 0 && violatesBoundaryChecks(word, caseSource, from,
            current != null && current.pattern() == null))) {
      return;
    }
    final int min = dictionary.compoundMin();
    final boolean first = from == 0;
    final int remaining = codePointOffsets.length - 1 - fromPoint;
    if (remaining < min) {
      return;
    }
    // this call splits the remainder into further parts, which the reference
    // implementation subjects to the same text checks as the complete input
    if (!first) {
      if (remainderChecks[fromPoint] == 0) {
        remainderChecks[fromPoint] = (byte) (rejectsCompoundText(word.substring(from)) ? 2 : 1);
      }
      if (remainderChecks[fromPoint] == 2) {
        return;
      }
    }
    for (int endPoint = fromPoint + min; endPoint < codePointOffsets.length; endPoint++) {
      if (budget[0] <= 0) {
        return;
      }
      final int end = codePointOffsets[endPoint];
      final boolean last = end == word.length();
      boolean spansRequiredBoundary = false;
      for (Junction junction : junctions) {
        spansRequiredBoundary |= from < junction.offset() && end > junction.offset();
      }
      if ((first && last) || spansRequiredBoundary
          || (!last && codePointOffsets.length - 1 - endPoint < min)) {
        continue;
      }
      budget[0]--;
      final String part = word.substring(from, end);
      final CompoundPosition position = first ? CompoundPosition.BEGIN
          : last ? CompoundPosition.END : CompoundPosition.MIDDLE;
      for (CompoundPart candidate : partReadings(part, caseSource.substring(from, end),
          position, first, last, analyses.hyphenatedFirstPart)) {
        if (!parts.isEmpty() && rejectsJunction(parts.get(parts.size() - 1), candidate,
            current == null ? null : current.pattern(), last)) {
          continue;
        }
        parts.add(candidate);
        if (last) {
          int units = 0;
          int syllables = 0;
          for (CompoundPart selected : parts) {
            units += dictionary.compoundUnits(selected.flags(), selected.affixes());
            syllables += dictionary.compoundSyllables(selected.surface(), selected.flags(),
                selected.affixes(), selected == candidate);
          }
          if ((analyses.hyphenatedFirstPart || dictionary.compoundSizeAllowed(syllables, units))
              && dictionary.compoundCaseAllowed(candidate.flags(), caseSource)) {
            analyses.addCompound(parts);
          }
        } else {
          search(word, caseSource, codePointOffsets, endPoint, parts, analyses,
              budget, junctions, remainderChecks);
        }
        parts.remove(parts.size() - 1);
      }
    }
  }

  /**
   * Applies the junction declarations. {@code CHECKCOMPOUNDDUP} forbids the closing
   * part from repeating the part before it; the reference implementation compares the
   * two parts it joins at each level, so an earlier repetition is not checked. A
   * junction restored from a pattern replacement must satisfy that pattern's flag
   * conditions and is exempt from the other patterns; any other junction is forbidden
   * when some pattern matches it.
   *
   * @param left The preceding part.
   * @param right The candidate part.
   * @param allowed The rule allowing this substituted junction, or {@code null}.
   * @param last Whether the candidate closes the compound.
   * @return {@code true} if a declaration forbids this part here.
   */
  private boolean rejectsJunction(CompoundPart left, CompoundPart right, CompoundPattern allowed,
      boolean last) {
    if (last && dictionary.checkCompoundDup() && left.root().equals(right.root())) {
      return true;
    }
    if (allowed != null) {
      return !allowed.matches(left.surface(), left.flags(), right.surface(),
          right.flags(), left.affixes(), right.affixes());
    }
    for (CompoundPattern pattern : dictionary.compoundPatterns()) {
      if (pattern.matches(left.surface(), left.flags(), right.surface(),
          right.flags(), left.affixes(), right.affixes())) {
        return true;
      }
    }
    return false;
  }

  /**
   * Applies the character-level boundary declarations at the junction before
   * {@code from}: {@code CHECKCOMPOUNDCASE} forbids an uppercase character on either
   * side of the junction, and {@code CHECKCOMPOUNDTRIPLE} forbids the same character
   * three times in a row across it.
   *
   * @param word The case variant under decomposition.
   * @param caseSource The character-case source for the uppercase judgment.
   * @param from The index the junction sits before; greater than zero.
   * @param allowTriple Whether this junction restores a simplified repeated letter.
   * @return {@code true} if a declaration forbids this junction.
   */
  private boolean violatesBoundaryChecks(String word, String caseSource, int from,
      boolean allowTriple) {
    final int before = word.codePointBefore(from);
    final int after = word.codePointAt(from);
    if (dictionary.checkCompoundCase()
        && (Character.isUpperCase(caseSource.codePointBefore(from))
            || Character.isUpperCase(caseSource.codePointAt(from)))) {
      return true;
    }
    if (!allowTriple && dictionary.checkCompoundTriple() && before == after) {
      final int beforeStart = from - Character.charCount(before);
      final int afterEnd = from + Character.charCount(after);
      if ((beforeStart > 0 && word.codePointBefore(beforeStart) == after)
          || (afterEnd < word.length() && word.codePointAt(afterEnd) == after)) {
        return true;
      }
    }
    return false;
  }

  /**
   * Collects direct and affixed readings for a compound component. Affixes at
   * internal boundaries require the permit flag. If no reading is found, a
   * capitalized dictionary entry is also considered.
   *
   * @param part The part's surface text.
   * @param surface The supplied case.
   * @param position The component position.
   * @param first Whether the part opens the word.
   * @param last Whether the part closes the word.
   * @param movingRule Whether the Hungarian moving rule relaxes the opening parts.
   * @return The permitted readings in discovery order.
   */
  private List<CompoundPart> partReadings(String part, String surface, CompoundPosition position,
      boolean first, boolean last, boolean movingRule) {
    final List<CompoundPart> readings = new ArrayList<>();
    final List<int[]> listed = last || movingRule ? null : dictionary.lookup(part);
    if (listed != null && dictionary.forbidsCompoundStart(listed)) {
      // a listed spelling barred from compounding is barred in its affixed readings too
      return readings;
    }
    collectPartReadings(part, surface, position, first, last, movingRule, readings);
    if (readings.isEmpty() && !part.isEmpty()) {
      final int initial = part.codePointAt(0);
      final int upper = Character.toUpperCase(initial);
      if (upper != initial) {
        collectPartReadings(new StringBuilder().appendCodePoint(upper)
            .append(part, Character.charCount(initial), part.length()).toString(),
            surface, position, first, last, movingRule, readings);
      }
    }
    return readings;
  }

  /**
   * Collects direct entries and permitted affix combinations for a component.
   *
   * @param part The part spelling to look up.
   * @param surface The supplied case.
   * @param position The part's place in the compound.
   * @param first Whether the part opens the word.
   * @param last Whether the part closes the word.
   * @param movingRule Whether an opening entry may also qualify through the hardwired
   *                   Hungarian flags.
   * @param readings The destination for selected readings.
   */
  private void collectPartReadings(String part, String surface, CompoundPosition position,
      boolean first, boolean last, boolean movingRule, List<CompoundPart> readings) {
    final List<int[]> entries = dictionary.lookup(part);
    if (entries != null) {
      for (int[] flags : entries) {
        if ((dictionary.mayStand(List.of(flags), position)
            || (movingRule && !last && dictionary.opensHyphenatedCompound(flags)))
            && dictionary.acceptsCase(flags, surface, part)) {
          readings.add(new CompoundPart(part, part, flags, List.of(),
              dictionary.morphologicalStems(part, flags)));
        }
      }
    }
    for (final Affix suffix : dictionary.suffixesEndingWith(
        part.codePointBefore(part.length()))) {
      collectAffixedPartReading(part, surface, suffix, position, first, last, readings);
    }
    for (final Affix suffix : dictionary.suffixesWithoutMaterial()) {
      collectAffixedPartReading(part, surface, suffix, position, first, last, readings);
    }
    for (final Affix prefix : dictionary.prefixesStartingWith(part.codePointAt(0))) {
      collectAffixedPartReading(part, surface, prefix, position, first, last, readings);
      collectCrossPartReadings(part, surface, prefix, position, first, last, readings);
    }
    for (final Affix prefix : dictionary.prefixesWithoutMaterial()) {
      collectAffixedPartReading(part, surface, prefix, position, first, last, readings);
      collectCrossPartReadings(part, surface, prefix, position, first, last, readings);
    }
  }

  /**
   * Searches suffix combinations after removing a compound component's prefix.
   *
   * @param part The component text.
   * @param surface The supplied case.
   * @param prefix The prefix rule.
   * @param position The component position.
   * @param first Whether this is the opening component.
   * @param last Whether this is the closing component.
   * @param readings The destination for accepted readings.
   */
  private void collectCrossPartReadings(String part, String surface, Affix prefix,
      CompoundPosition position, boolean first, boolean last, List<CompoundPart> readings) {
    if (!prefix.crossProduct() || dictionary.forbidsInCompound(prefix)
        || (!first && !dictionary.permitsInside(prefix))) {
      return;
    }
    final String intermediate = removePrefixAllowingIdentity(part, prefix);
    if (intermediate == null) {
      return;
    }
    for (Affix suffix : dictionary.suffixesEndingWith(intermediate.codePointBefore(intermediate.length()))) {
      collectCrossPart(part, surface, intermediate, prefix, suffix, position, last, readings);
    }
    for (Affix suffix : dictionary.suffixesWithoutMaterial()) {
      collectCrossPart(part, surface, intermediate, prefix, suffix, position, last, readings);
    }
  }

  /**
   * Validates a compound component with a prefix and suffix.
   *
   * @param part The component text.
   * @param surface The supplied case.
   * @param intermediate The text after prefix removal.
   * @param prefix The prefix rule.
   * @param suffix The suffix rule.
   * @param position The component position.
   * @param last Whether this is the closing component.
   * @param readings The destination for accepted readings.
   */
  private void collectCrossPart(String part, String surface, String intermediate,
      Affix prefix, Affix suffix, CompoundPosition position, boolean last,
      List<CompoundPart> readings) {
    if (!suffix.crossProduct() || dictionary.forbidsInCompound(suffix)
        || (!last && !dictionary.permitsInside(suffix))
        || dictionary.circumfixOnly(prefix) != dictionary.circumfixOnly(suffix)
        || (dictionary.needsFurtherAffix(prefix) && dictionary.needsFurtherAffix(suffix))) {
      return;
    }
    final String root = removeSuffixAllowingIdentity(intermediate, suffix);
    if (root == null) {
      return;
    }
    final List<int[]> entries = dictionary.lookup(root);
    if (entries != null) {
      for (int[] flags : entries) {
        if (dictionary.supportsCompoundCrossProduct(flags, position, prefix, suffix)
            && dictionary.acceptsCase(flags, surface, part, prefix, suffix)) {
          readings.add(new CompoundPart(part, root, flags, List.of(prefix, suffix),
              dictionary.morphologicalStems(root, flags, prefix, suffix)));
        }
      }
    }
    if (last && dictionary.compoundMoreSuffixes() && !dictionary.complexPrefixes()) {
      for (Affix inner : dictionary.suffixesEndingWith(root.codePointBefore(root.length()))) {
        collectCrossDoublePart(part, surface, root, inner, suffix, prefix, position, readings);
      }
      for (Affix inner : dictionary.suffixesWithoutMaterial()) {
        collectCrossDoublePart(part, surface, root, inner, suffix, prefix, position, readings);
      }
    }
  }

  /**
   * Adds the stem of one affixed part reading when the rule and the stem's entry admit
   * it at the position.
   *
   * @param part The part spelling under analysis.
   * @param surface The supplied case.
   * @param affix The rule to undo.
   * @param position The part's place in the compound.
   * @param first Whether this is the opening component.
   * @param last Whether this is the closing component.
   * @param readings The destination for selected readings.
   */
  private void collectAffixedPartReading(String part, String surface, Affix affix,
      CompoundPosition position, boolean first, boolean last, List<CompoundPart> readings) {
    final boolean suffix = affix.suffix();
    final boolean atEdge = suffix ? last : first;
    // a compound-only suffix joins parts and never closes a compound on its own
    if (dictionary.circumfixOnly(affix) || dictionary.forbidsInCompound(affix)
        || (!atEdge && !dictionary.permitsInside(affix))
        || (suffix && last && dictionary.compoundOnly(affix))) {
      return;
    }
    final String stem = removeAffixInCompound(part, affix, suffix);
    if (stem == null) {
      return;
    }
    final List<int[]> flagSets = dictionary.lookup(stem);
    if (flagSets != null && !dictionary.needsFurtherAffix(affix)) {
      for (int[] flags : flagSets) {
        if (dictionary.supportsPart(List.of(flags), affix.flag(), position,
            dictionary.affixAdmits(affix, position))
            && dictionary.acceptsCase(flags, surface, part, affix)) {
          readings.add(new CompoundPart(part, stem, flags, List.of(affix),
              dictionary.morphologicalStems(stem, flags, affix)));
        }
      }
    }
    if (dictionary.compoundMoreSuffixes() && suffix != dictionary.complexPrefixes() && atEdge) {
      final List<Affix> material = suffix
          ? dictionary.suffixesEndingWith(stem.codePointBefore(stem.length()))
          : dictionary.prefixesStartingWith(stem.codePointAt(0));
      final List<Affix> zero = suffix
          ? dictionary.suffixesWithoutMaterial() : dictionary.prefixesWithoutMaterial();
      for (Affix inner : material) {
        collectDoublePart(part, surface, stem, inner, affix, position, last, readings);
      }
      for (Affix inner : zero) {
        collectDoublePart(part, surface, stem, inner, affix, position, last, readings);
      }
    }
  }

  /**
   * Finds a compound part with continuation-linked affixes.
   *
   * @param part The component text.
   * @param surface The supplied case.
   * @param intermediate The form after outer affix removal.
   * @param inner The inner rule.
   * @param outer The outer rule.
   * @param position The compound position.
   * @param last Whether this is the final part.
   * @param readings The destination for readings.
   */
  private void collectDoublePart(String part, String surface, String intermediate,
      Affix inner, Affix outer, CompoundPosition position, boolean last,
      List<CompoundPart> readings) {
    if (!inner.allowsContinuation(outer.flag()) || dictionary.forbidsInCompound(inner)) {
      return;
    }
    final String root = removeAffixInCompound(intermediate, inner, inner.suffix());
    if (root == null) {
      return;
    }
    final List<int[]> entries = dictionary.lookup(root);
    if (entries != null && !dictionary.circumfixOnly(inner)) {
      for (int[] flags : entries) {
        if (dictionary.supportsPart(List.of(flags), inner.flag(), position,
            dictionary.affixAdmits(inner, position) || dictionary.affixAdmits(outer, position))
            && dictionary.acceptsCase(flags, surface, part, inner, outer)) {
          readings.add(new CompoundPart(part, root, flags, List.of(inner, outer),
              dictionary.morphologicalStems(root, flags, inner, outer)));
        }
      }
    }
    if (!inner.suffix() && inner.crossProduct() && outer.crossProduct()) {
      for (Affix suffix : dictionary.suffixesEndingWith(root.codePointBefore(root.length()))) {
        collectCompoundSuffixAfterPrefixes(part, surface, root, inner, outer, suffix,
            position, last, readings);
      }
      for (Affix suffix : dictionary.suffixesWithoutMaterial()) {
        collectCompoundSuffixAfterPrefixes(part, surface, root, inner, outer, suffix,
            position, last, readings);
      }
    }
  }

  /**
   * Checks a suffix after continuation-linked compound prefixes.
   *
   * @param part The component text.
   * @param surface The supplied case.
   * @param text The text after removing the prefixes.
   * @param inner The inner prefix.
   * @param outer The outer prefix.
   * @param suffix The suffix rule.
   * @param position The component position.
   * @param last Whether this is the final component.
   * @param readings The result destination.
   */
  private void collectCompoundSuffixAfterPrefixes(String part, String surface, String text,
      Affix inner, Affix outer, Affix suffix, CompoundPosition position, boolean last,
      List<CompoundPart> readings) {
    if (!last && !dictionary.permitsInside(suffix)) {
      return;
    }
    final String root = removeSuffixAllowingIdentity(text, suffix);
    if (root != null) {
      addCompoundCrossDouble(part, surface, root, inner, outer, suffix, position, readings);
    }
  }

  /**
   * Removes an inner compound suffix after a prefix and outer suffix.
   *
   * @param part The component text.
   * @param surface The supplied case.
   * @param text The text after removing the outer suffix and prefix.
   * @param inner The inner suffix.
   * @param outer The outer suffix.
   * @param prefix The prefix rule.
   * @param position The component position.
   * @param readings The result destination.
   */
  private void collectCrossDoublePart(String part, String surface, String text, Affix inner,
      Affix outer, Affix prefix, CompoundPosition position, List<CompoundPart> readings) {
    final String root = removeSuffixAllowingIdentity(text, inner);
    if (root != null) {
      addCompoundCrossDouble(part, surface, root, inner, outer, prefix, position, readings);
    }
  }

  /**
   * Validates a continuation sequence combined with an opposite-end affix.
   *
   * @param part The component text.
   * @param surface The supplied case.
   * @param root The restored entry text.
   * @param inner The inner rule in the continuation sequence.
   * @param outer The outer rule in the continuation sequence.
   * @param cross The opposite-end rule.
   * @param position The component position.
   * @param readings The result destination.
   */
  private void addCompoundCrossDouble(String part, String surface, String root, Affix inner,
      Affix outer, Affix cross, CompoundPosition position, List<CompoundPart> readings) {
    if (!inner.allowsContinuation(outer.flag()) || !inner.crossProduct() || !cross.crossProduct()
        || dictionary.forbidsInCompound(inner) || dictionary.forbidsInCompound(cross)
        || dictionary.circumfixOnly(outer)
        || dictionary.circumfixOnly(inner) != dictionary.circumfixOnly(cross)) {
      return;
    }
    final Affix prefix = inner.suffix() ? cross : inner;
    final Affix suffix = inner.suffix() ? inner : cross;
    final Affix[] applied = inner.suffix()
        ? new Affix[] {cross, inner, outer} : new Affix[] {inner, outer, cross};
    final List<int[]> entries = dictionary.lookup(root);
    if (entries != null) {
      for (int[] flags : entries) {
        if (dictionary.supportsCompoundCrossProduct(flags, position, prefix, suffix, outer)
            && dictionary.acceptsCase(flags, surface, part, applied)) {
          readings.add(new CompoundPart(part, root, flags, List.of(applied),
              dictionary.morphologicalStems(root, flags, applied)));
        }
      }
    }
  }

  /**
   * Checks a direct or affixed form without compound recursion.
   *
   * @param word The candidate spelling.
   * @return Whether a non-compound reading exists.
   */
  private boolean isNoncompoundForm(String word) {
    if (word.isEmpty()) {
      return false;
    }
    final Results stems = new Results(false);
    for (String variant : variants(word)) {
      analyze(variant, new Analysis(word, variant, stems, false));
    }
    return !stems.isEmpty();
  }

  /**
   * Undoes one affix rule on a compound part. Unlike the standalone removals, a rule
   * that neither adds nor removes material is undone here, to its own spelling with
   * the condition checked, because dictionaries position compound parts through
   * exactly such zero rules.
   *
   * @param part The part spelling under analysis.
   * @param affix The rule to undo.
   * @param suffix Whether the rule is a suffix rule.
   * @return The candidate stem, or {@code null} when the rule does not apply.
   */
  private String removeAffixInCompound(String part, Affix affix, boolean suffix) {
    return suffix
        ? removeSuffixAllowingIdentity(part, affix)
        : removePrefixAllowingIdentity(part, affix);
  }

  /**
   * Undoes one suffix rule and, through continuation classes, one further suffix on
   * the intermediate stem, adding dictionary-confirmed analyses. A rule that applies
   * only inside compounds or requires the matching circumfix member is not undone
   * because no prefix accompanies this path. A rule requiring a further affix produces
   * no single-removal analysis. A rule that adds and strips no material is undone like
   * any other, which recognizes a virtual stem it completes and reports its fields.
   *
   * @param word The case variant under analysis.
   * @param suffix The suffix rule to undo.
   * @param analyses The mutable, insertion-ordered set collecting the stems found.
   */
  private void undoSuffix(String word, Affix suffix, Analysis analyses) {
    if (dictionary.compoundOnly(suffix) || dictionary.circumfixOnly(suffix)) {
      return;
    }
    final String stem = removeSuffixAllowingIdentity(word, suffix);
    if (stem == null) {
      return;
    }
    if (!dictionary.needsFurtherAffix(suffix)) {
      final List<int[]> flagSets = analyses.lookup(stem);
      if (flagSets != null) {
        analyses.noteForbidden(flagSets, suffix.flag());
        for (int[] flags : flagSets) {
          if (dictionary.supports(List.of(flags), suffix.flag())) {
            analyses.add(stem, flags, suffix);
          }
        }
      }
    }
    if (dictionary.complexPrefixes()) {
      return;
    }
    for (final Affix inner : dictionary.suffixesEndingWith(
        stem.codePointBefore(stem.length()))) {
      undoInnerSuffix(stem, suffix, inner, analyses);
    }
    for (final Affix inner : dictionary.suffixesWithoutMaterial()) {
      undoInnerSuffix(stem, suffix, inner, analyses);
    }
  }

  /**
   * Undoes the inner suffix of a twofold removal when the rule's continuation
   * classes allow the outer one. The continuation-linked combination satisfies a
   * {@code NEEDAFFIX} marker on either rule.
   *
   * @param stem The intermediate stem after the outer removal.
   * @param outer The already-undone outer suffix rule.
   * @param inner The candidate inner suffix rule.
   * @param analyses The mutable, insertion-ordered set collecting the stems found.
   */
  private void undoInnerSuffix(String stem, Affix outer, Affix inner,
      Analysis analyses) {
    if (!inner.allowsContinuation(outer.flag()) || dictionary.compoundOnly(inner)
        || dictionary.circumfixOnly(inner)) {
      return;
    }
    final String doubleStem = removeSuffixAllowingIdentity(stem, inner);
    if (doubleStem == null) {
      return;
    }
    final List<int[]> innerFlags = analyses.lookup(doubleStem);
    if (innerFlags != null) {
      for (int[] flags : innerFlags) {
        if (dictionary.supports(List.of(flags), inner.flag())) {
          analyses.add(doubleStem, flags, inner, outer);
        }
      }
    }
  }

  /**
   * Undoes one prefix rule and, for cross-product rules, one further suffix on the
   * intermediate stem, adding dictionary-confirmed analyses. A rule that
   * applies only inside compounds is not undone at all. A rule marked as needing a
   * further affix or the matching circumfix member produces no single-removal analysis.
   * A rule that adds and strips no material is undone like any other. A valid
   * cross-product suffix can combine with either kind of rule.
   *
   * @param word The case variant under analysis.
   * @param prefix The prefix rule to undo.
   * @param analyses The mutable, insertion-ordered set collecting the stems found.
   */
  private void undoPrefix(String word, Affix prefix, Analysis analyses) {
    if (dictionary.compoundOnly(prefix)) {
      return;
    }
    final String stem = removePrefixAllowingIdentity(word, prefix);
    if (stem == null) {
      return;
    }
    if (!dictionary.needsFurtherAffix(prefix) && !dictionary.circumfixOnly(prefix)) {
      final List<int[]> flagSets = analyses.lookup(stem);
      if (flagSets != null) {
        analyses.noteForbidden(flagSets, prefix.flag());
        for (int[] flags : flagSets) {
          if (dictionary.supports(List.of(flags), prefix.flag())) {
            analyses.add(stem, flags, prefix);
          }
        }
      }
    }
    if (dictionary.complexPrefixes()) {
      for (Affix inner : dictionary.prefixesStartingWith(stem.codePointAt(0))) {
        undoInnerPrefix(stem, prefix, inner, analyses);
      }
      for (Affix inner : dictionary.prefixesWithoutMaterial()) {
        undoInnerPrefix(stem, prefix, inner, analyses);
      }
    }
    if (!prefix.crossProduct()) {
      return;
    }
    for (final Affix suffix : dictionary.suffixesEndingWith(
        stem.codePointBefore(stem.length()))) {
      undoCrossProductSuffix(stem, prefix, suffix, analyses);
    }
    for (final Affix suffix : dictionary.suffixesWithoutMaterial()) {
      undoCrossProductSuffix(stem, prefix, suffix, analyses);
    }
  }

  /**
   * Undoes the suffix half of a cross-product removal when both rules opted in. The
   * two rules must agree on circumfixing: a circumfix-marked affix is only valid with
   * a marked affix of the other kind, so a pair of which exactly one is marked mixes
   * an ordinary affix into a circumfix and is rejected.
   *
   * @param stem The intermediate stem after the prefix removal.
   * @param prefix The already-undone prefix rule.
   * @param suffix The candidate suffix rule.
   * @param analyses The mutable, insertion-ordered set collecting the stems found.
   */
  private void undoCrossProductSuffix(String stem, Affix prefix, Affix suffix,
      Analysis analyses) {
    if (!suffix.crossProduct() || dictionary.compoundOnly(suffix)
        || dictionary.circumfixOnly(prefix) != dictionary.circumfixOnly(suffix)) {
      return;
    }
    final String doubleStem = removeSuffix(stem, suffix);
    if (doubleStem == null) {
      return;
    }
    // One member can satisfy the other member's needs-further-affix marker. Both rule
    // flags must occur in one homonym's flag set.
    final List<int[]> both = analyses.lookup(doubleStem);
    if (both != null && !(dictionary.needsFurtherAffix(prefix)
        && dictionary.needsFurtherAffix(suffix))) {
      for (int[] flags : both) {
        if (dictionary.licensesCrossProduct(flags, prefix, suffix)) {
          analyses.noteForbidden(List.of(flags), suffix.flag());
        }
        if (dictionary.supportsCrossProduct(List.of(flags), prefix, suffix)) {
          analyses.add(doubleStem, flags, prefix, suffix);
        }
      }
    }
    if (dictionary.complexPrefixes()) {
      return;
    }
    for (final Affix inner : dictionary.suffixesEndingWith(
        doubleStem.codePointBefore(doubleStem.length()))) {
      undoCrossProductInnerSuffix(doubleStem, prefix, suffix, inner, analyses);
    }
    for (final Affix inner : dictionary.suffixesWithoutMaterial()) {
      undoCrossProductInnerSuffix(doubleStem, prefix, suffix, inner, analyses);
    }
  }

  /**
   * Undoes an inner suffix after a prefix and an outer suffix have been removed.
   * The inner suffix must license the outer suffix through the continuation flags.
   * The suffix combination satisfies {@code NEEDAFFIX} markers in the derivation.
   *
   * @param stem The intermediate stem after the prefix and outer suffix removal.
   * @param prefix The already-undone prefix rule.
   * @param outer The already-undone outer suffix rule.
   * @param inner The candidate inner suffix rule.
   * @param analyses The mutable, insertion-ordered set collecting the stems found.
   */
  private void undoCrossProductInnerSuffix(String stem, Affix prefix, Affix outer,
      Affix inner, Analysis analyses) {
    if (!inner.crossProduct() || !inner.allowsContinuation(outer.flag())
        || dictionary.compoundOnly(inner)
        || dictionary.circumfixOnly(outer)
        || dictionary.circumfixOnly(prefix) != dictionary.circumfixOnly(inner)) {
      return;
    }
    final String root = removeSuffixAllowingIdentity(stem, inner);
    if (root == null) {
      return;
    }
    final List<int[]> flagSets = analyses.lookup(root);
    if (flagSets != null) {
      for (int[] flags : flagSets) {
        if (dictionary.supportsCrossProduct(List.of(flags), prefix, inner)) {
          analyses.add(root, flags, prefix, inner, outer);
        }
      }
    }
  }

  /**
   * Removes a continuation-linked inner prefix in COMPLEXPREFIXES mode.
   *
   * @param stem The form after the outer prefix was removed.
   * @param outer The outer prefix.
   * @param inner The inner prefix.
   * @param analyses The result context.
   */
  private void undoInnerPrefix(String stem, Affix outer, Affix inner, Analysis analyses) {
    if (!inner.allowsContinuation(outer.flag()) || dictionary.compoundOnly(inner)
        || dictionary.circumfixOnly(outer)) {
      return;
    }
    final String root = removePrefixAllowingIdentity(stem, inner);
    if (root == null) {
      return;
    }
    final List<int[]> entries = analyses.lookup(root);
    if (entries != null && !dictionary.circumfixOnly(inner)) {
      for (int[] flags : entries) {
        if (dictionary.supports(List.of(flags), inner.flag())) {
          analyses.add(root, flags, inner, outer);
        }
      }
    }
    if (inner.crossProduct() && outer.crossProduct()) {
      for (Affix suffix : dictionary.suffixesEndingWith(root.codePointBefore(root.length()))) {
        undoDoublePrefixSuffix(root, inner, outer, suffix, analyses);
      }
      for (Affix suffix : dictionary.suffixesWithoutMaterial()) {
        undoDoublePrefixSuffix(root, inner, outer, suffix, analyses);
      }
    }
  }

  /**
   * Removes the suffix following a continuation-linked prefix combination.
   *
   * @param word The form after prefix removal.
   * @param inner The inner prefix.
   * @param outer The outer prefix.
   * @param suffix The candidate suffix.
   * @param analyses The result context.
   */
  private void undoDoublePrefixSuffix(String word, Affix inner, Affix outer,
      Affix suffix, Analysis analyses) {
    if (!suffix.crossProduct() || dictionary.compoundOnly(suffix)
        || dictionary.circumfixOnly(inner) != dictionary.circumfixOnly(suffix)) {
      return;
    }
    final String root = removeSuffixAllowingIdentity(word, suffix);
    if (root != null) {
      final List<int[]> entries = analyses.lookup(root);
      if (entries != null) {
        for (int[] flags : entries) {
          if (dictionary.supportsCrossProduct(List.of(flags), inner, suffix)) {
            analyses.add(root, flags, inner, outer, suffix);
          }
        }
      }
    }
  }

  /**
   * Undoes one suffix rule: cuts the affix material off the end of the word, restores
   * the strip string the rule removed on application, and checks the rule's condition
   * against the restored stem. A strip-only rule, whose affix material is empty, is
   * undone by restoring its strip string alone. Rules that neither add nor remove
   * material are handled by {@link #removeSuffixAllowingIdentity(String, Affix)}, and
   * candidates that would leave an empty stem are rejected. A word the affix material
   * covers entirely reverses a full-strip application, which hunspell only performs
   * when the affix file declares {@code FULLSTRIP}; without that declaration the rule
   * does not apply.
   *
   * @param word The surface form.
   * @param suffix The rule to undo.
   * @return The candidate stem, or {@code null} when the rule does not apply.
   */
  private String removeSuffix(String word, Affix suffix) {
    final String affix = suffix.affix();
    final String strip = suffix.strip();
    if (affix.isEmpty() && strip.isEmpty() || !word.endsWith(affix)
        || word.length() - affix.length() + strip.length() == 0
        || (word.length() == affix.length() && !dictionary.fullStrip())) {
      return null;
    }
    final String stem = word.substring(0, word.length() - affix.length()) + strip;
    return suffix.condition().matches(stem) ? stem : null;
  }

  /**
   * Undoes a suffix in a continuation sequence, including a rule that changes no
   * material. An identity rule still has to satisfy the condition.
   *
   * @param word The surface form at this point in the sequence.
   * @param suffix The rule to undo.
   * @return The candidate stem, or {@code null} when the rule does not apply.
   */
  private String removeSuffixAllowingIdentity(String word, Affix suffix) {
    if (isIdentityRule(suffix)) {
      return suffix.condition().matches(word) ? word : null;
    }
    return removeSuffix(word, suffix);
  }

  /**
   * Undoes a prefix in a continuation sequence, including a rule that changes no
   * material. An identity rule still has to satisfy the condition.
   *
   * @param word The surface form at this point in the sequence.
   * @param prefix The rule to undo.
   * @return The candidate stem, or {@code null} when the rule does not apply.
   */
  private String removePrefixAllowingIdentity(String word, Affix prefix) {
    if (isIdentityRule(prefix)) {
      return prefix.condition().matches(word) ? word : null;
    }
    return removePrefix(word, prefix);
  }

  /**
   * Checks whether an affix rule adds and strips no material.
   *
   * @param affix The rule to inspect.
   * @return {@code true} if applying the rule does not change the spelling.
   */
  private boolean isIdentityRule(Affix affix) {
    return affix.affix().isEmpty() && affix.strip().isEmpty();
  }

  /**
   * Undoes one prefix rule: cuts the affix material off the start of the word,
   * restores the strip string the rule removed on application, and checks the rule's
   * condition against the restored stem. A strip-only rule, whose affix material is
   * empty, is undone by restoring its strip string alone. Rules that neither add nor
   * remove material are handled by {@link #removePrefixAllowingIdentity(String, Affix)},
   * and candidates that would leave an empty stem are rejected. A
   * word the affix material covers entirely reverses a full-strip application, which
   * hunspell only performs when the affix file declares {@code FULLSTRIP}; without
   * that declaration the rule does not apply.
   *
   * @param word The surface form.
   * @param prefix The rule to undo.
   * @return The candidate stem, or {@code null} when the rule does not apply.
   */
  private String removePrefix(String word, Affix prefix) {
    final String affix = prefix.affix();
    final String strip = prefix.strip();
    if (affix.isEmpty() && strip.isEmpty() || !word.startsWith(affix)
        || word.length() - affix.length() + strip.length() == 0
        || (word.length() == affix.length() && !dictionary.fullStrip())) {
      return null;
    }
    final String stem = strip + word.substring(affix.length());
    return prefix.condition().matches(stem) ? stem : null;
  }
}
