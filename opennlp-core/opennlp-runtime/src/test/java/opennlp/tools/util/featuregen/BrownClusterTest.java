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

package opennlp.tools.util.featuregen;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class BrownClusterTest {

  private static BrownCluster cluster(String lexicon) throws IOException {
    return new BrownCluster(new ByteArrayInputStream(lexicon.getBytes(StandardCharsets.UTF_8)));
  }

  @Test
  void testThreeColumnLinesNeedFrequencyAboveFive() throws IOException {
    BrownCluster cluster = cluster("0101\tthe\t10\n0110\trare\t5\n");
    Assertions.assertEquals("0101", cluster.lookupToken("the"));
    Assertions.assertNull(cluster.lookupToken("rare"));
  }

  @Test
  void testTwoColumnLinesMapFirstFieldToSecond() throws IOException {
    BrownCluster cluster = cluster("dog\t0111\n");
    Assertions.assertEquals("0111", cluster.lookupToken("dog"));
  }

  @Test
  void testTrailingTabsAreDropped() throws IOException {
    // trailing empty fields do not count, as with String.split("\t")
    BrownCluster cluster = cluster("cat\t0100\t\t\n0011\tbird\t7\t\n");
    Assertions.assertEquals("0100", cluster.lookupToken("cat"));
    Assertions.assertEquals("0011", cluster.lookupToken("bird"));
  }

  @Test
  void testLinesWithOtherFieldCountsAreIgnored() throws IOException {
    BrownCluster cluster = cluster("\n\t\nsingle\na\tb\tc\td\n0101\tfish\t10\n");
    Assertions.assertNull(cluster.lookupToken("single"));
    Assertions.assertNull(cluster.lookupToken("a"));
    Assertions.assertNull(cluster.lookupToken(""));
    Assertions.assertEquals("0101", cluster.lookupToken("fish"));
  }

  @Test
  void testSpacesAreNotSeparators() throws IOException {
    BrownCluster cluster = cluster("0101 the 10\n");
    Assertions.assertNull(cluster.lookupToken("the"));
  }
}
