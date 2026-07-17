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

package opennlp.geo;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import opennlp.tools.geo.PlaceAncestor;

/**
 * Tests the containment hierarchy against project-authored miniature tables; no
 * external hierarchy data is involved.
 */
public class ContainmentSpineTest {

  private static ContainmentSpine parkSlopeSpine() {
    return ContainmentSpine.builder()
        .add("85865587", "421205765", "Park Slope", "neighbourhood")
        .add("421205765", "85977539", "Brooklyn", "borough")
        .add("85977539", "85688543", "New York", "locality")
        .add("85688543", "85633793", "New York", "region")
        .add("85633793", null, "United States", "country")
        .build();
  }

  @Test
  void testAncestorsWalkOutward() {
    final List<PlaceAncestor> chain = parkSlopeSpine().ancestors("85865587");

    Assertions.assertEquals(4, chain.size());
    Assertions.assertEquals("Brooklyn", chain.get(0).name());
    Assertions.assertEquals("borough", chain.get(0).type());
    Assertions.assertEquals("locality", chain.get(1).type());
    Assertions.assertEquals("region", chain.get(2).type());
    Assertions.assertEquals("United States", chain.get(3).name());
    Assertions.assertEquals("country", chain.get(3).type());
  }

  @Test
  void testUnknownAndRootPlacesHaveNoAncestors() {
    Assertions.assertTrue(parkSlopeSpine().ancestors("999").isEmpty());
    Assertions.assertTrue(parkSlopeSpine().ancestors("85633793").isEmpty());
  }

  @Test
  void testCyclesTerminate() {
    final ContainmentSpine cyclic = ContainmentSpine.builder()
        .add("a", "b", "A", "t")
        .add("b", "a", "B", "t")
        .build();
    Assertions.assertEquals(1, cyclic.ancestors("a").size());
  }

  /**
   * Asserts the traversal stop at a dangling parent reference: a parent identifier the
   * spine does not know contributes no ancestor and ends the chain, so a place whose
   * only parent is missing has an empty chain, and a longer chain keeps exactly the
   * places up to the dangling reference.
   */
  @Test
  void testMissingParentReferenceEndsTheChain() {
    final ContainmentSpine spine = ContainmentSpine.builder()
        .add("x", "y", "X", "t")
        .add("y", "zz", "Y", "t")
        .build();

    Assertions.assertEquals(List.of(new PlaceAncestor("y", "Y", "t")),
        spine.ancestors("x"));
    Assertions.assertTrue(spine.ancestors("y").isEmpty());
  }

  /**
   * Asserts the duplicate identifier behavior of the builder: adding an identifier
   * again replaces the earlier place, so a chain through that identifier reports the
   * name and type of the last addition.
   */
  @Test
  void testDuplicateIdsKeepTheLastAddition() {
    final ContainmentSpine spine = ContainmentSpine.builder()
        .add("child", "dup", "Child", "t")
        .add("dup", null, "First", "t1")
        .add("dup", null, "Second", "t2")
        .build();

    Assertions.assertEquals(List.of(new PlaceAncestor("dup", "Second", "t2")),
        spine.ancestors("child"));
  }

  /**
   * Asserts the exact traversal stop inside cycles of the user-supplied table: the walk
   * halts at the first identifier it has already seen, so a self-parented place has no
   * ancestors at all, and a three-place cycle yields exactly the two other places
   * before the walk would revisit the starting place.
   */
  @Test
  void testCyclesStopAtTheFirstRepeatedIdentifier() {
    final ContainmentSpine selfLoop = ContainmentSpine.builder()
        .add("a", "a", "A", "t")
        .build();
    Assertions.assertTrue(selfLoop.ancestors("a").isEmpty());

    final ContainmentSpine triangle = ContainmentSpine.builder()
        .add("a", "b", "A", "t")
        .add("b", "c", "B", "t")
        .add("c", "a", "C", "t")
        .build();
    Assertions.assertEquals(List.of(
        new PlaceAncestor("b", "B", "t"),
        new PlaceAncestor("c", "C", "t")),
        triangle.ancestors("a"));
  }

  /**
   * Asserts the internal depth cap on acyclic chains: a chain of seventy places stops
   * after sixty-four ancestors, nearest first, so a pathologically deep table cannot
   * produce an unbounded walk.
   */
  @Test
  void testDepthCapBoundsVeryDeepChains() {
    final ContainmentSpine.Builder builder = ContainmentSpine.builder();
    for (int i = 0; i < 70; i++) {
      builder.add("p" + i, i == 69 ? null : "p" + (i + 1), "P" + i, "t");
    }
    final List<PlaceAncestor> chain = builder.build().ancestors("p0");

    Assertions.assertEquals(64, chain.size());
    Assertions.assertEquals("p1", chain.get(0).id());
    Assertions.assertEquals("p64", chain.get(63).id());
  }

  @Test
  void testNeutralTableLoads(@TempDir Path dir) throws IOException {
    final Path table = dir.resolve("containment.tsv");
    Files.write(table, String.join("\n",
        "# id\tparent\tname\ttype",
        "Q123\tQ60\tSoHo\tneighbourhood",
        "Q60\tQ1384\tNew York City\tcity",
        "Q1384\t\tNew York\tstate",
        "").getBytes(StandardCharsets.UTF_8));

    final ContainmentSpine spine = ContainmentSpine.builder().addTable(table).build();
    final List<PlaceAncestor> chain = spine.ancestors("Q123");
    Assertions.assertEquals(2, chain.size());
    Assertions.assertEquals("New York City", chain.get(0).name());
    Assertions.assertEquals("New York", chain.get(1).name());
  }

  @Test
  void testWofMetaCsvLoadsWithQuotedNames(@TempDir Path dir) throws IOException {
    final Path meta = dir.resolve("wof-locality-latest.csv");
    Files.write(meta, String.join("\n",
        "bbox,id,name,parent_id,placetype,source",
        "\"1,2,3,4\",85977539,Brooklyn,85688543,borough,mz",
        "\"5,6,7,8\",85688543,\"New York, the Big Apple\",-1,locality,mz",
        "").getBytes(StandardCharsets.UTF_8));

    final ContainmentSpine spine = ContainmentSpine.builder().addWofMeta(meta).build();
    final List<PlaceAncestor> chain = spine.ancestors("85977539");
    Assertions.assertEquals(1, chain.size());
    Assertions.assertEquals("New York, the Big Apple", chain.get(0).name());
    Assertions.assertEquals("locality", chain.get(0).type());
  }

  /**
   * Asserts that a quoted field carrying an embedded newline stays one record, as RFC
   * 4180 requires: the place name keeps its newline, the row keeps its columns, and the
   * remainder of the name never becomes a record of its own.
   */
  @Test
  void testWofMetaCsvKeepsQuotedNewlinesInOneRecord(@TempDir Path dir) throws IOException {
    final Path meta = dir.resolve("wof-locality-latest.csv");
    Files.write(meta, String.join("\n",
        "id,parent_id,name,placetype",
        "999,123,Child,neighbourhood",
        "123,456,\"Sao Paulo\nZona Norte\",locality",
        "456,-1,Brazil,country",
        "").getBytes(StandardCharsets.UTF_8));

    final ContainmentSpine spine = ContainmentSpine.builder().addWofMeta(meta).build();

    Assertions.assertEquals(List.of(
        new PlaceAncestor("123", "Sao Paulo\nZona Norte", "locality"),
        new PlaceAncestor("456", "Brazil", "country")),
        spine.ancestors("999"));
  }

  /**
   * Asserts that a quoted field the file never closes fails loud and names the line the
   * quoted field started on, instead of silently swallowing the rest of the table.
   */
  @Test
  void testWofMetaCsvUnterminatedQuoteFailsLoud(@TempDir Path dir) throws IOException {
    final Path meta = dir.resolve("unterminated.csv");
    Files.write(meta, String.join("\n",
        "id,parent_id,name,placetype",
        "123,456,\"Sao Paulo,locality",
        "456,-1,Brazil,country",
        "").getBytes(StandardCharsets.UTF_8));

    final IOException e = Assertions.assertThrows(IOException.class,
        () -> ContainmentSpine.builder().addWofMeta(meta));
    Assertions.assertTrue(e.getMessage().contains("unterminated quoted field"),
        e.getMessage());
    Assertions.assertTrue(e.getMessage().contains("line 2"), e.getMessage());
  }

  /**
   * Asserts that a row whose name column is empty fails loud and names the offending
   * row and identifier, rather than being dropped so that every chain through that
   * place silently ends at a dangling parent.
   */
  @Test
  void testWofMetaRowWithEmptyNameFailsLoud(@TempDir Path dir) throws IOException {
    final Path meta = dir.resolve("empty-name.csv");
    Files.write(meta, String.join("\n",
        "id,parent_id,name,placetype",
        "857,86,,county",
        "").getBytes(StandardCharsets.UTF_8));

    final IOException e = Assertions.assertThrows(IOException.class,
        () -> ContainmentSpine.builder().addWofMeta(meta));
    Assertions.assertTrue(e.getMessage().contains("empty name"), e.getMessage());
    Assertions.assertTrue(e.getMessage().contains("857"), e.getMessage());
    Assertions.assertTrue(e.getMessage().contains("line 2"), e.getMessage());
  }

  /**
   * Asserts that a row whose placetype column is empty fails loud and names the
   * offending row and identifier.
   */
  @Test
  void testWofMetaRowWithEmptyPlacetypeFailsLoud(@TempDir Path dir) throws IOException {
    final Path meta = dir.resolve("empty-type.csv");
    Files.write(meta, String.join("\n",
        "id,parent_id,name,placetype",
        "857,86,Kings County,",
        "").getBytes(StandardCharsets.UTF_8));

    final IOException e = Assertions.assertThrows(IOException.class,
        () -> ContainmentSpine.builder().addWofMeta(meta));
    Assertions.assertTrue(e.getMessage().contains("empty placetype"), e.getMessage());
    Assertions.assertTrue(e.getMessage().contains("857"), e.getMessage());
  }

  /**
   * Asserts that a row whose id column is empty fails loud and names the offending
   * line, like its name and placetype siblings, instead of surfacing as a generic
   * builder rejection without any file context.
   */
  @Test
  void testWofMetaRowWithEmptyIdFailsLoud(@TempDir Path dir) throws IOException {
    final Path meta = dir.resolve("empty-id.csv");
    Files.write(meta, String.join("\n",
        "id,parent_id,name,placetype",
        ",86,Kings County,county",
        "").getBytes(StandardCharsets.UTF_8));

    final IOException e = Assertions.assertThrows(IOException.class,
        () -> ContainmentSpine.builder().addWofMeta(meta));
    Assertions.assertTrue(e.getMessage().contains("empty id column"), e.getMessage());
    Assertions.assertTrue(e.getMessage().contains("line 2"), e.getMessage());
  }

  /**
   * Asserts that a stray quote inside an unquoted field fails loud with its line
   * instead of opening a phantom quoted field that would silently splice the following
   * rows into one record and drop their places.
   */
  @Test
  void testStrayQuoteInUnquotedFieldFailsLoud(@TempDir Path dir) throws IOException {
    final Path meta = dir.resolve("stray-quote.csv");
    Files.write(meta, String.join("\n",
        "id,parent_id,name,placetype",
        "123,456,Kings\"County,county",
        "456,-1,New York,locality",
        "").getBytes(StandardCharsets.UTF_8));

    final IOException e = Assertions.assertThrows(IOException.class,
        () -> ContainmentSpine.builder().addWofMeta(meta));
    Assertions.assertTrue(e.getMessage().contains("stray quote"), e.getMessage());
    Assertions.assertTrue(e.getMessage().contains("line 2"), e.getMessage());
  }

  /**
   * Asserts that content directly after a field's closing quote fails loud with its
   * line, since accepting it would concatenate quoted and unquoted material into one
   * silently altered field.
   */
  @Test
  void testContentAfterClosingQuoteFailsLoud(@TempDir Path dir) throws IOException {
    final Path meta = dir.resolve("after-quote.csv");
    Files.write(meta, String.join("\n",
        "id,parent_id,name,placetype",
        "123,456,\"Kings\"County,county",
        "").getBytes(StandardCharsets.UTF_8));

    final IOException e = Assertions.assertThrows(IOException.class,
        () -> ContainmentSpine.builder().addWofMeta(meta));
    Assertions.assertTrue(e.getMessage().contains("after a closing quote"),
        e.getMessage());
    Assertions.assertTrue(e.getMessage().contains("line 2"), e.getMessage());
  }

  /**
   * Asserts that CRLF line endings parse exactly like LF, through all three CRLF
   * branches: between rows, at the end of the final row, and inside a quoted field,
   * where the quoted break is kept in the field as a single LF.
   */
  @Test
  void testCrlfRowsParseLikeLf(@TempDir Path dir) throws IOException {
    final Path meta = dir.resolve("crlf.csv");
    Files.write(meta, String.join("\r\n",
        "id,parent_id,name,placetype",
        "999,123,Child,neighbourhood",
        "123,456,\"Sao Paulo\r\nZona Norte\",locality",
        "456,-1,Brazil,country",
        "").getBytes(StandardCharsets.UTF_8));

    final ContainmentSpine spine = ContainmentSpine.builder().addWofMeta(meta).build();

    Assertions.assertEquals(List.of(
        new PlaceAncestor("123", "Sao Paulo\nZona Norte", "locality"),
        new PlaceAncestor("456", "Brazil", "country")),
        spine.ancestors("999"));
  }

  /**
   * Asserts the doubled-quote escape of RFC 4180: a doubled quote inside a quoted
   * field reads as one literal quote character in the place name.
   */
  @Test
  void testDoubledQuoteReadsAsOneLiteralQuote(@TempDir Path dir) throws IOException {
    final Path meta = dir.resolve("doubled-quote.csv");
    Files.write(meta, String.join("\n",
        "id,parent_id,name,placetype",
        "999,123,Child,neighbourhood",
        "123,-1,\"The \"\"Big Apple\"\"\",locality",
        "").getBytes(StandardCharsets.UTF_8));

    final ContainmentSpine spine = ContainmentSpine.builder().addWofMeta(meta).build();

    Assertions.assertEquals(List.of(
        new PlaceAncestor("123", "The \"Big Apple\"", "locality")),
        spine.ancestors("999"));
  }

  /**
   * Asserts that error line numbers stay correct after a quoted field spanning lines:
   * the row after a two-line quoted field starts on file line 4, and a defect in it is
   * reported there, pinning the line bookkeeping inside quoted fields.
   */
  @Test
  void testErrorLineNumberAfterMultiLineQuotedField(@TempDir Path dir)
      throws IOException {
    final Path meta = dir.resolve("line-numbers.csv");
    Files.write(meta, String.join("\n",
        "id,parent_id,name,placetype",
        "123,456,\"Sao Paulo\nZona Norte\",locality",
        "456,-1,,country",
        "").getBytes(StandardCharsets.UTF_8));

    final IOException e = Assertions.assertThrows(IOException.class,
        () -> ContainmentSpine.builder().addWofMeta(meta));
    Assertions.assertTrue(e.getMessage().contains("empty name"), e.getMessage());
    Assertions.assertTrue(e.getMessage().contains("line 4"), e.getMessage());
  }

  @Test
  void testMalformedTablesFailLoud(@TempDir Path dir) throws IOException {
    final Path bad = dir.resolve("bad.tsv");
    Files.write(bad, "onlyone\n".getBytes(StandardCharsets.UTF_8));
    Assertions.assertThrows(IOException.class,
        () -> ContainmentSpine.builder().addTable(bad));

    final Path noColumns = dir.resolve("meta.csv");
    Files.write(noColumns, "a,b,c\n1,2,3\n".getBytes(StandardCharsets.UTF_8));
    Assertions.assertThrows(IOException.class,
        () -> ContainmentSpine.builder().addWofMeta(noColumns));
  }

  @Test
  void testInvalidArguments() {
    Assertions.assertThrows(IllegalArgumentException.class,
        () -> ContainmentSpine.builder().build());
    Assertions.assertThrows(IllegalArgumentException.class,
        () -> ContainmentSpine.builder().add(null, null, "n", "t"));
    Assertions.assertThrows(IllegalArgumentException.class,
        () -> parkSlopeSpine().ancestors(null));
  }
}
