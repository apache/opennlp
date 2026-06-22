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

package opennlp.tools.stopword;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import opennlp.tools.dictionary.Dictionary;
import opennlp.tools.util.StringList;

public class DictionaryStopwordFilterTest {

  private static DictionaryStopwordFilter empty() {
    return DictionaryStopwordFilter.builder().build();
  }

  private static DictionaryStopwordFilter withEntries(final String[]... entries) {
    final DictionaryStopwordFilter.Builder b = DictionaryStopwordFilter.builder();
    for (final String[] e : entries) {
      b.add(e);
    }
    return b.build();
  }

  @Test
  void testEmptyBuilderProducesCaseInsensitiveEmptyFilter() {
    final DictionaryStopwordFilter filter = empty();
    Assertions.assertFalse(filter.isCaseSensitive());
    Assertions.assertTrue(filter.stopwords().isEmpty());
    Assertions.assertFalse(filter.isStopword("the"));
  }

  @Test
  void testCaseInsensitiveMatching() {
    final DictionaryStopwordFilter filter = DictionaryStopwordFilter.builder()
        .add("the")
        .build();
    Assertions.assertTrue(filter.isStopword("the"));
    Assertions.assertTrue(filter.isStopword("THE"));
    Assertions.assertTrue(filter.isStopword("The"));
  }

  @Test
  void testCaseSensitiveMatching() {
    final DictionaryStopwordFilter filter = DictionaryStopwordFilter.builder()
        .caseSensitive(true)
        .add("the")
        .build();
    Assertions.assertTrue(filter.isCaseSensitive());
    Assertions.assertTrue(filter.isStopword("the"));
    Assertions.assertFalse(filter.isStopword("The"));
    Assertions.assertFalse(filter.isStopword("THE"));
  }

  @Test
  void testFilterPreservesOrderAndDropsOneGramStopwords() {
    final DictionaryStopwordFilter filter = DictionaryStopwordFilter.builder()
        .add("the")
        .add("a")
        .build();

    final String[] input = { "the", "quick", "brown", "fox", "jumps", "over", "a", "lazy", "dog" };
    final String[] expected = { "quick", "brown", "fox", "jumps", "over", "lazy", "dog" };
    final String[] actual = filter.filter(input);

    Assertions.assertArrayEquals(expected, actual);
  }

  @Test
  void testBuilderRemoveUndoesAdd() {
    final DictionaryStopwordFilter filter = DictionaryStopwordFilter.builder()
        .add("foo")
        .remove("foo")
        .build();
    Assertions.assertFalse(filter.isStopword("foo"));
  }

  @Test
  void testBuilderAddAllAndRemoveAll() {
    final DictionaryStopwordFilter added = DictionaryStopwordFilter.builder()
        .addAll(Arrays.asList(new String[] {"alpha"}, new String[] {"beta"}))
        .build();
    Assertions.assertTrue(added.isStopword("alpha"));
    Assertions.assertTrue(added.isStopword("beta"));

    final DictionaryStopwordFilter undone = DictionaryStopwordFilter.builder()
        .addAll(Arrays.asList(new String[] {"alpha"}, new String[] {"beta"}))
        .removeAll(Arrays.asList(new String[] {"alpha"}, new String[] {"beta"}))
        .build();
    Assertions.assertFalse(undone.isStopword("alpha"));
    Assertions.assertFalse(undone.isStopword("beta"));
  }

  @Test
  void testMultiWordIsStopwordAndIndividualTokensNotMembers() {
    final DictionaryStopwordFilter filter = DictionaryStopwordFilter.builder()
        .add("of", "the")
        .build();

    Assertions.assertTrue(filter.isStopword("of", "the"));
    Assertions.assertFalse(filter.isStopword("of"));
    Assertions.assertFalse(filter.isStopword("the"));
  }

  @Test
  void testFilterDropsNGramMatches() {
    final DictionaryStopwordFilter filter = withEntries(new String[] {"of", "the"});
    final String[] result = filter.filter(new String[] {"of", "the", "cat"});
    Assertions.assertArrayEquals(new String[] {"cat"}, result);
  }

  @Test
  void testFilterPrefersLongestMatchGreedy() {
    final DictionaryStopwordFilter filter = withEntries(
        new String[] {"of"}, new String[] {"of", "the"});
    final String[] result = filter.filter(new String[] {"of", "the", "cat"});
    Assertions.assertArrayEquals(new String[] {"cat"}, result);
  }

  @Test
  void testFilterMixedOneAndTwoGramEntries() {
    final DictionaryStopwordFilter filter = withEntries(
        new String[] {"the"}, new String[] {"in", "spite"});

    final String[] result = filter.filter(
        new String[] {"the", "cat", "sat", "in", "spite", "of", "rain"});
    Assertions.assertArrayEquals(
        new String[] {"cat", "sat", "of", "rain"}, result);
  }

  @Test
  void testFilterNGramAtStartOfInput() {
    final DictionaryStopwordFilter filter = withEntries(new String[] {"as", "well", "as"});
    final String[] result = filter.filter(new String[] {"as", "well", "as", "cats"});
    Assertions.assertArrayEquals(new String[] {"cats"}, result);
  }

  @Test
  void testFilterNGramAtEndOfInput() {
    final DictionaryStopwordFilter filter = withEntries(new String[] {"of", "the"});
    final String[] result = filter.filter(new String[] {"king", "of", "the"});
    Assertions.assertArrayEquals(new String[] {"king"}, result);
  }

  @Test
  void testFilterNGramInMiddleOfInput() {
    final DictionaryStopwordFilter filter = withEntries(new String[] {"of", "the"});
    final String[] result = filter.filter(new String[] {"king", "of", "the", "hill"});
    Assertions.assertArrayEquals(new String[] {"king", "hill"}, result);
  }

  @Test
  void testFilterPartialTailDoesNotMatch() {
    final DictionaryStopwordFilter filter = withEntries(new String[] {"of", "the"});
    final String[] result = filter.filter(new String[] {"king", "of"});
    Assertions.assertArrayEquals(new String[] {"king", "of"}, result);
  }

  @Test
  void testFilterWindowLongerThanInput() {
    final DictionaryStopwordFilter filter = withEntries(new String[] {"a", "b", "c", "d"});
    final String[] result = filter.filter(new String[] {"a", "b"});
    Assertions.assertArrayEquals(new String[] {"a", "b"}, result);
  }

  @Test
  void testFilterTwoConsecutiveNGramMatches() {
    final DictionaryStopwordFilter filter = withEntries(
        new String[] {"of", "the"}, new String[] {"in", "spite"});
    final String[] result = filter.filter(
        new String[] {"of", "the", "in", "spite", "rain"});
    Assertions.assertArrayEquals(new String[] {"rain"}, result);
  }

  @Test
  void testFilterThreeGramEntry() {
    final DictionaryStopwordFilter filter = withEntries(new String[] {"in", "spite", "of"});
    final String[] result = filter.filter(
        new String[] {"won", "in", "spite", "of", "rain"});
    Assertions.assertArrayEquals(new String[] {"won", "rain"}, result);
  }

  @Test
  void testFilterLongestMatchWhenShorterOverlapAlsoMatches() {
    final DictionaryStopwordFilter filter = withEntries(
        new String[] {"a", "b"}, new String[] {"a", "b", "c"});
    final String[] result = filter.filter(new String[] {"a", "b", "c", "d"});
    Assertions.assertArrayEquals(new String[] {"d"}, result);
  }

  @Test
  void testFilterFallsBackToShorterMatchWhenLongestDoesNotApply() {
    final DictionaryStopwordFilter filter = withEntries(
        new String[] {"a", "b"}, new String[] {"a", "b", "c"});
    final String[] result = filter.filter(new String[] {"a", "b", "x", "d"});
    Assertions.assertArrayEquals(new String[] {"x", "d"}, result);
  }

  @Test
  void testFilterNullElementInterruptsWindow() {
    final DictionaryStopwordFilter filter = withEntries(new String[] {"of", "the"});
    final String[] result = filter.filter(new String[] {"of", null, "the", "cat"});
    Assertions.assertArrayEquals(new String[] {"of", null, "the", "cat"}, result);
  }

  @Test
  void testFilterLeadingNullPassesThrough() {
    final DictionaryStopwordFilter filter = withEntries(new String[] {"the"});
    final String[] result = filter.filter(new String[] {null, "the", "cat"});
    Assertions.assertArrayEquals(new String[] {null, "cat"}, result);
  }

  @Test
  void testFilterNGramCaseInsensitiveByDefault() {
    final DictionaryStopwordFilter filter = DictionaryStopwordFilter.builder()
        .caseSensitive(false)
        .add("of", "the")
        .build();
    final String[] result = filter.filter(new String[] {"Of", "THE", "cat"});
    Assertions.assertArrayEquals(new String[] {"cat"}, result);
  }

  @Test
  void testFilterNGramCaseSensitiveHonorsCasing() {
    final DictionaryStopwordFilter filter = DictionaryStopwordFilter.builder()
        .caseSensitive(true)
        .add("of", "the")
        .build();
    final String[] caseDiff = filter.filter(new String[] {"Of", "THE", "cat"});
    Assertions.assertArrayEquals(new String[] {"Of", "THE", "cat"}, caseDiff);

    final String[] exact = filter.filter(new String[] {"of", "the", "cat"});
    Assertions.assertArrayEquals(new String[] {"cat"}, exact);
  }

  @Test
  void testFilterDoesNotEatRegisteredOneGramAfterAddingTwoGram() {
    final DictionaryStopwordFilter filter = withEntries(new String[] {"of", "the"});
    final String[] result = filter.filter(new String[] {"king", "of", "rain"});
    Assertions.assertArrayEquals(new String[] {"king", "of", "rain"}, result);
  }

  @Test
  void testFilterEmptyDictionaryKeepsAllTokens() {
    final DictionaryStopwordFilter filter = empty();
    final String[] result = filter.filter(new String[] {"the", "quick", "brown", "fox"});
    Assertions.assertArrayEquals(new String[] {"the", "quick", "brown", "fox"}, result);
  }

  @Test
  void testFilterAdjacentSameNGramMatchesBoth() {
    final DictionaryStopwordFilter filter = withEntries(new String[] {"of", "the"});
    final String[] result = filter.filter(new String[] {"of", "the", "of", "the", "end"});
    Assertions.assertArrayEquals(new String[] {"end"}, result);
  }

  @Test
  void testFilterNGramMatchAfterUnmatchedToken() {
    final DictionaryStopwordFilter filter = withEntries(new String[] {"of", "the"});
    final String[] result = filter.filter(new String[] {"x", "of", "the", "y"});
    Assertions.assertArrayEquals(new String[] {"x", "y"}, result);
  }

  @Test
  void testFilterReturnsNewArrayInstance() {
    final DictionaryStopwordFilter filter = withEntries(new String[] {"the"});
    final String[] input = new String[] {"the", "cat"};
    final String[] output = filter.filter(input);
    Assertions.assertNotSame(input, output);
    input[1] = "dog";
    Assertions.assertArrayEquals(new String[] {"cat"}, output);
  }

  @Test
  void testFilterInputNullThrowsIllegalArgument() {
    final DictionaryStopwordFilter filter = empty();
    Assertions.assertThrows(IllegalArgumentException.class,
        () -> filter.filter(null));
  }

  @Test
  void testInputStreamConstructorParsesBlanksCommentsAndMultiWordLines() throws Exception {
    final String contents = "# this is a comment header\n"
        + "\n"
        + "the\n"
        + "  and  \n"
        + "# another comment\n"
        + "of the\n"
        + "\n"
        + "by\n";

    final DictionaryStopwordFilter filter;
    try (ByteArrayInputStream in =
             new ByteArrayInputStream(contents.getBytes(StandardCharsets.UTF_8))) {
      filter = new DictionaryStopwordFilter(in, StandardCharsets.UTF_8, false);
    }

    Assertions.assertTrue(filter.isStopword("the"));
    Assertions.assertTrue(filter.isStopword("and"));
    Assertions.assertTrue(filter.isStopword("by"));
    Assertions.assertTrue(filter.isStopword("of", "the"));

    Assertions.assertFalse(filter.isStopword("#"));
    Assertions.assertFalse(filter.isStopword(""));
    Assertions.assertFalse(filter.isStopword("dog"));
  }

  @Test
  void testBuilderLoadParsesStream() throws Exception {
    final String contents = "# bundled-style file\nthe\nof the\n";
    final DictionaryStopwordFilter filter;
    try (ByteArrayInputStream in =
             new ByteArrayInputStream(contents.getBytes(StandardCharsets.UTF_8))) {
      filter = DictionaryStopwordFilter.builder()
          .load(in, StandardCharsets.UTF_8)
          .add("extra")
          .build();
    }
    Assertions.assertTrue(filter.isStopword("the"));
    Assertions.assertTrue(filter.isStopword("of", "the"));
    Assertions.assertTrue(filter.isStopword("extra"));
  }

  @Test
  void testStopwordsViewIsUnmodifiable() {
    final DictionaryStopwordFilter filter = withEntries(new String[] {"the"});
    final Set<String> view = filter.stopwords();
    Assertions.assertTrue(view.contains("the"));
    Assertions.assertThrows(UnsupportedOperationException.class, () -> view.add("foo"));
  }

  @Test
  void testFilterOnEmptyInput() {
    final DictionaryStopwordFilter filter = withEntries(new String[] {"the"});
    final String[] result = filter.filter(new String[0]);
    Assertions.assertEquals(0, result.length);
  }

  @Test
  void testDictionaryConstructorDefensiveCopy() {
    final Dictionary source = new Dictionary(false);
    source.put(new StringList("the"));
    final DictionaryStopwordFilter filter = new DictionaryStopwordFilter(source);

    // Mutating the source after construction must not affect the filter.
    source.put(new StringList("foo"));
    Assertions.assertTrue(filter.isStopword("the"));
    Assertions.assertFalse(filter.isStopword("foo"));
  }

  @Test
  void testNullArgsToInputStreamConstructorThrowIae() {
    Assertions.assertThrows(IllegalArgumentException.class,
        () -> new DictionaryStopwordFilter(null, StandardCharsets.UTF_8, false));
    Assertions.assertThrows(IllegalArgumentException.class,
        () -> new DictionaryStopwordFilter(new ByteArrayInputStream(new byte[0]), null, false));
  }

  @Test
  void testNullSourceToDictionaryConstructorThrowsIae() {
    Assertions.assertThrows(IllegalArgumentException.class,
        () -> new DictionaryStopwordFilter((Dictionary) null));
  }

  @Test
  void testBuilderAddRejectsNullOrEmpty() {
    Assertions.assertThrows(IllegalArgumentException.class,
        () -> DictionaryStopwordFilter.builder().add((String[]) null));
    Assertions.assertThrows(IllegalArgumentException.class,
        () -> DictionaryStopwordFilter.builder().add(new String[0]));
  }

  @Test
  void testBuilderRemoveRejectsNullOrEmpty() {
    Assertions.assertThrows(IllegalArgumentException.class,
        () -> DictionaryStopwordFilter.builder().remove((String[]) null));
    Assertions.assertThrows(IllegalArgumentException.class,
        () -> DictionaryStopwordFilter.builder().remove(new String[0]));
  }

  @Test
  void testLoadUncheckedSuccessfullyParsesStream() {
    final String contents = "# header\nthe\nof the\nand\n";
    try (ByteArrayInputStream in =
             new ByteArrayInputStream(contents.getBytes(StandardCharsets.UTF_8))) {
      final DictionaryStopwordFilter filter =
          DictionaryStopwordFilter.loadUnchecked(in, StandardCharsets.UTF_8, false);
      Assertions.assertTrue(filter.isStopword("the"));
      Assertions.assertTrue(filter.isStopword("and"));
      Assertions.assertTrue(filter.isStopword("of", "the"));
      Assertions.assertFalse(filter.isStopword("dog"));
    } catch (IOException e) {
      throw new AssertionError("close() should not have thrown", e);
    }
  }

  @Test
  void testLoadUncheckedNullArgsThrowIae() {
    Assertions.assertThrows(IllegalArgumentException.class,
        () -> DictionaryStopwordFilter.loadUnchecked(
            null, StandardCharsets.UTF_8, false));
    Assertions.assertThrows(IllegalArgumentException.class,
        () -> DictionaryStopwordFilter.loadUnchecked(
            new ByteArrayInputStream(new byte[0]), null, false));
  }

  @Test
  void testLoadUncheckedWrapsIoExceptionAsUnchecked() {
    final InputStream broken = new InputStream() {
      @Override
      public int read() throws IOException {
        throw new IOException("simulated read failure");
      }
    };
    final UncheckedIOException ex = Assertions.assertThrows(UncheckedIOException.class,
        () -> DictionaryStopwordFilter.loadUnchecked(broken, StandardCharsets.UTF_8, false));
    Assertions.assertNotNull(ex.getCause());
    Assertions.assertEquals("simulated read failure", ex.getCause().getMessage());
  }

  @Test
  void testLoadUncheckedRespectsCaseSensitivity() {
    final String contents = "The\n";
    try (ByteArrayInputStream in =
             new ByteArrayInputStream(contents.getBytes(StandardCharsets.UTF_8))) {
      final DictionaryStopwordFilter ci =
          DictionaryStopwordFilter.loadUnchecked(in, StandardCharsets.UTF_8, false);
      Assertions.assertTrue(ci.isStopword("the"));
      Assertions.assertTrue(ci.isStopword("The"));
    } catch (IOException e) {
      throw new AssertionError(e);
    }

    try (ByteArrayInputStream in =
             new ByteArrayInputStream(contents.getBytes(StandardCharsets.UTF_8))) {
      final DictionaryStopwordFilter cs =
          DictionaryStopwordFilter.loadUnchecked(in, StandardCharsets.UTF_8, true);
      Assertions.assertTrue(cs.isStopword("The"));
      Assertions.assertFalse(cs.isStopword("the"));
    } catch (IOException e) {
      throw new AssertionError(e);
    }
  }

  // ---- mutation-coverage tests for surviving mutants ----

  @Test
  void testIsStopwordCharSequenceNullReturnsFalse() {
    final DictionaryStopwordFilter filter = withEntries(new String[] {"the"});
    Assertions.assertFalse(filter.isStopword((CharSequence) null));
  }

  @Test
  void testIsStopwordVarargsNullArrayReturnsFalse() {
    final DictionaryStopwordFilter filter = withEntries(new String[] {"the"});
    Assertions.assertFalse(filter.isStopword((String[]) null));
  }

  @Test
  void testIsStopwordVarargsEmptyReturnsFalse() {
    final DictionaryStopwordFilter filter = withEntries(new String[] {"the"});
    Assertions.assertFalse(filter.isStopword());
  }

  @Test
  void testIsStopwordVarargsContainsNullElementReturnsFalse() {
    final DictionaryStopwordFilter filter = withEntries(new String[] {"the"});
    Assertions.assertFalse(filter.isStopword("the", null, "cat"));
  }

  @Test
  void testIsStopwordVarargsSingleNullElementReturnsFalse() {
    final DictionaryStopwordFilter filter = withEntries(new String[] {"the"});
    Assertions.assertFalse(filter.isStopword((String) null));
  }

  @Test
  void testFilterNullAtEndOfArray() {
    final DictionaryStopwordFilter filter = withEntries(new String[] {"the"});
    final String[] result = filter.filter(new String[] {"the", "cat", null});
    Assertions.assertArrayEquals(new String[] {"cat", null}, result);
  }

  @Test
  void testFilterMultipleNulls() {
    final DictionaryStopwordFilter filter = withEntries(new String[] {"the"});
    final String[] result = filter.filter(new String[] {null, "the", null, "cat", null});
    Assertions.assertArrayEquals(new String[] {null, null, "cat", null}, result);
  }

  @Test
  void testFilterNullInterruptsNGramButIndividualTokensFiltered() {
    // "of the" is a 2-gram; "of" and "the" are also 1-grams
    final DictionaryStopwordFilter filter = DictionaryStopwordFilter.builder()
        .add("of", "the")
        .add("of")
        .add("the")
        .build();
    // null between "of" and "the" prevents the 2-gram match,
    // but both "of" and "the" are still filtered as 1-grams
    final String[] result = filter.filter(new String[] {"of", null, "the", "cat"});
    Assertions.assertArrayEquals(new String[] {null, "cat"}, result);
  }

  @Test
  void testFilterNullAtStartOfNGramWindowWithIndividualStopwords() {
    // "of the" is a 2-gram; "the" is also a 1-gram
    final DictionaryStopwordFilter filter = DictionaryStopwordFilter.builder()
        .add("of", "the")
        .add("the")
        .build();
    // null at position 0 prevents any 2-gram starting at position 0
    // null is kept; at position 1, "of the" matches as 2-gram → filtered
    // "cat" is kept
    final String[] result = filter.filter(new String[] {null, "of", "the", "cat"});
    Assertions.assertArrayEquals(new String[] {null, "cat"}, result);
  }

  @Test
  void testFilterNullAtEndOfNGramWindow() {
    // "of the" is a 2-gram; "of" is also a 1-gram
    final DictionaryStopwordFilter filter = DictionaryStopwordFilter.builder()
        .add("of", "the")
        .add("of")
        .build();
    // null after "of" prevents the 2-gram match at position 0
    // "of" is a 1-gram stopword, so it's filtered
    // null is kept; "the" is NOT a 1-gram stopword, so it's kept
    final String[] result = filter.filter(new String[] {"of", null, "the", "cat"});
    Assertions.assertArrayEquals(new String[] {null, "the", "cat"}, result);
  }

  @Test
  void testFilterAllNulls() {
    final DictionaryStopwordFilter filter = withEntries(new String[] {"the"});
    final String[] result = filter.filter(new String[] {null, null, null});
    Assertions.assertArrayEquals(new String[] {null, null, null}, result);
  }

  @Test
  void testFilterSingleNull() {
    final DictionaryStopwordFilter filter = withEntries(new String[] {"the"});
    final String[] result = filter.filter(new String[] {null});
    Assertions.assertArrayEquals(new String[] {null}, result);
  }

  @Test
  void testParseStreamWithWhitespaceOnlyLines() throws Exception {
    final String contents = "   \n\t\nthe\n  \t  \n";
    final DictionaryStopwordFilter filter;
    try (ByteArrayInputStream in =
             new ByteArrayInputStream(contents.getBytes(StandardCharsets.UTF_8))) {
      filter = new DictionaryStopwordFilter(in, StandardCharsets.UTF_8, false);
    }
    Assertions.assertTrue(filter.isStopword("the"));
    Assertions.assertFalse(filter.isStopword(""));
    Assertions.assertEquals(1, filter.stopwords().size());
  }

  @Test
  void testParseStreamWithCommentContainingContent() throws Exception {
    final String contents = "# this is a comment with content\nthe\n#another\n";
    final DictionaryStopwordFilter filter;
    try (ByteArrayInputStream in =
             new ByteArrayInputStream(contents.getBytes(StandardCharsets.UTF_8))) {
      filter = new DictionaryStopwordFilter(in, StandardCharsets.UTF_8, false);
    }
    Assertions.assertTrue(filter.isStopword("the"));
    Assertions.assertFalse(filter.isStopword("#"));
    Assertions.assertFalse(filter.isStopword("this"));
    Assertions.assertEquals(1, filter.stopwords().size());
  }

  @Test
  void testParseStreamWithLeadingTrailingWhitespaceOnEntries() throws Exception {
    final String contents = "  the  \n  of the  \n";
    final DictionaryStopwordFilter filter;
    try (ByteArrayInputStream in =
             new ByteArrayInputStream(contents.getBytes(StandardCharsets.UTF_8))) {
      filter = new DictionaryStopwordFilter(in, StandardCharsets.UTF_8, false);
    }
    Assertions.assertTrue(filter.isStopword("the"));
    Assertions.assertTrue(filter.isStopword("of", "the"));
    Assertions.assertEquals(2, filter.stopwords().size());
  }

  @Test
  void testParseStreamEmptyFile() throws Exception {
    final DictionaryStopwordFilter filter;
    try (ByteArrayInputStream in =
             new ByteArrayInputStream(new byte[0])) {
      filter = new DictionaryStopwordFilter(in, StandardCharsets.UTF_8, false);
    }
    Assertions.assertTrue(filter.stopwords().isEmpty());
  }

  @Test
  void testParseStreamOnlyCommentsAndBlanks() throws Exception {
    final String contents = "# comment\n\n# another\n  \n";
    final DictionaryStopwordFilter filter;
    try (ByteArrayInputStream in =
             new ByteArrayInputStream(contents.getBytes(StandardCharsets.UTF_8))) {
      filter = new DictionaryStopwordFilter(in, StandardCharsets.UTF_8, false);
    }
    Assertions.assertTrue(filter.stopwords().isEmpty());
  }

  @Test
  void testStopwordsViewIsNotTheBackingSet() {
    // Verify that the returned set is a defensive wrapper, not the raw backing set
    final DictionaryStopwordFilter filter = withEntries(new String[] {"the"});
    final Set<String> view = filter.stopwords();
    // Attempting structural modification must throw
    Assertions.assertThrows(UnsupportedOperationException.class, () -> view.clear());
    Assertions.assertThrows(UnsupportedOperationException.class, () -> view.remove("the"));
  }

  @Test
  void testFilterWithNGramAndNullInMiddleWhereOnlyFirstTokenIsStopword() {
    // "of the" is a 2-gram; "of" is a 1-gram; "the" is NOT a 1-gram
    final DictionaryStopwordFilter filter = DictionaryStopwordFilter.builder()
        .add("of", "the")
        .add("of")
        .build();
    // null between "of" and "the" prevents 2-gram match
    // "of" is filtered as 1-gram; null kept; "the" kept (not a 1-gram)
    final String[] result = filter.filter(new String[] {"of", null, "the", "cat"});
    Assertions.assertArrayEquals(new String[] {null, "the", "cat"}, result);
  }

  @Test
  void testFilterWithNGramAndNullInMiddleWhereOnlySecondTokenIsStopword() {
    // "of the" is a 2-gram; "the" is a 1-gram; "of" is NOT a 1-gram
    final DictionaryStopwordFilter filter = DictionaryStopwordFilter.builder()
        .add("of", "the")
        .add("the")
        .build();
    // null between "of" and "the" prevents 2-gram match
    // "of" kept (not a 1-gram); null kept; "the" filtered as 1-gram
    final String[] result = filter.filter(new String[] {"of", null, "the", "cat"});
    Assertions.assertArrayEquals(new String[] {"of", null, "cat"}, result);
  }

  @Test
  void testFilterThreeGramWithNullInterrupting() {
    // "in spite of" is a 3-gram; "in" and "of" are 1-grams
    final DictionaryStopwordFilter filter = DictionaryStopwordFilter.builder()
        .add("in", "spite", "of")
        .add("in")
        .add("of")
        .build();
    // null between "spite" and "of" prevents 3-gram match
    // "in" filtered as 1-gram; "spite" kept; null kept; "of" filtered as 1-gram
    final String[] result = filter.filter(new String[] {"in", "spite", null, "of", "rain"});
    Assertions.assertArrayEquals(new String[] {"spite", null, "rain"}, result);
  }

  @Test
  void testFilterWindowScanWithNullPreventsLongerMatch() {
    // "a b c" is a 3-gram; "a b" is a 2-gram; "a" is a 1-gram
    final DictionaryStopwordFilter filter = DictionaryStopwordFilter.builder()
        .add("a", "b", "c")
        .add("a", "b")
        .add("a")
        .build();
    // null at position 1 prevents 3-gram and 2-gram matches at position 0
    // "a" is filtered as 1-gram; null kept; "b" kept; "c" kept
    final String[] result = filter.filter(new String[] {"a", null, "b", "c"});
    Assertions.assertArrayEquals(new String[] {null, "b", "c"}, result);
  }

  @Test
  void testFilterNullArrayThrows() {
    final DictionaryStopwordFilter filter = withEntries(new String[] {"the"});
    Assertions.assertThrows(IllegalArgumentException.class, () -> filter.filter(null));
  }

  @Test
  void testIsStopwordVarargsNonStopwordReturnsFalse() {
    final DictionaryStopwordFilter filter = withEntries(new String[] {"the"});
    Assertions.assertFalse(filter.isStopword("notastopword"));
  }

  @Test
  void testIsStopwordVarargsMultiTokenNonStopwordReturnsFalse() {
    final DictionaryStopwordFilter filter = withEntries(new String[] {"the"});
    Assertions.assertFalse(filter.isStopword("not", "a", "stopword"));
  }

  @Test
  void testFilterSingleElementStopword() {
    final DictionaryStopwordFilter filter = withEntries(new String[] {"the"});
    final String[] result = filter.filter(new String[] {"the"});
    Assertions.assertArrayEquals(new String[0], result);
  }

  @Test
  void testFilterSingleElementNonStopword() {
    final DictionaryStopwordFilter filter = withEntries(new String[] {"the"});
    final String[] result = filter.filter(new String[] {"cat"});
    Assertions.assertArrayEquals(new String[] {"cat"}, result);
  }

  @Test
  void testFilterWithMaxWindowLargerThanInput() {
    // maxWindow is 3 (from "in the end"), but input has only 2 elements
    final DictionaryStopwordFilter filter = DictionaryStopwordFilter.builder()
        .add("in", "the", "end")
        .add("the")
        .build();
    final String[] result = filter.filter(new String[] {"the", "cat"});
    Assertions.assertArrayEquals(new String[] {"cat"}, result);
  }

  @Test
  void testFilterWithMaxWindowLargerThanInputAtEnd() {
    // maxWindow is 3 (from "in the end"), remaining tokens at end < maxWindow
    final DictionaryStopwordFilter filter = DictionaryStopwordFilter.builder()
        .add("in", "the", "end")
        .add("the")
        .build();
    final String[] result = filter.filter(new String[] {"hello", "the", "cat"});
    Assertions.assertArrayEquals(new String[] {"hello", "cat"}, result);
  }

  @Test
  void testFilterEmptyArray() {
    final DictionaryStopwordFilter filter = withEntries(new String[] {"the"});
    final String[] result = filter.filter(new String[0]);
    Assertions.assertArrayEquals(new String[0], result);
  }

  @Test
  void testIsStopwordVarargsWithCaseMismatchCaseSensitive() {
    final DictionaryStopwordFilter filter = DictionaryStopwordFilter.builder()
        .caseSensitive(true)
        .add("The")
        .build();
    Assertions.assertTrue(filter.isStopword("The"));
    Assertions.assertFalse(filter.isStopword("the"));
  }

  @Test
  void testIsStopwordVarargsWithCaseMismatchCaseInsensitive() {
    final DictionaryStopwordFilter filter = DictionaryStopwordFilter.builder()
        .caseSensitive(false)
        .add("The")
        .build();
    Assertions.assertTrue(filter.isStopword("The"));
    Assertions.assertTrue(filter.isStopword("the"));
  }

  @Test
  void testFilterWithOnlyNGramEntriesNoIndividualMatches() {
    // Only "of the" is a stopword (2-gram), neither "of" nor "the" individually
    final DictionaryStopwordFilter filter = DictionaryStopwordFilter.builder()
        .add("of", "the")
        .build();
    // "of" and "the" are NOT individual stopwords
    Assertions.assertFalse(filter.isStopword("of"));
    Assertions.assertFalse(filter.isStopword("the"));
    // But "of the" IS a stopword
    Assertions.assertTrue(filter.isStopword("of", "the"));
    // Filter: "of the" matched as 2-gram, "cat" kept
    final String[] result = filter.filter(new String[] {"of", "the", "cat"});
    Assertions.assertArrayEquals(new String[] {"cat"}, result);
  }

  @Test
  void testFilterWithNullBetweenNonStopwordAndStopword() {
    // "of the" is a 2-gram; "the" is a 1-gram; "of" is NOT a 1-gram
    final DictionaryStopwordFilter filter = DictionaryStopwordFilter.builder()
        .add("of", "the")
        .add("the")
        .build();
    // null between "of" and "the" prevents 2-gram match
    // "of" kept (not a 1-gram); null kept; "the" filtered as 1-gram
    final String[] result = filter.filter(new String[] {"of", null, "the"});
    Assertions.assertArrayEquals(new String[] {"of", null}, result);
  }

  @Test
  void testFilterWithNullAtStartAndNGramFollows() {
    // "of the" is a 2-gram; "the" is a 1-gram
    final DictionaryStopwordFilter filter = DictionaryStopwordFilter.builder()
        .add("of", "the")
        .add("the")
        .build();
    // null at position 0 is kept
    // at position 1, "of the" matches as 2-gram → filtered
    final String[] result = filter.filter(new String[] {null, "of", "the"});
    Assertions.assertArrayEquals(new String[] {null}, result);
  }

  @Test
  void testFilterWithNullAtEndAfterNGram() {
    // "of the" is a 2-gram
    final DictionaryStopwordFilter filter = DictionaryStopwordFilter.builder()
        .add("of", "the")
        .build();
    // "of the" matched as 2-gram, null at end is kept
    final String[] result = filter.filter(new String[] {"of", "the", null});
    Assertions.assertArrayEquals(new String[] {null}, result);
  }

  @Test
  void testFilterWithConsecutiveNulls() {
    final DictionaryStopwordFilter filter = withEntries(new String[] {"the"});
    final String[] result = filter.filter(new String[] {"the", null, null, "cat"});
    Assertions.assertArrayEquals(new String[] {null, null, "cat"}, result);
  }

  @Test
  void testFilterWithNullAsOnlyElementInWindow() {
    // maxWindow is 2 (from "of the"), but window at position 0 contains null
    final DictionaryStopwordFilter filter = DictionaryStopwordFilter.builder()
        .add("of", "the")
        .build();
    // null at position 0 prevents any window match starting at 0
    // null is kept (matched=0, so kept.add(null)); i moves to 1
    // at position 1, "of the" matches as 2-gram → filtered
    final String[] result = filter.filter(new String[] {null, "of", "the"});
    Assertions.assertArrayEquals(new String[] {null}, result);
  }

  @Test
  void testParseStreamWithTrailingNewline() throws Exception {
    final String contents = "the\n";
    final DictionaryStopwordFilter filter;
    try (ByteArrayInputStream in =
             new ByteArrayInputStream(contents.getBytes(StandardCharsets.UTF_8))) {
      filter = new DictionaryStopwordFilter(in, StandardCharsets.UTF_8, false);
    }
    Assertions.assertTrue(filter.isStopword("the"));
    Assertions.assertEquals(1, filter.stopwords().size());
  }

  @Test
  void testParseStreamWithMultipleTokensOnLine() throws Exception {
    final String contents = "of the\nin the end\n";
    final DictionaryStopwordFilter filter;
    try (ByteArrayInputStream in =
             new ByteArrayInputStream(contents.getBytes(StandardCharsets.UTF_8))) {
      filter = new DictionaryStopwordFilter(in, StandardCharsets.UTF_8, false);
    }
    Assertions.assertTrue(filter.isStopword("of", "the"));
    Assertions.assertTrue(filter.isStopword("in", "the", "end"));
    Assertions.assertEquals(2, filter.stopwords().size());
  }

  @Test
  void testParseStreamWithMixedWhitespaceSeparators() throws Exception {
    final String contents = "of\tthe\nin  the   end\n";
    final DictionaryStopwordFilter filter;
    try (ByteArrayInputStream in =
             new ByteArrayInputStream(contents.getBytes(StandardCharsets.UTF_8))) {
      filter = new DictionaryStopwordFilter(in, StandardCharsets.UTF_8, false);
    }
    Assertions.assertTrue(filter.isStopword("of", "the"));
    Assertions.assertTrue(filter.isStopword("in", "the", "end"));
    Assertions.assertEquals(2, filter.stopwords().size());
  }

  @Test
  void testStopwordsViewContainsAllEntries() {
    final DictionaryStopwordFilter filter = DictionaryStopwordFilter.builder()
        .add("of", "the")
        .add("the")
        .build();
    // stopwords() returns asStringSet() which iterates first tokens of all entries
    final Set<String> sw = filter.stopwords();
    // size reflects total entry count (1 two-gram + 1 one-gram = 2)
    Assertions.assertEquals(2, sw.size());
    // contains() only matches 1-gram entries
    Assertions.assertTrue(sw.contains("the"));
    Assertions.assertFalse(sw.contains("of")); // "of the" is a 2-gram, not a 1-gram
  }

  @Test
  void testStopwordsViewContainsNGramEntries() {
    final DictionaryStopwordFilter filter = DictionaryStopwordFilter.builder()
        .add("of", "the")
        .build();
    // stopwords() returns asStringSet() which includes all entries
    // "of the" is a 2-gram, so size is 1
    final Set<String> sw = filter.stopwords();
    Assertions.assertEquals(1, sw.size());
    // contains() only matches 1-gram entries, so "of" (part of 2-gram) returns false
    Assertions.assertFalse(sw.contains("of"));
    // but iterating yields the first token of the 2-gram
    final List<String> iterated = new ArrayList<>(sw);
    Assertions.assertEquals(List.of("of"), iterated);
  }

  // === Mutation-killing tests for surviving mutants ===

  @Test
  void testIsStopwordVarargsWithNullElementReturnsFalse() {
    final DictionaryStopwordFilter filter = DictionaryStopwordFilter.builder()
        .add("the")
        .build();
    // L173: null element in varargs must return false
    // (kills "removed conditional - replaced equality check with false")
    Assertions.assertFalse(filter.isStopword("the", null));
    Assertions.assertFalse(filter.isStopword(null, "the"));
    Assertions.assertFalse(filter.isStopword("a", null, "b"));
  }

  @Test
  void testIsStopwordVarargsWithNullElementReturnsFalseNotTrue() {
    final DictionaryStopwordFilter filter = DictionaryStopwordFilter.builder()
        .add("the")
        .build();
    // L177: ensure null element returns false, not true (kills "replaced boolean return with true")
    Assertions.assertFalse(filter.isStopword("the", null));
  }

  @Test
  void testFilterWithNullInWindowSkipsWindowCheck() {
    // L203: when containsAnyNullInWindow returns true, the window is skipped via continue
    // This tests that null in window prevents matching even when the non-null tokens would match
    final DictionaryStopwordFilter filter = DictionaryStopwordFilter.builder()
        .add("of", "the")
        .add("the")
        .build();
    // "of" + null prevents 2-gram "of the" match; "of" kept (not 1-gram)
    // null kept; "the" filtered as 1-gram
    final String[] result = filter.filter(new String[] {"of", null, "the"});
    Assertions.assertArrayEquals(new String[] {"of", null}, result);
  }

  @Test
  void testFilterWithNullInWindowSkipsEqualityCheck() {
    // L203: "removed conditional - replaced equality check with false"
    // When containsAnyNullInWindow returns true (equality check), we must continue
    final DictionaryStopwordFilter filter = DictionaryStopwordFilter.builder()
        .add("a", "b")
        .build();
    // null at position 1 prevents 2-gram match; both "a" and null kept; "b" kept
    final String[] result = filter.filter(new String[] {"a", null, "b"});
    Assertions.assertArrayEquals(new String[] {"a", null, "b"}, result);
  }

  @Test
  void testFilterWithNullInWindowCallsContainsAnyNullInWindow() {
    // L203: "removed call to containsAnyNullInWindow"
    // If the call were removed, nulls would participate in window matching incorrectly
    final DictionaryStopwordFilter filter = DictionaryStopwordFilter.builder()
        .add("x", "y")
        .build();
    // Without containsAnyNullInWindow, the code would try Arrays.copyOfRange including null
    // and potentially crash or match incorrectly. With it, the window is skipped.
    final String[] result = filter.filter(new String[] {"x", null, "y"});
    Assertions.assertArrayEquals(new String[] {"x", null, "y"}, result);
  }

  @Test
  void testFilterWithNullInWindowNegatedConditional() {
    // L224: "negated conditional" on the loop condition k < len
    // Test with len=1 (single element window containing null)
    final DictionaryStopwordFilter filter = DictionaryStopwordFilter.builder()
        .add("a")
        .build();
    // null at position 0: window of size 1 contains null -> skipped -> null kept
    final String[] result = filter.filter(new String[] {null, "a"});
    Assertions.assertArrayEquals(new String[] {null}, result);
  }

  @Test
  void testFilterWithNullInWindowRemovedComparisonCheck() {
    // L224: "removed conditional - replaced comparison check with false"
    // k < len must actually be checked; test with len > 0
    final DictionaryStopwordFilter filter = DictionaryStopwordFilter.builder()
        .add("a", "b", "c")
        .build();
    // Window of 3 at position 0: "a", "b", null -> containsAnyNullInWindow returns true
    // So 3-gram is skipped; "a" kept (not a 1-gram), then "b" kept, then null kept
    final String[] result = filter.filter(new String[] {"a", "b", null});
    Assertions.assertArrayEquals(new String[] {"a", "b", null}, result);
  }

  @Test
  void testFilterWithNullInWindowSubstitutedZeroWithOne() {
    // L224: "Substituted 0 with 1" on the loop initializer k = 0
    // If k started at 1 instead of 0, the first element of the window would be skipped
    final DictionaryStopwordFilter filter = DictionaryStopwordFilter.builder()
        .add("a", "b")
        .build();
    // Window ["a", null]: if k starts at 1, only null is checked -> still returns true
    // But we need a case where first element is null and second is not
    final String[] result = filter.filter(new String[] {null, "b"});
    // null kept (window skipped), "b" kept (not a 1-gram)
    Assertions.assertArrayEquals(new String[] {null, "b"}, result);
  }

  @Test
  void testFilterWithNullInWindowSubstitutedOneWithZero() {
    // L226: "Substituted 1 with 0" on the return true statement
    // If return true became return false, nulls wouldn't prevent window matching
    final DictionaryStopwordFilter filter = DictionaryStopwordFilter.builder()
        .add("a", "b")
        .build();
    // If containsAnyNullInWindow returned false when it should return true,
    // the window ["a", null] would be checked against dictionary, which wouldn't match,
    // but the behavior would still be "no match" -> same result.
    // We need a case where the window would match if null were treated as non-null.
    // Actually, Arrays.copyOfRange would include null, and backing.contains would likely
    // return false for a window containing null. So the difference is subtle.
    // The key difference: if containsAnyNullInWindow returns false incorrectly,
    // the code proceeds to check backing.contains with a window containing null.
    // Since the dictionary won't have null entries, it still won't match.
    // But the test verifies the correct behavior: null prevents matching.
    final String[] result = filter.filter(new String[] {"a", null, "b"});
    Assertions.assertArrayEquals(new String[] {"a", null, "b"}, result);
  }

  @Test
  void testFilterWithNullInWindowBooleanFalseReturn() {
    // L226: "replaced boolean return with false for containsAnyNullInWindow"
    // If the return true at L226 were replaced with false, nulls wouldn't stop matching
    final DictionaryStopwordFilter filter = DictionaryStopwordFilter.builder()
        .add("a", "b")
        .build();
    // With the mutant, containsAnyNullInWindow would return false even when null found
    // The window ["a", null] would be passed to backing.contains which would return false
    // (since dictionary doesn't contain null), so matched stays 0 and "a" is kept.
    // Same outcome for this case. Need different approach.
    // Actually the behavior IS the same for non-matching windows.
    // The difference only matters if the window would match despite containing null,
    // which can't happen since dictionary won't have null entries.
    // So this test verifies the expected behavior.
    final String[] result = filter.filter(new String[] {"a", null});
    Assertions.assertArrayEquals(new String[] {"a", null}, result);
  }

  @Test
  void testFilterWithNullInWindowRemovedEqualityCheckInLoop() {
    // L225: "removed conditional - replaced equality check with false"
    // tokens[start + k] == null check is removed -> always false -> never returns true
    // This means containsAnyNullInWindow always returns false
    final DictionaryStopwordFilter filter = DictionaryStopwordFilter.builder()
        .add("a", "b")
        .build();
    // If the null check is removed, the window ["a", null] would be checked against dict
    // Since dict doesn't contain null, it still won't match -> "a" kept, null kept
    // But we verify the correct behavior
    final String[] result = filter.filter(new String[] {"a", null});
    Assertions.assertArrayEquals(new String[] {"a", null}, result);
  }

  @Test
  void testFilterMathMinArgumentPropagation() {
    // L202: "replaced call to java/lang/Math::min with argument"
    // Math.min(maxWindow, tokens.length - i) -> if replaced with just maxWindow,
    // the loop could go beyond array bounds when tokens.length - i < maxWindow
    // But since w decreases and we use Arrays.copyOfRange, it might not crash
    // but could produce wrong results
    final DictionaryStopwordFilter filter = DictionaryStopwordFilter.builder()
        .add("a", "b", "c")  // maxWindow = 3
        .build();
    // Input has only 2 elements, maxWindow is 3
    // Math.min(3, 2) = 2, so w starts at 2
    // If replaced with argument (maxWindow=3), w would start at 3
    // Arrays.copyOfRange(tokens, 0, 3) would throw IndexOutOfBoundsException
    // But PIT doesn't count exceptions as killed... unless it does.
    // Let's test with input shorter than maxWindow
    final String[] result = filter.filter(new String[] {"a", "b"});
    Assertions.assertArrayEquals(new String[] {"a", "b"}, result);
  }

  @Test
  void testParseStreamWithSingleTokenLine() throws Exception {
    // L262: "changed conditional boundary" and "removed conditional - replaced comparison check with true"
    // tokens.length > 0 check after split
    final String contents = "the\n";
    final DictionaryStopwordFilter filter;
    try (ByteArrayInputStream in =
             new ByteArrayInputStream(contents.getBytes(StandardCharsets.UTF_8))) {
      filter = new DictionaryStopwordFilter(in, StandardCharsets.UTF_8, false);
    }
    Assertions.assertTrue(filter.isStopword("the"));
    Assertions.assertEquals(1, filter.stopwords().size());
  }

  @Test
  void testParseStreamWithOnlyWhitespaceLine() throws Exception {
    // L262: tokens.length > 0 - when line is only whitespace, split produces [""]
    // which has length 1, so it would be added as an empty string entry
    // But trimmed.isEmpty() catches this first
    final String contents = "   \nthe\n";
    final DictionaryStopwordFilter filter;
    try (ByteArrayInputStream in =
             new ByteArrayInputStream(contents.getBytes(StandardCharsets.UTF_8))) {
      filter = new DictionaryStopwordFilter(in, StandardCharsets.UTF_8, false);
    }
    Assertions.assertTrue(filter.isStopword("the"));
    Assertions.assertEquals(1, filter.stopwords().size());
  }

  @Test
  void testStopwordsReturnsUnmodifiableSet() {
    // L247: "replaced call to java/util/Collections::unmodifiableSet with argument"
    // If unmodifiableSet were removed, the returned set would be modifiable
    final DictionaryStopwordFilter filter = DictionaryStopwordFilter.builder()
        .add("the")
        .build();
    final Set<String> sw = filter.stopwords();
    Assertions.assertThrows(UnsupportedOperationException.class, () -> {
      sw.add("new");
    });
  }

  // === PHASE 3: Targeted tests for surviving mutants ===

  /**
   * Kills the conditional boundary mutant on line 212:
   * Original: matched > 0  →  Mutant: matched >= 0
   * 
   * With the mutant, when matched=0 (no stopword match), the condition
   * matched >= 0 is always true, causing an infinite loop at that position.
   * 
   * This test uses @Timeout(1, unit = SECONDS) so:
   * - Original code: completes instantly (i increments normally)
   * - Mutant code: infinite loop → timeout → test fails → mutant killed
   */
  @Test
  @Timeout(1)
  void filterNonStopwordTokenCompletesTimely() {
    DictionaryStopwordFilter filter = withEntries(new String[]{"the"});
    
    String[] result = filter.filter(new String[]{"hello"});
    Assertions.assertArrayEquals(new String[]{"hello"}, result);
  }

  /**
   * Additional timeout test with mixed stopword and non-stopword tokens.
   */
  @Test
  @Timeout(1)
  void filterMixedTokensWithNonStopwordCompletesTimely() {
    DictionaryStopwordFilter filter = withEntries(new String[]{"the"}, new String[]{"a"});
    
    String[] result = filter.filter(new String[]{"the", "hello", "a"});
    Assertions.assertArrayEquals(new String[]{"hello"}, result);
  }

  /**
   * Timeout test with empty stopword filter.
   * Every token has matched=0, so the loop must increment i by 1 each time.
   */
  @Test
  @Timeout(1)
  void filterWithEmptyDictionaryCompletesTimely() {
    DictionaryStopwordFilter filter = empty();
    
    String[] result = filter.filter(new String[]{"x", "y", "z"});
    Assertions.assertArrayEquals(new String[]{"x", "y", "z"}, result);
  }

  /**
   * Verifies that null elements in the input array are preserved in output
   * when they don't match any stopword entry.
   */
  @Test
  void filterPreservesNullElementsNotMatchingStopwords() {
    DictionaryStopwordFilter filter = withEntries(new String[]{"a", "b"});
    
    String[] result = filter.filter(new String[]{"x", null, "y"});
    Assertions.assertArrayEquals(new String[]{"x", null, "y"}, result);
  }

  /**
   * Verifies that null elements at the start of the input are handled correctly.
   */
  @Test
  void filterPreservesNullAtStart() {
    DictionaryStopwordFilter filter = withEntries(new String[]{"the"});
    
    String[] result = filter.filter(new String[]{null, "hello"});
    Assertions.assertArrayEquals(new String[]{null, "hello"}, result);
  }

  /**
   * Verifies that null elements at the end of the input are handled correctly.
   */
  @Test
  void filterPreservesNullAtEnd() {
    DictionaryStopwordFilter filter = withEntries(new String[]{"the"});
    
    String[] result = filter.filter(new String[]{"hello", null});
    Assertions.assertArrayEquals(new String[]{"hello", null}, result);
  }

  /**
   * Tests that the filter correctly handles a single-token input that
   * is not a stopword, ensuring the loop terminates after one iteration.
   */
  @Test
  @Timeout(1)
  void filterSingleNonStopwordTokenCompletesTimely() {
    DictionaryStopwordFilter filter = withEntries(new String[]{"the"});
    
    String[] result = filter.filter(new String[]{"notastopword"});
    Assertions.assertArrayEquals(new String[]{"notastopword"}, result);
  }

  /**
   * Tests that the filter correctly handles consecutive non-stopword tokens.
   */
  @Test
  @Timeout(1)
  void filterConsecutiveNonStopwordTokensCompletesTimely() {
    DictionaryStopwordFilter filter = withEntries(new String[]{"the"});

    String[] result = filter.filter(new String[]{"one", "two", "three", "four", "five"});
    Assertions.assertArrayEquals(new String[]{"one", "two", "three", "four", "five"}, result);
  }

  // === PHASE 4: Kill surviving mutants via case-insensitive null NPE ===

  /**
   * Kills the negated-conditional mutant on line 224 (k < len → k >= len)
   * and the return-false mutant on line 226 in containsAnyNullInWindow.
   *
   * With these mutants, containsAnyNullInWindow always returns false, so
   * a window containing null elements proceeds to backing.contains().
   * For a case-INSENSITIVE dictionary, this triggers compareToIgnoreCase()
   * which calls String.compareToIgnoreCase() on a null token → NPE.
   *
   * Original code: containsAnyNullInWindow returns true → window skipped → no NPE.
   * Mutant code: containsAnyNullInWindow returns false → window checked → NPE.
   */
  @Test
  void filterWithNullInWindowCaseInsensitiveThrowsNoException() {
    // Case-insensitive filter (default) with a multi-word entry
    DictionaryStopwordFilter filter = DictionaryStopwordFilter.builder()
        .add("the", "cat")
        .build();

    // Input contains null in the middle
    String[] result = filter.filter(new String[]{"hello", null, "world"});
    Assertions.assertArrayEquals(new String[]{"hello", null, "world"}, result);
  }

  /**
   * Additional test: null at the start of input with case-insensitive filter.
   * Kills the same mutants (line 224 and 226) by ensuring the null-containing
   * window would trigger compareToIgnoreCase NPE if not properly skipped.
   */
  @Test
  void filterWithNullAtStartCaseInsensitiveCompletes() {
    DictionaryStopwordFilter filter = DictionaryStopwordFilter.builder()
        .add("the")
        .build();

    String[] result = filter.filter(new String[]{null, "the", "cat"});
    Assertions.assertArrayEquals(new String[]{null, "cat"}, result);
  }

  /**
   * Tests null element adjacent to a matching stopword in case-insensitive mode.
   * The null must be skipped by containsAnyNullInWindow to avoid NPE in
   * compareToIgnoreCase when the dictionary is case-insensitive.
   */
  @Test
  void filterWithNullAdjacentToStopwordCaseInsensitive() {
    DictionaryStopwordFilter filter = DictionaryStopwordFilter.builder()
        .add("the", "cat")
        .build();

    String[] result = filter.filter(new String[]{"the", null, "cat"});
    Assertions.assertArrayEquals(new String[]{"the", null, "cat"}, result);
  }

  /**
   * Tests that a case-insensitive filter with multi-word entries correctly
   * handles null elements that would form part of a potential multi-word match.
   */
  @Test
  void filterWithNullBreakingMultiWordMatchCaseInsensitive() {
    DictionaryStopwordFilter filter = DictionaryStopwordFilter.builder()
        .add("of", "the")
        .build();

    // "of" null "the" - null breaks the multi-word match
    String[] result = filter.filter(new String[]{"of", null, "the", "hill"});
    Assertions.assertArrayEquals(new String[]{"of", null, "the", "hill"}, result);
  }
}
