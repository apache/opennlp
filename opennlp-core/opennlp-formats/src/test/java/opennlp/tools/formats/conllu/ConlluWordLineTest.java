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

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import opennlp.tools.util.InvalidFormatException;

public class ConlluWordLineTest {

  @Test
  void testParseLine() throws InvalidFormatException {
    ConlluWordLine line = new ConlluWordLine(
        "12\tHänden\tHand\tNOUN\tNN\tCase=Dat|Number=Plur\t5\tnmod\t_\t_");

    Assertions.assertEquals("12", line.getId());
    Assertions.assertEquals("Händen", line.getForm());
    Assertions.assertEquals("Hand", line.getLemma());
    Assertions.assertEquals("NOUN", line.getPosTag(ConlluTagset.U));
    Assertions.assertEquals("NN", line.getPosTag(ConlluTagset.X));
    Assertions.assertEquals("Case=Dat|Number=Plur", line.getFeats());
    Assertions.assertEquals("5", line.getHead());
    Assertions.assertEquals("nmod", line.getDeprel());
    Assertions.assertEquals("_", line.getDeps());
    Assertions.assertEquals("_", line.getMisc());
  }
}
