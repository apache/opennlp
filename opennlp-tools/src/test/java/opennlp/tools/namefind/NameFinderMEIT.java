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

import opennlp.tools.util.DownloadUtil;
import opennlp.tools.util.Span;

public class NameFinderMEIT {

  @Test
  public void testNameFinderWithDownloadedModel() throws Exception {

    String input = "Pierre Vinken , 61 years old , will join the board as a nonexecutive director Nov. 29 .";
    String[] sentence = input.split(" ");

    TokenNameFinder nameFinder = new NameFinderME("en", DownloadUtil.EntityType.PERSON);
    Span[] names = nameFinder.find(sentence);

    Assert.assertEquals(1, names.length);
    Assert.assertEquals(new Span(0, 2, "person"), names[0]);
    Assert.assertEquals("person", names[0].getType());

  }

}
