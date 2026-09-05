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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import opennlp.tools.geo.PlaceAncestor;
import opennlp.tools.util.InvalidFormatException;

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

  /** Verifies that traversal stops at a missing parent reference. */
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

  /** Verifies that the last addition replaces an existing identifier. */
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

  /** Verifies that traversal stops before repeating an identifier in a cycle. */
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

  /** Verifies that an acyclic chain stops at the 64-ancestor limit. */
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

  /** Verifies that a quoted newline stays within its RFC 4180 record. */
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

  /** Verifies that an unterminated quoted field reports its starting line. */
  @Test
  void testWofMetaCsvRejectsUnterminatedQuote(@TempDir Path dir) throws IOException {
    final Path meta = dir.resolve("unterminated.csv");
    Files.write(meta, String.join("\n",
        "id,parent_id,name,placetype",
        "123,456,\"Sao Paulo,locality",
        "456,-1,Brazil,country",
        "").getBytes(StandardCharsets.UTF_8));

    final InvalidFormatException e = Assertions.assertThrows(InvalidFormatException.class,
        () -> ContainmentSpine.builder().addWofMeta(meta));
    Assertions.assertTrue(e.getMessage().contains("unterminated quoted field"),
        e.getMessage());
    Assertions.assertTrue(e.getMessage().contains("line 2"), e.getMessage());
  }

  /** Verifies that an empty name reports its row and identifier. */
  @Test
  void testWofMetaRowRejectsEmptyName(@TempDir Path dir) throws IOException {
    final Path meta = dir.resolve("empty-name.csv");
    Files.write(meta, String.join("\n",
        "id,parent_id,name,placetype",
        "857,86,,county",
        "").getBytes(StandardCharsets.UTF_8));

    final InvalidFormatException e = Assertions.assertThrows(InvalidFormatException.class,
        () -> ContainmentSpine.builder().addWofMeta(meta));
    Assertions.assertTrue(e.getMessage().contains("empty name"), e.getMessage());
    Assertions.assertTrue(e.getMessage().contains("857"), e.getMessage());
    Assertions.assertTrue(e.getMessage().contains("line 2"), e.getMessage());
  }

  /** Verifies that an empty place type reports its row and identifier. */
  @Test
  void testWofMetaRowRejectsEmptyPlacetype(@TempDir Path dir) throws IOException {
    final Path meta = dir.resolve("empty-type.csv");
    Files.write(meta, String.join("\n",
        "id,parent_id,name,placetype",
        "857,86,Kings County,",
        "").getBytes(StandardCharsets.UTF_8));

    final InvalidFormatException e = Assertions.assertThrows(InvalidFormatException.class,
        () -> ContainmentSpine.builder().addWofMeta(meta));
    Assertions.assertTrue(e.getMessage().contains("empty placetype"), e.getMessage());
    Assertions.assertTrue(e.getMessage().contains("857"), e.getMessage());
  }

  /** Verifies that an empty identifier reports its line. */
  @Test
  void testWofMetaRowRejectsEmptyId(@TempDir Path dir) throws IOException {
    final Path meta = dir.resolve("empty-id.csv");
    Files.write(meta, String.join("\n",
        "id,parent_id,name,placetype",
        ",86,Kings County,county",
        "").getBytes(StandardCharsets.UTF_8));

    final InvalidFormatException e = Assertions.assertThrows(InvalidFormatException.class,
        () -> ContainmentSpine.builder().addWofMeta(meta));
    Assertions.assertTrue(e.getMessage().contains("empty id column"), e.getMessage());
    Assertions.assertTrue(e.getMessage().contains("line 2"), e.getMessage());
  }

  /** Verifies that a quote in an unquoted field reports its line. */
  @Test
  void testRejectsStrayQuoteInUnquotedField(@TempDir Path dir) throws IOException {
    final Path meta = dir.resolve("stray-quote.csv");
    Files.write(meta, String.join("\n",
        "id,parent_id,name,placetype",
        "123,456,Kings\"County,county",
        "456,-1,New York,locality",
        "").getBytes(StandardCharsets.UTF_8));

    final InvalidFormatException e = Assertions.assertThrows(InvalidFormatException.class,
        () -> ContainmentSpine.builder().addWofMeta(meta));
    Assertions.assertTrue(e.getMessage().contains("stray quote"), e.getMessage());
    Assertions.assertTrue(e.getMessage().contains("line 2"), e.getMessage());
  }

  /** Verifies that content after a closing quote reports its line. */
  @Test
  void testRejectsContentAfterClosingQuote(@TempDir Path dir) throws IOException {
    final Path meta = dir.resolve("after-quote.csv");
    Files.write(meta, String.join("\n",
        "id,parent_id,name,placetype",
        "123,456,\"Kings\"County,county",
        "").getBytes(StandardCharsets.UTF_8));

    final InvalidFormatException e = Assertions.assertThrows(InvalidFormatException.class,
        () -> ContainmentSpine.builder().addWofMeta(meta));
    Assertions.assertTrue(e.getMessage().contains("after a closing quote"),
        e.getMessage());
    Assertions.assertTrue(e.getMessage().contains("line 2"), e.getMessage());
  }

  /** Verifies CRLF endings between rows, at EOF, and inside a quoted field. */
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

  /** Verifies the RFC 4180 doubled-quote escape. */
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

  /** Verifies line numbers after a quoted field spans more than one line. */
  @Test
  void testErrorLineNumberAfterMultiLineQuotedField(@TempDir Path dir)
      throws IOException {
    final Path meta = dir.resolve("line-numbers.csv");
    Files.write(meta, String.join("\n",
        "id,parent_id,name,placetype",
        "123,456,\"Sao Paulo\nZona Norte\",locality",
        "456,-1,,country",
        "").getBytes(StandardCharsets.UTF_8));

    final InvalidFormatException e = Assertions.assertThrows(InvalidFormatException.class,
        () -> ContainmentSpine.builder().addWofMeta(meta));
    Assertions.assertTrue(e.getMessage().contains("empty name"), e.getMessage());
    Assertions.assertTrue(e.getMessage().contains("line 4"), e.getMessage());
  }

  @Test
  void testRejectsMalformedTables(@TempDir Path dir) throws IOException {
    final Path bad = dir.resolve("bad.tsv");
    Files.write(bad, "onlyone\n".getBytes(StandardCharsets.UTF_8));
    Assertions.assertThrows(InvalidFormatException.class,
        () -> ContainmentSpine.builder().addTable(bad));

    final Path noColumns = dir.resolve("meta.csv");
    Files.write(noColumns, "a,b,c\n1,2,3\n".getBytes(StandardCharsets.UTF_8));
    Assertions.assertThrows(InvalidFormatException.class,
        () -> ContainmentSpine.builder().addWofMeta(noColumns));
  }

  /** Verifies that a blank required value in a neutral table reports its line. */
  @Test
  void testNeutralTableRowRejectsBlankName(@TempDir Path dir) throws IOException {
    final Path blankName = dir.resolve("blank-name.tsv");
    Files.write(blankName, "x1\t\t \tregion\n".getBytes(StandardCharsets.UTF_8));

    final InvalidFormatException e = Assertions.assertThrows(InvalidFormatException.class,
        () -> ContainmentSpine.builder().addTable(blankName));
    Assertions.assertTrue(e.getMessage().contains("line 1"), e.getMessage());
    Assertions.assertTrue(e.getMessage().contains("name must not be null or blank"),
        e.getMessage());
  }

  @Test
  void testInvalidArguments() {
    Assertions.assertThrows(IllegalArgumentException.class,
        () -> ContainmentSpine.builder().build());
    Assertions.assertThrows(IllegalArgumentException.class,
        () -> ContainmentSpine.builder().add(null, null, "n", "t"));
    Assertions.assertThrows(IllegalArgumentException.class,
        () -> parkSlopeSpine().ancestors(null));
    Assertions.assertThrows(IllegalArgumentException.class,
        () -> ContainmentSpine.builder().add("child", " ", "Child", "locality"));
  }

  @Test
  void testNeutralTableRejectsExtraColumns(@TempDir Path dir) throws IOException {
    final Path table = dir.resolve("extra-column.tsv");
    Files.writeString(table, "x\t\tPlace\tlocality\textra\n", StandardCharsets.UTF_8);

    Assertions.assertThrows(InvalidFormatException.class,
        () -> ContainmentSpine.builder().addTable(table));
  }

  @Test
  void testNeutralTableRejectsMalformedUtf8(@TempDir Path dir) throws IOException {
    final Path table = dir.resolve("malformed-utf8.tsv");
    Files.write(table, new byte[] {'x', '\t', '\t', (byte) 0xc3, '\t', 't', '\n'});

    Assertions.assertThrows(IOException.class,
        () -> ContainmentSpine.builder().addTable(table));
  }

  @Test
  void testWofMetaRejectsMalformedUtf8(@TempDir Path dir) throws IOException {
    final Path table = dir.resolve("malformed-utf8.csv");
    final byte[] header = "id,parent_id,name,placetype\n1,-1,".getBytes(StandardCharsets.UTF_8);
    final byte[] suffix = ",locality\n".getBytes(StandardCharsets.UTF_8);
    final byte[] content = new byte[header.length + 1 + suffix.length];
    System.arraycopy(header, 0, content, 0, header.length);
    content[header.length] = (byte) 0xc3;
    System.arraycopy(suffix, 0, content, header.length + 1, suffix.length);
    Files.write(table, content);

    Assertions.assertThrows(IOException.class,
        () -> ContainmentSpine.builder().addWofMeta(table));
  }

  @ParameterizedTest
  @ValueSource(strings = {"abc", "+1", "-x"})
  void testWofMetaRejectsNonNumericParentIds(String parentId, @TempDir Path dir)
      throws IOException {
    final Path table = dir.resolve("invalid-parent.csv");
    Files.writeString(table, String.join("\n",
        "id,parent_id,name,placetype",
        "1," + parentId + ",Place,locality",
        ""), StandardCharsets.UTF_8);

    Assertions.assertThrows(InvalidFormatException.class,
        () -> ContainmentSpine.builder().addWofMeta(table));
  }
}
