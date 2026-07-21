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
 * is reduced to the dictionary words it can be derived from by removing one suffix, one
 * prefix, or a cross-product combination of both.
 *
 * <p>{@link #stem(CharSequence)} returns the first analysis, preferring the word's own
 * dictionary entry; {@link #stemAll(CharSequence)} returns every distinct analysis. A
 * word with no analysis is returned unchanged, so the stemmer degrades to identity on
 * unknown vocabulary. A form containing uppercase characters is also analyzed in its
 * lowercase variant, so sentence-initial capitalization does not hide an entry.
 * Entries the dictionary marks as virtual stems ({@code NEEDAFFIX}), compound-only
 * parts ({@code ONLYINCOMPOUND}), or forbidden words ({@code FORBIDDENWORD}) never
 * count as standalone analyses, matching how hunspell reads those flags.</p>
 *
 * <p>The {@link Stemmer} interface leaves thread safety to the implementation. This
 * implementation reads only the immutable dictionary state, so a single instance is
 * safe to share between threads.</p>
 *
 */
@ThreadSafe
public class HunspellStemmer implements Stemmer {

  /**
   * The most part-licensing attempts one decomposition search may spend. Compounding
   * searches every split of every tail, which on adversarial input with a
   * one-character minimum part length grows without useful bound; the budget stops
   * the search there, missing analyses rather than stalling, in line with the
   * engine's fail-closed posture.
   */
  private static final int PART_CHECK_BUDGET = 2048;

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

  @Override
  public CharSequence stem(CharSequence word) {
    final List<CharSequence> analyses = stemAll(word);
    return analyses.get(0);
  }

  @Override
  public List<CharSequence> stemAll(CharSequence word) {
    if (word == null) {
      throw new IllegalArgumentException("word must not be null");
    }
    final String surface = word.toString();
    if (surface.isEmpty()) {
      // a zero-length word has no morphology; without this guard a strip-only rule
      // could restore its strip string onto nothing and answer a non-empty stem
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
   * Collects the case variants to analyze: the surface form first, then its lowercase
   * form when the two differ. Ordering matters because the first analysis found wins
   * in {@link #stem(CharSequence)}.
   *
   * @param surface The surface form.
   * @return The variants in analysis order. Never {@code null} or empty.
   */
  private List<String> variants(String surface) {
    final String lowered = StringUtil.toLowerCase(surface);
    return lowered.equals(surface) ? List.of(surface) : List.of(surface, lowered);
  }

  /**
   * Adds every analysis of one case variant to the result set: the word's own
   * dictionary entry, single suffix removal, twofold suffix removal through
   * continuation classes, single prefix removal, and cross-product removal of one
   * prefix together with one suffix. Insertion order into the set fixes the
   * preference order reported by {@link #stemAll(CharSequence)}.
   *
   * @param word The case variant to analyze.
   * @param analyses The mutable, insertion-ordered set collecting the stems found.
   */
  private void analyze(String word, Set<String> analyses) {
    final List<int[]> own = dictionary.lookup(word);
    if (own != null && dictionary.validStandalone(own)) {
      analyses.add(word);
    }
    // Only rules whose affix material ends in the word's last character can be
    // undone from it, so each scan walks that bucket plus the strip-only rules
    // instead of the whole inventory.
    for (final Affix suffix : dictionary.suffixesEndingWith(word.charAt(word.length() - 1))) {
      undoSuffix(word, suffix, analyses);
    }
    for (final Affix suffix : dictionary.suffixesWithoutMaterial()) {
      undoSuffix(word, suffix, analyses);
    }
    for (final Affix prefix : dictionary.prefixesStartingWith(word.charAt(0))) {
      undoPrefix(word, prefix, analyses);
    }
    for (final Affix prefix : dictionary.prefixesWithoutMaterial()) {
      undoPrefix(word, prefix, analyses);
    }
  }

  /**
   * Decomposes a word into listed compound parts when the affix analysis found
   * nothing: the first part must be admitted to open a compound, every further part
   * to continue or close one, each at least the declared minimum length and counted
   * against the declared maximum. A part stands on its own entry or on an entry plus
   * one affix, the way published dictionaries position their linking forms through
   * zero or dash suffixes. The stems of the parts of every successful splitting are
   * reported left to right, so the head-most material comes last. A word the
   * dictionary lists as forbidden never decomposes; that is how one specific
   * ill-formed compound is blocked while its parts stay productive.
   *
   * @param word The case variant to decompose.
   * @param surface The surface form the variant was derived from; character case at
   *                junctions is judged against it, so lowercasing a variant cannot
   *                sidestep a {@code CHECKCOMPOUNDCASE} declaration.
   * @param analyses The mutable, insertion-ordered set collecting the part stems.
   */
  private void decompose(String word, String surface, Set<String> analyses) {
    final List<int[]> own = dictionary.lookup(word);
    if (own != null && dictionary.anyForbidden(own)) {
      return;
    }
    if (word.length() < 2 * dictionary.compoundMin()) {
      return;
    }
    // lowercasing may change the length in exceptional mappings, in which case the
    // offsets no longer align and the variant itself is the only usable case source
    final String caseSource = surface.length() == word.length() ? surface : word;
    search(word, caseSource, 0, new ArrayList<>(), new ArrayList<>(), analyses,
        new int[] {PART_CHECK_BUDGET});
  }

  /**
   * Extends a partial decomposition with the part starting at {@code from}, trying
   * every admissible length and recursing on the remainder. The boundary into this
   * part honors the {@code CHECKCOMPOUNDCASE} and {@code CHECKCOMPOUNDTRIPLE}
   * declarations, a part repeating its left neighbor honors
   * {@code CHECKCOMPOUNDDUP}, and a completed decomposition flushes every part's
   * stems into the analyses in part order.
   *
   * @param word The case variant under decomposition.
   * @param caseSource The character-case source for junction checks, the surface
   *                   form when its offsets align with the variant.
   * @param from The index the next part starts at.
   * @param surfaces The surface strings of the parts taken so far.
   * @param stems The licensed stems of the parts taken so far, one list per part.
   * @param analyses The mutable, insertion-ordered set collecting the part stems.
   * @param budget The remaining part-licensing attempts, counted down in place.
   */
  private void search(String word, String caseSource, int from, List<String> surfaces,
      List<List<String>> stems, Set<String> analyses, int[] budget) {
    if (from > 0 && violatesBoundaryChecks(word, caseSource, from)) {
      return;
    }
    final int min = dictionary.compoundMin();
    final int max = dictionary.compoundWordMax();
    final boolean first = from == 0;
    // every split leaving room for a further part; a first-position part must also
    // leave the closing part, so the whole word is never one part
    if (max == 0 || surfaces.size() + 2 <= max) {
      for (int end = from + min; end <= word.length() - min; end++) {
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
    // the closing part takes the whole remainder; a compound has at least two parts
    if (first || word.length() - from < min
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
   * Applies the {@code CHECKCOMPOUNDDUP} declaration: a part must not repeat the
   * part directly before it.
   *
   * @param part The candidate part.
   * @param surfaces The surface strings of the parts taken so far.
   * @return {@code true} if the declaration forbids this part here.
   */
  private boolean duplicatesNeighbor(String part, List<String> surfaces) {
    return dictionary.checkCompoundDup() && !surfaces.isEmpty()
        && part.equals(surfaces.get(surfaces.size() - 1));
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
   * @return {@code true} if a declaration forbids this junction.
   */
  private boolean violatesBoundaryChecks(String word, String caseSource, int from) {
    final char before = word.charAt(from - 1);
    final char after = word.charAt(from);
    if (dictionary.checkCompoundCase()
        && (Character.isUpperCase(caseSource.charAt(from - 1))
            || Character.isUpperCase(caseSource.charAt(from)))) {
      return true;
    }
    if (dictionary.checkCompoundTriple() && before == after
        && ((from >= 2 && word.charAt(from - 2) == after)
            || (from + 1 < word.length() && word.charAt(from + 1) == after))) {
      return true;
    }
    return false;
  }

  /**
   * Collects the listed stems that admit one part at its compound position: the part
   * as its own entry, or an entry plus one suffix or one prefix whose removal leaves
   * a listed stem, zero-material rules included, because published dictionaries
   * position their linking forms through zero and dash suffixes. An affix at a
   * compound-internal boundary must carry the permit flag, a suffix facing the next
   * part or a prefix facing the previous one. A part not found as written is also
   * tried with its first letter uppercased, the way nouns listed capitalized appear
   * lowercase inside a compound.
   *
   * @param part The part's surface text.
   * @param position The part's place in the compound.
   * @param first Whether the part opens the word.
   * @param last Whether the part closes the word.
   * @return The stems admitting the part, in discovery order. Never {@code null}.
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
   * Collects the stems admitting one spelling of a part, bare and through one affix.
   *
   * @param part The part spelling to look up.
   * @param position The part's place in the compound.
   * @param first Whether the part opens the word.
   * @param last Whether the part closes the word.
   * @param stems The mutable, insertion-ordered set collecting the stems.
   */
  private void collectPartStems(String part, CompoundPosition position,
      boolean first, boolean last, Set<String> stems) {
    final List<int[]> own = dictionary.lookup(part);
    if (own != null && dictionary.mayStand(own, position)) {
      stems.add(part);
    }
    for (final Affix suffix : dictionary.suffixesEndingWith(part.charAt(part.length() - 1))) {
      collectSuffixedPartStem(part, suffix, position, last, stems);
    }
    for (final Affix suffix : dictionary.suffixesWithoutMaterial()) {
      collectSuffixedPartStem(part, suffix, position, last, stems);
    }
    for (final Affix prefix : dictionary.prefixesStartingWith(part.charAt(0))) {
      collectPrefixedPartStem(part, prefix, position, first, stems);
    }
    for (final Affix prefix : dictionary.prefixesWithoutMaterial()) {
      collectPrefixedPartStem(part, prefix, position, first, stems);
    }
  }

  /**
   * Adds the stem of one suffixed part reading when the rule and the stem's entry
   * admit it at the position.
   *
   * @param part The part spelling under analysis.
   * @param suffix The suffix rule to undo.
   * @param position The part's place in the compound.
   * @param last Whether the part closes the word.
   * @param stems The mutable, insertion-ordered set collecting the stems.
   */
  private void collectSuffixedPartStem(String part, Affix suffix,
      CompoundPosition position, boolean last, Set<String> stems) {
    if (dictionary.circumfixOnly(suffix) || dictionary.forbidsInCompound(suffix)
        || (!last && !dictionary.permitsInside(suffix))) {
      return;
    }
    final String stem = removeAffixInCompound(part, suffix, true);
    if (stem == null) {
      return;
    }
    final List<int[]> flagSets = dictionary.lookup(stem);
    if (flagSets != null && dictionary.supportsPart(flagSets, suffix.flag(), position,
        dictionary.affixAdmits(suffix, position))) {
      stems.add(stem);
    }
  }

  /**
   * Adds the stem of one prefixed part reading when the rule and the stem's entry
   * admit it at the position.
   *
   * @param part The part spelling under analysis.
   * @param prefix The prefix rule to undo.
   * @param position The part's place in the compound.
   * @param first Whether the part opens the word.
   * @param stems The mutable, insertion-ordered set collecting the stems.
   */
  private void collectPrefixedPartStem(String part, Affix prefix,
      CompoundPosition position, boolean first, Set<String> stems) {
    if (dictionary.circumfixOnly(prefix) || dictionary.forbidsInCompound(prefix)
        || (!first && !dictionary.permitsInside(prefix))) {
      return;
    }
    final String stem = removeAffixInCompound(part, prefix, false);
    if (stem == null) {
      return;
    }
    final List<int[]> flagSets = dictionary.lookup(stem);
    if (flagSets != null && dictionary.supportsPart(flagSets, prefix.flag(), position,
        dictionary.affixAdmits(prefix, position))) {
      stems.add(stem);
    }
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
    if (affix.affix().isEmpty() && affix.strip().isEmpty()) {
      return affix.condition().matches(part) ? part : null;
    }
    return suffix ? removeSuffix(part, affix) : removePrefix(part, affix);
  }

  /**
   * Undoes one suffix rule and, through continuation classes, one further suffix on
   * the intermediate stem, adding every dictionary-confirmed analysis. A rule that
   * applies only inside compounds or only as half of a circumfix is not undone at all,
   * the latter because no prefix accompanies it on this path; a rule marked as needing
   * a further affix yields no single-removal analysis, because the surface form it
   * makes alone is a virtual stem; its twofold analyses stand, the inner affix being
   * exactly the further one required.
   *
   * @param word The case variant under analysis.
   * @param suffix The suffix rule to undo.
   * @param analyses The mutable, insertion-ordered set collecting the stems found.
   */
  private void undoSuffix(String word, Affix suffix, Set<String> analyses) {
    if (dictionary.compoundOnly(suffix) || dictionary.circumfixOnly(suffix)) {
      return;
    }
    final String stem = removeSuffix(word, suffix);
    if (stem == null) {
      return;
    }
    if (!dictionary.needsFurtherAffix(suffix)) {
      final List<int[]> flagSets = dictionary.lookup(stem);
      if (flagSets != null && dictionary.supports(flagSets, suffix.flag())) {
        analyses.add(stem);
      }
    }
    for (final Affix inner : dictionary.suffixesEndingWith(stem.charAt(stem.length() - 1))) {
      undoInnerSuffix(stem, suffix, inner, analyses);
    }
    for (final Affix inner : dictionary.suffixesWithoutMaterial()) {
      undoInnerSuffix(stem, suffix, inner, analyses);
    }
  }

  /**
   * Undoes the second suffix of a twofold removal when the inner rule's continuation
   * classes allow it after the outer one.
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
    final String doubleStem = removeSuffix(stem, inner);
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
   * intermediate stem, adding every dictionary-confirmed analysis. A rule that
   * applies only inside compounds is not undone at all. A rule marked as needing a
   * further affix or as half of a circumfix yields no single-removal analysis; its
   * cross-product analyses stand, the suffix being exactly the further affix or the
   * other circumfix half required.
   *
   * @param word The case variant under analysis.
   * @param prefix The prefix rule to undo.
   * @param analyses The mutable, insertion-ordered set collecting the stems found.
   */
  private void undoPrefix(String word, Affix prefix, Set<String> analyses) {
    if (dictionary.compoundOnly(prefix)) {
      return;
    }
    final String stem = removePrefix(word, prefix);
    if (stem == null) {
      return;
    }
    if (!dictionary.needsFurtherAffix(prefix) && !dictionary.circumfixOnly(prefix)) {
      final List<int[]> flagSets = dictionary.lookup(stem);
      if (flagSets != null && dictionary.supports(flagSets, prefix.flag())) {
        analyses.add(stem);
      }
    }
    if (!prefix.crossProduct()) {
      return;
    }
    for (final Affix suffix : dictionary.suffixesEndingWith(stem.charAt(stem.length() - 1))) {
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
      Set<String> analyses) {
    if (!suffix.crossProduct() || dictionary.compoundOnly(suffix)
        || dictionary.circumfixOnly(prefix) != dictionary.circumfixOnly(suffix)) {
      return;
    }
    final String doubleStem = removeSuffix(stem, suffix);
    if (doubleStem == null) {
      return;
    }
    // a needs-further-affix marker on either rule is satisfied by the other rule,
    // so no such check applies here; both flags must sit in one homonym's flag set
    final List<int[]> both = dictionary.lookup(doubleStem);
    if (both != null && dictionary.supports(both, prefix.flag(), suffix.flag())) {
      analyses.add(doubleStem);
    }
  }

  /**
   * Undoes one suffix rule: cuts the affix material off the end of the word, restores
   * the strip string the rule removed on application, and checks the rule's condition
   * against the restored stem. A strip-only rule, whose affix material is empty, is
   * undone by restoring its strip string alone. Rules that neither add nor remove
   * material and candidates that would leave an empty stem are rejected.
   *
   * @param word The surface form.
   * @param suffix The rule to undo.
   * @return The candidate stem, or {@code null} when the rule does not apply.
   */
  private String removeSuffix(String word, Affix suffix) {
    final String affix = suffix.affix();
    final String strip = suffix.strip();
    if (affix.isEmpty() && strip.isEmpty() || !word.endsWith(affix)
        || word.length() - affix.length() + strip.length() == 0) {
      return null;
    }
    final String stem = word.substring(0, word.length() - affix.length()) + strip;
    return suffix.condition().matches(stem) ? stem : null;
  }

  /**
   * Undoes one prefix rule: cuts the affix material off the start of the word,
   * restores the strip string the rule removed on application, and checks the rule's
   * condition against the restored stem. A strip-only rule, whose affix material is
   * empty, is undone by restoring its strip string alone. Rules that neither add nor
   * remove material and candidates that would leave an empty stem are rejected.
   *
   * @param word The surface form.
   * @param prefix The rule to undo.
   * @return The candidate stem, or {@code null} when the rule does not apply.
   */
  private String removePrefix(String word, Affix prefix) {
    final String affix = prefix.affix();
    final String strip = prefix.strip();
    if (affix.isEmpty() && strip.isEmpty() || !word.startsWith(affix)
        || word.length() - affix.length() + strip.length() == 0) {
      return null;
    }
    final String stem = strip + word.substring(affix.length());
    return prefix.condition().matches(stem) ? stem : null;
  }
}
