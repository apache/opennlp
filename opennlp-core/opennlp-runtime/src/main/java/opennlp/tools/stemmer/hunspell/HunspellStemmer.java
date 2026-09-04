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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import opennlp.tools.commons.ThreadSafe;
import opennlp.tools.stemmer.Stemmer;
import opennlp.tools.stemmer.hunspell.HunspellDictionary.Affix;
import opennlp.tools.stemmer.hunspell.HunspellDictionary.CompoundPosition;
import opennlp.tools.util.StringUtil;

/**
 * A dictionary-backed {@link Stemmer} over a {@link HunspellDictionary}: a surface form
 * is reduced to listed dictionary words by removing one prefix and at most 2 suffixes,
 * subject to the loaded affix rules.
 *
 * <p>{@link #stem(CharSequence)} returns the first analysis, preferring the word's
 * dictionary entry; {@link #stemAll(CharSequence)} returns all distinct analyses. A
 * word with no analysis is returned unchanged, so the stemmer returns the input for
 * unknown words. A form containing uppercase characters is also analyzed in a
 * lowercase variant, so sentence-initial capitalization does not hide an entry.
 * Entries the dictionary marks as virtual stems ({@code NEEDAFFIX}), compound-only
 * parts ({@code ONLYINCOMPOUND}), or forbidden words ({@code FORBIDDENWORD}) do not
 * count as standalone analyses.</p>
 *
 * <p>Compound part search permits at most {@value #PART_CHECK_BUDGET} part-licensing
 * attempts per input word; further compound analyses are then skipped.
 * {@link Stemmer} thread safety depends on the implementation. This implementation
 * accesses only immutable dictionary state, so a single instance is
 * safe to share between threads.</p>
 *
 * @since 3.0.0
 */
@ThreadSafe
public final class HunspellStemmer implements Stemmer {

  /**
   * Maximum part-licensing attempts for one decomposition. The limit prevents
   * excessive work on adversarial input with one-character compound parts.
   */
  private static final int PART_CHECK_BUDGET = 2048;

  private final HunspellDictionary dictionary;

  /**
   * Initializes the stemmer.
   *
   * @param dictionary The dictionary used for analysis. Must not be {@code null}.
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
   * <p>Returns the first analysis, preferring a direct dictionary entry.</p>
   */
  @Override
  public CharSequence stem(CharSequence word) {
    final List<CharSequence> analyses = stemAll(word);
    return analyses.get(0);
  }

  /**
   * {@inheritDoc}
   *
   * <p>Returns all distinct analyses, or a single-element list of the unchanged word
   * when it has none.</p>
   */
  @Override
  public List<CharSequence> stemAll(CharSequence word) {
    if (word == null) {
      throw new IllegalArgumentException("word must not be null");
    }
    final String surface = word.toString();
    if (surface.isEmpty()) {
      // A zero-length word has no morphology. The guard prevents strip-only rules from
      // returning a non-empty stem.
      return List.of(surface);
    }
    final Set<String> analyses = new LinkedHashSet<>();
    for (final String variant : variants(surface)) {
      analyze(variant, analyses);
    }
    if (analyses.isEmpty() && dictionary.compoundsDeclared()) {
      for (final String variant : variants(surface)) {
        decompose(variant, surface, analyses);
      }
    }
    if (analyses.isEmpty()) {
      return List.of(surface);
    }
    return List.copyOf(analyses);
  }

  /**
   * Collects the case variants to analyze: the surface form first, followed by the
   * lowercase form when different. Ordering determines the result from
   * {@link #stem(CharSequence)}.
   *
   * @param surface The surface form.
   * @return A non-empty list in analysis order.
   */
  private List<String> variants(String surface) {
    final String lowered = StringUtil.toLowerCase(surface);
    return lowered.equals(surface) ? List.of(surface) : List.of(surface, lowered);
  }

  /**
   * Adds analyses of one case variant to the result set: the word's
   * dictionary entry, single suffix removal, twofold suffix removal through
   * continuation classes, single prefix removal, and cross-product removal of one
   * prefix with one or 2 suffixes. Insertion order into the set sets the preference
   * order reported by {@link #stemAll(CharSequence)}.
   *
   * @param word The case variant to analyze.
   * @param analyses The mutable, insertion-ordered set collecting the stems found.
   */
  private void analyze(String word, Set<String> analyses) {
    final List<int[]> entries = dictionary.lookup(word);
    if (entries != null) {
      final boolean validStandalone = dictionary.validStandalone(entries);
      if (validStandalone) {
        analyses.add(word);
      }
      if (!validStandalone && dictionary.anyForbidden(entries)) {
        return;
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
   * Decomposes a word into listed compound parts when affix analysis has no result. Each part must
   * meet position, length, and part-count constraints. A part can use a direct entry or one affix
   * removal. Successful splits add part stems in text order. Forbidden entries are not decomposed.
   *
   * @param word The case variant to decompose.
   * @param surface The source surface form used to check {@code CHECKCOMPOUNDCASE} boundaries.
   * @param analyses The mutable, insertion-ordered set collecting the part stems.
   */
  private void decompose(String word, String surface, Set<String> analyses) {
    final List<int[]> entries = dictionary.lookup(word);
    if (entries != null && dictionary.anyForbidden(entries)) {
      return;
    }
    if (word.codePointCount(0, word.length()) < 2L * dictionary.compoundMin()) {
      return;
    }
    // lowercasing may change the length in exceptional mappings, in which case the
    // offsets no longer align and the variant is the usable case source
    final String caseSource = surface.length() == word.length() ? surface : word;
    search(word, caseSource, 0, new ArrayList<>(), new ArrayList<>(), analyses,
        new int[] {PART_CHECK_BUDGET});
  }

  /**
   * Extends a partial decomposition with the part starting at {@code from}, trying
   * each permitted length and recursing on the remainder. The boundary into this
   * part honors the {@code CHECKCOMPOUNDCASE} and {@code CHECKCOMPOUNDTRIPLE}
   * declarations, a part repeating the left neighbor honors
   * {@code CHECKCOMPOUNDDUP}, and a completed decomposition adds all part
   * stems into the analyses in part order.
   *
   * @param word The case variant under decomposition.
   * @param caseSource The character-case source for junction checks, the surface
   *                   form when offsets align with the variant.
   * @param from The index the next part starts at.
   * @param surfaces The surface strings of the parts taken so far.
   * @param stems The licensed stems of the parts taken so far, one list per part.
   * @param analyses The mutable, insertion-ordered set collecting the part stems.
   * @param budget The remaining part-licensing attempts, decremented in place.
   */
  private void search(String word, String caseSource, int from, List<String> surfaces,
      List<List<String>> stems, Set<String> analyses, int[] budget) {
    if (from > 0 && violatesBoundaryChecks(word, caseSource, from)) {
      return;
    }
    final int min = dictionary.compoundMin();
    final int max = dictionary.compoundWordMax();
    final boolean first = from == 0;
    final int remaining = word.codePointCount(from, word.length());
    // Reserve enough text for another part. The opening part must also reserve a closing part.
    if ((long) remaining >= 2L * min && (max == 0 || surfaces.size() + 2 <= max)) {
      final int lastEnd = word.offsetByCodePoints(word.length(), -min);
      for (int end = word.offsetByCodePoints(from, min); end <= lastEnd;
           end = word.offsetByCodePoints(end, 1)) {
        if (budget[0] <= 0) {
          return;
        }
        budget[0]--;
        final String part = word.substring(from, end);
        if (duplicatesNeighbor(part, surfaces)) {
          continue;
        }
        final List<String> partStems = partStems(part,
            first ? CompoundPosition.BEGIN : CompoundPosition.MIDDLE, first, false);
        if (partStems.isEmpty()) {
          continue;
        }
        surfaces.add(part);
        stems.add(partStems);
        search(word, caseSource, end, surfaces, stems, analyses, budget);
        surfaces.remove(surfaces.size() - 1);
        stems.remove(stems.size() - 1);
      }
    }
    // The closing part consumes the remainder. A compound requires multiple parts.
    if (first || remaining < min
        || (max > 0 && surfaces.size() + 1 > max) || budget[0] <= 0) {
      return;
    }
    budget[0]--;
    final String part = word.substring(from);
    if (duplicatesNeighbor(part, surfaces)) {
      return;
    }
    final List<String> partStems = partStems(part, CompoundPosition.END, false, true);
    if (partStems.isEmpty()) {
      return;
    }
    for (final List<String> earlier : stems) {
      analyses.addAll(earlier);
    }
    analyses.addAll(partStems);
  }

  /**
   * Applies {@code CHECKCOMPOUNDDUP}: a part must not repeat the preceding part.
   *
   * @param part The candidate part.
   * @param surfaces The surface strings of the parts taken so far.
   * @return {@code true} if the check rejects this part.
   */
  private boolean duplicatesNeighbor(String part, List<String> surfaces) {
    return dictionary.checkCompoundDup() && !surfaces.isEmpty()
        && part.equals(surfaces.get(surfaces.size() - 1));
  }

  /**
   * Applies the character-level boundary declarations at the junction before
   * {@code from}: {@code CHECKCOMPOUNDCASE} rejects an uppercase character on either
   * side of the junction, and {@code CHECKCOMPOUNDTRIPLE} rejects a character repeated
   * across the junction.
   *
   * @param word The case variant under decomposition.
   * @param caseSource The character-case source for the uppercase check.
   * @param from The positive index where the junction begins.
   * @return {@code true} if a configured check rejects this junction.
   */
  private boolean violatesBoundaryChecks(String word, String caseSource, int from) {
    final int before = word.codePointBefore(from);
    final int after = word.codePointAt(from);
    if (dictionary.checkCompoundCase()
        && (Character.isUpperCase(caseSource.codePointBefore(from))
            || Character.isUpperCase(caseSource.codePointAt(from)))) {
      return true;
    }
    if (dictionary.checkCompoundTriple() && before == after) {
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
   * Collects listed stems that permit a part at the requested compound position: the part
   * as a direct entry, or an entry plus one suffix or prefix where removal produces
   * a listed stem, zero-material rules included, because published dictionaries
   * position their linking forms through zero and dash suffixes. An affix at a
   * compound-internal boundary must contain the permit flag, a suffix facing the next
   * part or a prefix facing the previous one. A part not found as written is also
   * tried with the first letter uppercased to match nouns listed with initial capitals.
   *
   * @param part The part's surface text.
   * @param position The part's place in the compound.
   * @param first Whether the part opens the word.
   * @param last Whether the part closes the word.
   * @return The matching stems in discovery order.
   */
  private List<String> partStems(String part, CompoundPosition position,
      boolean first, boolean last) {
    final Set<String> stems = new LinkedHashSet<>();
    collectPartStems(part, position, first, last, stems);
    if (stems.isEmpty() && !part.isEmpty()) {
      final int initial = part.codePointAt(0);
      final int upper = Character.toUpperCase(initial);
      if (upper != initial) {
        collectPartStems(new StringBuilder().appendCodePoint(upper)
            .append(part, Character.charCount(initial), part.length()).toString(),
            position, first, last, stems);
      }
    }
    return List.copyOf(stems);
  }

  /**
   * Collects direct and affixed stems for one spelling of a part.
   *
   * @param part The part spelling to look up.
   * @param position The part's place in the compound.
   * @param first Whether the part opens the word.
   * @param last Whether the part closes the word.
   * @param stems The mutable, insertion-ordered set collecting the stems.
   */
  private void collectPartStems(String part, CompoundPosition position,
      boolean first, boolean last, Set<String> stems) {
    final List<int[]> entries = dictionary.lookup(part);
    if (entries != null && dictionary.mayStand(entries, position)) {
      stems.add(part);
    }
    for (final Affix suffix : dictionary.suffixesEndingWith(
        part.codePointBefore(part.length()))) {
      collectAffixedPartStem(part, suffix, true, position, last, stems);
    }
    for (final Affix suffix : dictionary.suffixesWithoutMaterial()) {
      collectAffixedPartStem(part, suffix, true, position, last, stems);
    }
    for (final Affix prefix : dictionary.prefixesStartingWith(part.codePointAt(0))) {
      collectAffixedPartStem(part, prefix, false, position, first, stems);
    }
    for (final Affix prefix : dictionary.prefixesWithoutMaterial()) {
      collectAffixedPartStem(part, prefix, false, position, first, stems);
    }
  }

  /**
   * Adds the stem of one affixed part reading when the rule and the stem's entry admit
   * it at the position.
   *
   * @param part The part spelling under analysis.
   * @param affix The rule to undo.
   * @param suffix Whether the rule is a suffix rule.
   * @param position The part's place in the compound.
   * @param atEdge Whether the part occurs at the word end the rule faces, the closing part
   *               for a suffix rule and the opening part for a prefix rule; an affix
   *               facing another part instead needs the permit flag.
   * @param stems The mutable, insertion-ordered set collecting the stems.
   */
  private void collectAffixedPartStem(String part, Affix affix, boolean suffix,
      CompoundPosition position, boolean atEdge, Set<String> stems) {
    if (dictionary.circumfixOnly(affix) || dictionary.forbidsInCompound(affix)
        || (!atEdge && !dictionary.permitsInside(affix))) {
      return;
    }
    final String stem = removeAffixInCompound(part, affix, suffix);
    if (stem == null) {
      return;
    }
    final List<int[]> flagSets = dictionary.lookup(stem);
    if (flagSets != null && dictionary.supportsPart(flagSets, affix.flag(), position,
        dictionary.affixAdmits(affix, position))) {
      stems.add(stem);
    }
  }

  /**
   * Undoes one affix rule on a compound part. Unlike the standalone removals, a rule
   * that adds and removes no material is undone here with the condition checked because
   * dictionaries use such rules to position compound parts.
   *
   * @param part The part spelling under analysis.
   * @param affix The rule to undo.
   * @param suffix Whether the rule is a suffix rule.
   * @return The candidate stem, or {@code null} when the rule does not apply.
   */
  private String removeAffixInCompound(String part, Affix affix, boolean suffix) {
    if (isIdentityRule(affix)) {
      return affix.condition().matches(part) ? part : null;
    }
    return suffix ? removeSuffix(part, affix) : removePrefix(part, affix);
  }

  /**
   * Undoes one suffix rule and, through continuation classes, one further suffix on
   * the intermediate stem, adding dictionary-confirmed analyses. A rule that applies
   * only inside compounds or requires the matching circumfix member is not undone
   * because no prefix accompanies this path. A rule requiring a further affix produces
   * no single-removal analysis. An identity rule also produces no single-removal
   * analysis, but it can complete a 2-suffix analysis through continuation classes.
   *
   * @param word The case variant under analysis.
   * @param suffix The suffix rule to undo.
   * @param analyses The mutable, insertion-ordered set collecting the stems found.
   */
  private void undoSuffix(String word, Affix suffix, Set<String> analyses) {
    if (dictionary.compoundOnly(suffix) || dictionary.circumfixOnly(suffix)) {
      return;
    }
    final boolean identity = isIdentityRule(suffix);
    final String stem = removeSuffixAllowingIdentity(word, suffix);
    if (stem == null) {
      return;
    }
    if (!identity && !dictionary.needsFurtherAffix(suffix)) {
      final List<int[]> flagSets = dictionary.lookup(stem);
      if (flagSets != null && dictionary.supports(flagSets, suffix.flag())) {
        analyses.add(stem);
      }
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
      Set<String> analyses) {
    if (!inner.allowsContinuation(outer.flag()) || dictionary.compoundOnly(inner)
        || dictionary.circumfixOnly(inner)) {
      return;
    }
    final String doubleStem = removeSuffixAllowingIdentity(stem, inner);
    if (doubleStem == null) {
      return;
    }
    final List<int[]> innerFlags = dictionary.lookup(doubleStem);
    if (innerFlags != null && dictionary.supports(innerFlags, inner.flag())) {
      analyses.add(doubleStem);
    }
  }

  /**
   * Undoes one prefix rule and, for cross-product rules, one further suffix on the
   * intermediate stem, adding dictionary-confirmed analyses. A rule that
   * applies only inside compounds is not undone at all. A rule marked as needing a
   * further affix or the matching circumfix member produces no single-removal analysis.
   * An identity rule also produces no single-removal analysis. A valid cross-product
   * suffix can combine with either kind of rule.
   *
   * @param word The case variant under analysis.
   * @param prefix The prefix rule to undo.
   * @param analyses The mutable, insertion-ordered set collecting the stems found.
   */
  private void undoPrefix(String word, Affix prefix, Set<String> analyses) {
    if (dictionary.compoundOnly(prefix)) {
      return;
    }
    final boolean identity = isIdentityRule(prefix);
    final String stem = identity
        ? (prefix.condition().matches(word) ? word : null)
        : removePrefix(word, prefix);
    if (stem == null) {
      return;
    }
    if (!identity && !dictionary.needsFurtherAffix(prefix)
        && !dictionary.circumfixOnly(prefix)) {
      final List<int[]> flagSets = dictionary.lookup(stem);
      if (flagSets != null && dictionary.supports(flagSets, prefix.flag())) {
        analyses.add(stem);
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
   * Undoes the suffix member of a cross-product removal when both rules opt in. Prefix
   * and suffix must agree on circumfixing, so a rule without the marker cannot combine
   * with a marked rule.
   *
   * @param stem The intermediate stem after the prefix removal.
   * @param prefix The already-undone prefix rule.
   * @param suffix The candidate suffix rule.
   * @param analyses The mutable, insertion-ordered set collecting the stems found.
   */
  private void undoCrossProductSuffix(String stem, Affix prefix, Affix suffix,
      Set<String> analyses) {
    if (!suffix.crossProduct() || dictionary.compoundOnly(suffix)
        || dictionary.circumfixOnly(prefix) != dictionary.circumfixOnly(suffix)) {
      return;
    }
    final String doubleStem = removeSuffix(stem, suffix);
    if (doubleStem == null) {
      return;
    }
    // Each rule satisfies a needs-further-affix marker on the other. Both rule flags
    // must occur in one homonym's flag set.
    final List<int[]> both = dictionary.lookup(doubleStem);
    if (both != null && dictionary.supports(both, prefix, suffix)
        && !(dictionary.needsFurtherAffix(prefix)
            && dictionary.needsFurtherAffix(suffix))) {
      analyses.add(doubleStem);
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
      Affix inner, Set<String> analyses) {
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
    final List<int[]> flagSets = dictionary.lookup(root);
    if (flagSets != null && dictionary.supports(flagSets, prefix, inner)) {
      analyses.add(root);
    }
  }

  /**
   * Undoes one suffix rule: cuts the affix material off the end of the word, restores
   * the strip string the rule removed on application, and checks the rule's condition
   * on the restored stem. A strip-only rule with empty affix material is undone by
   * restoring the strip string. Rules with no added or stripped material and candidates
   * that produce an empty stem are rejected. A word covered completely by the affix
   * material represents a full-strip application, supported only when the affix file
   * specifies {@code FULLSTRIP}.
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
   * Checks whether an affix rule adds and strips no material.
   *
   * @param affix The rule to inspect.
   * @return {@code true} if applying the rule does not change the spelling.
   */
  private static boolean isIdentityRule(Affix affix) {
    return affix.affix().isEmpty() && affix.strip().isEmpty();
  }

  /**
   * Undoes one prefix rule: cuts the affix material off the start of the word,
   * restores the strip string the rule removed on application, and checks the rule's
   * condition on the restored stem. A strip-only rule with empty affix material is
   * undone by restoring the strip string. Rules with no added or stripped material and
   * candidates that produce an empty stem are rejected. A word covered completely by
   * the affix material represents a full-strip application, supported only when the
   * affix file specifies {@code FULLSTRIP}.
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
