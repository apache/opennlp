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

package opennlp.tools.formats.conllu;

import org.junit.Assert;
import org.junit.Test;

import opennlp.tools.util.InvalidFormatException;

public class ConlluWordLineTest {

  @Test
  public void testParseLine() throws InvalidFormatException {
    ConlluWordLine line = new ConlluWordLine(
        "12\tHänden\tHand\tNOUN\tNN\tCase=Dat|Number=Plur\t5\tnmod\t_\t_");

    Assert.assertEquals("12", line.getId());
    Assert.assertEquals("Händen", line.getForm());
    Assert.assertEquals("Hand", line.getLemma());
    Assert.assertEquals("NOUN", line.getPosTag(ConlluTagset.U));
    Assert.assertEquals("NN", line.getPosTag(ConlluTagset.X));
    Assert.assertEquals("Case=Dat|Number=Plur", line.getFeats());
    Assert.assertEquals("5", line.getHead());
    Assert.assertEquals("nmod", line.getDeprel());
    Assert.assertEquals("_", line.getDeps());
    Assert.assertEquals("_", line.getMisc());
  }
}
