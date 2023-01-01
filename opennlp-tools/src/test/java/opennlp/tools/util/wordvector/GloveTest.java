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

package opennlp.tools.util.wordvector;

import java.io.IOException;
import java.io.InputStream;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;


/*
 * Note:
 * Examples taken from the 'glove.6B.50d.txt' data set,
 * which is licensed under CC0 1.0 Universal (CC0 1.0) Public Domain Dedication.
 * (see: https://creativecommons.org/publicdomain/zero/1.0/)
 */
public class GloveTest extends AbstractWordVectorTest {

  @Test
  public void testParseValid() throws IOException {
    try (InputStream glove = getResourceStream("glove-example-short.txt")) {
      WordVectorTable wvTable = Glove.parse(glove);
      Assertions.assertNotNull(wvTable);
      Assertions.assertNotNull(wvTable.get("the"));
      Assertions.assertNotNull(wvTable.get("of"));
      Assertions.assertNotNull(wvTable.get("to"));
      Assertions.assertNotNull(wvTable.get("and"));

      // Tokens unknown in the short example should not be found (!)
      Assertions.assertNull(wvTable.get("OpenNLP"));
    }
  }

  @Test
  public void testParseEmpty() throws IOException {
    try (InputStream glove = getResourceStream("glove-example-empty.txt")) {
      WordVectorTable wvTable = Glove.parse(glove);
      Assertions.assertNotNull(wvTable);
      Assertions.assertEquals(-1, wvTable.dimension());
    }
  }

  @Test
  public void testParseDetectsBrokenDimensions() throws IOException {
    try (InputStream glove = getResourceStream("glove-example-broken-dimensions.txt")) {
      Assertions.assertThrows(IOException.class, () -> Glove.parse(glove));
    }
  }
}
