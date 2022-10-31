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

package opennlp.tools.sentdetect;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;

import org.junit.Assert;
import org.junit.Test;

import opennlp.tools.sentdetect.lang.Factory;

public class DefaultSDContextGeneratorTest {

  @Test
  public void testGetContext() throws Exception {
    SDContextGenerator sdContextGenerator =
        new DefaultSDContextGenerator(Collections.<String>emptySet(), Factory.defaultEosCharacters);

    String[] context = sdContextGenerator.getContext(
        "Mr. Smith joined RONDHUIT Inc. as a manager of sales department.", 2);
    Assert.assertArrayEquals("sn/eos=./x=Mr/2/xcap/v=/s=/n=Smith/ncap".split("/"), context);

    context = sdContextGenerator.getContext(
        "Mr. Smith joined RONDHUIT Inc. as a manager of sales department.", 29);
    Assert.assertArrayEquals("sn/eos=./x=Inc/3/xcap/v=RONDHUIT/vcap/s=/n=as".split("/"), context);
  }

  @Test
  public void testGetContextWithAbbreviations() throws Exception {
    SDContextGenerator sdContextGenerator =
        new DefaultSDContextGenerator(new HashSet<>(Arrays.asList("Mr./Inc.".split("/"))),
            Factory.defaultEosCharacters);

    String[] context = sdContextGenerator.getContext(
        "Mr. Smith joined RONDHUIT Inc. as a manager of sales department.", 2);
    Assert.assertArrayEquals("sn/eos=./x=Mr/2/xcap/xabbrev/v=/s=/n=Smith/ncap".split("/"), context);

    context = sdContextGenerator.getContext(
        "Mr. Smith joined RONDHUIT Inc. as a manager of sales department.", 29);
    Assert.assertArrayEquals("sn/eos=./x=Inc/3/xcap/xabbrev/v=RONDHUIT/vcap/s=/n=as".split("/"), context);
  }
}
