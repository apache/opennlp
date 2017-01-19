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

package opennlp.tools.formats.muc;

import java.io.IOException;

import org.junit.Assert;
import org.junit.Test;

import opennlp.tools.util.ObjectStream;
import opennlp.tools.util.ObjectStreamUtils;

public class DocumentSplitterStreamTest {

  @Test
  public void testSplitTwoDocuments() throws IOException {

    StringBuilder docsString = new StringBuilder();

    for (int i = 0; i < 2; i++) {
      docsString.append("<DOC>\n");
      docsString.append("test document #").append(i).append("\n");
      docsString.append("</DOC>\n");
    }

    try (ObjectStream<String> docs = new DocumentSplitterStream(
        ObjectStreamUtils.createObjectStream(docsString.toString()))) {
      String doc1 = docs.read();
      Assert.assertEquals(docsString.length() / 2, doc1.length() + 1);
      Assert.assertTrue(doc1.contains("#0"));

      String doc2 = docs.read();
      Assert.assertEquals(docsString.length() / 2, doc2.length() + 1);
      Assert.assertTrue(doc2.contains("#1"));

      Assert.assertNull(docs.read());
      Assert.assertNull(docs.read());
    }
  }
}
