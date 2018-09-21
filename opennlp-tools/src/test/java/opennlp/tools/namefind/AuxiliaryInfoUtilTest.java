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

package opennlp.tools.namefind;

import org.junit.Assert;
import org.junit.Test;

public class AuxiliaryInfoUtilTest {

  @Test
  public void testGetSeparatorIndex() throws Exception {
    Assert.assertEquals(0, AuxiliaryInfoUtil.getSeparatorIndex("/POStag"));
    Assert.assertEquals(1, AuxiliaryInfoUtil.getSeparatorIndex("1/POStag"));
    Assert.assertEquals(10, AuxiliaryInfoUtil.getSeparatorIndex("word/stuff/POStag"));
  }

  @Test(expected = RuntimeException.class)
  public void testGetSeparatorIndexNoPos() throws Exception {
    AuxiliaryInfoUtil.getSeparatorIndex("NOPOStags");
  }

  @Test
  public void testGetWordPart() throws Exception {
    Assert.assertEquals(" ", AuxiliaryInfoUtil.getWordPart("/POStag"));
    Assert.assertEquals("1", AuxiliaryInfoUtil.getWordPart("1/POStag"));
    Assert.assertEquals("word", AuxiliaryInfoUtil.getWordPart("word/POStag"));
    Assert.assertEquals("word/stuff", AuxiliaryInfoUtil.getWordPart("word/stuff/POStag"));
  }

  @Test
  public void testGetWordParts() throws Exception {
    String[] results = AuxiliaryInfoUtil.getWordParts(new String[]{"1/A", "234/B", "3456/C", "/D"});
    Assert.assertEquals(4, results.length);
    Assert.assertEquals("1", results[0]);
    Assert.assertEquals("234", results[1]);
    Assert.assertEquals("3456", results[2]);
    Assert.assertEquals(" ", results[3]);
  }

  @Test
  public void testGetAuxPart() throws Exception {
    Assert.assertEquals("POStag", AuxiliaryInfoUtil.getAuxPart("/POStag"));
    Assert.assertEquals("POStag", AuxiliaryInfoUtil.getAuxPart("1/POStag"));
    Assert.assertEquals("POStag", AuxiliaryInfoUtil.getAuxPart("word/POStag"));
    Assert.assertEquals("POStag", AuxiliaryInfoUtil.getAuxPart("word/stuff/POStag"));
  }

  @Test
  public void testGetAuxParts() throws Exception {
    String[] results = AuxiliaryInfoUtil.getAuxParts(new String[] {"1/ABC", "234/B", "3456/CD", "/DEFGH"});
    Assert.assertEquals(4, results.length);
    Assert.assertEquals("ABC", results[0]);
    Assert.assertEquals("B", results[1]);
    Assert.assertEquals("CD", results[2]);
    Assert.assertEquals("DEFGH", results[3]);
  }
}
