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

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class POSTaggerMEIT {

  @Test
  void testPOSTagger() throws IOException {

    POSTagger tagger = new POSTaggerME("en");

    String[] tags = tagger.tag(new String[] {
        "The",
        "driver",
        "got",
        "badly",
        "injured",
        "."});

    Assertions.assertEquals(6, tags.length);
    Assertions.assertEquals("DT", tags[0]);
    Assertions.assertEquals("NN", tags[1]);
    Assertions.assertEquals("VBD", tags[2]);
    Assertions.assertEquals("RB", tags[3]);
    Assertions.assertEquals("VBN", tags[4]);
    Assertions.assertEquals(".", tags[5]);
  }

}
