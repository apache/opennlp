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

package opennlp.tools.formats.nkjp;

import java.io.IOException;
import java.io.InputStream;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class NKJPSegmentationDocumentTest {
  @Test
  public void testParsingSimpleDoc() throws IOException {
    try (InputStream nkjpSegXmlIn =
           NKJPSegmentationDocumentTest.class.getResourceAsStream("ann_segmentation.xml")) {

      NKJPSegmentationDocument doc = NKJPSegmentationDocument.parse(nkjpSegXmlIn);

      assertEquals(1, doc.getSegments().size());

      assertEquals(7, doc.getSegments().get("segm_1.1-s").size());

      String src = "To krótkie zdanie w drugim akapicie.";

      int offset = doc.getSegments().get("segm_1.1-s").get("segm_1.1-seg").offset;
      assertEquals(0, offset);
      int length = doc.getSegments().get("segm_1.1-s").get("segm_1.1-seg").length;
      assertEquals(2, length);
      assertEquals("To", src.substring(offset, length));
    }
  }
}
