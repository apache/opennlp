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
import java.util.Arrays;
import java.util.Set;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

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
}
