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

package opennlp.tools.chunker;

import org.junit.Assert;
import org.junit.Test;

/**
 * This is the test class for {@link ChunkerModel}.
 */
public class ChunkerModelTest {

  @Test
  public void testInvalidFactorySignature() throws Exception {

    ChunkerModel model = null;
    try {
      model = new ChunkerModel(this.getClass().getResourceAsStream("chunker170custom.bin"));
    } catch (IllegalArgumentException e) {
      Assert.assertTrue("Exception must state ChunkerFactory",
          e.getMessage().contains("ChunkerFactory"));
      Assert.assertTrue("Exception must mention DummyChunkerFactory",
          e.getMessage().contains("opennlp.tools.chunker.DummyChunkerFactory"));
    }
    Assert.assertNull(model);
  }

  @Test
  public void test170DefaultFactory() throws Exception {

    Assert.assertNotNull(
        new ChunkerModel(this.getClass().getResourceAsStream("chunker170default.bin")));

  }

  @Test
  public void test180CustomFactory() throws Exception {

    Assert.assertNotNull(
        new ChunkerModel(this.getClass().getResourceAsStream("chunker180custom.bin")));

  }
}
