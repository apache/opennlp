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

package opennlp.tools.postag;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;

import org.junit.Assert;
import org.junit.Test;

import opennlp.tools.util.CollectionObjectStream;

/**
 * Tests for the {@link WordTagSampleStream} class.
 */
public class WordTagSampleStreamTest {

  @Test
  public void testParseSimpleSample() throws IOException {

    Collection<String> sampleString = new ArrayList<>(1);
    sampleString.add("This_x1 is_x2 a_x3 test_x4 sentence_x5 ._x6");

    WordTagSampleStream stream =
        new WordTagSampleStream(new CollectionObjectStream<>(sampleString));

    POSSample sample = stream.read();
    String words[] = sample.getSentence();

    Assert.assertEquals("This", words[0]);
    Assert.assertEquals("is", words[1]);
    Assert.assertEquals("a", words[2]);
    Assert.assertEquals("test", words[3]);
    Assert.assertEquals("sentence", words[4]);
    Assert.assertEquals(".", words[5]);

    String tags[] = sample.getTags();
    Assert.assertEquals("x1", tags[0]);
    Assert.assertEquals("x2", tags[1]);
    Assert.assertEquals("x3", tags[2]);
    Assert.assertEquals("x4", tags[3]);
    Assert.assertEquals("x5", tags[4]);
    Assert.assertEquals("x6", tags[5]);

    Assert.assertNull(stream.read());
    stream.reset();
    Assert.assertNotNull(stream.read());
  }
}
