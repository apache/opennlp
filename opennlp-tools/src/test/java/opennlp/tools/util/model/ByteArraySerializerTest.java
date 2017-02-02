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

package opennlp.tools.util.model;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Random;

import org.junit.Assert;
import org.junit.Test;

public class ByteArraySerializerTest {

  @Test
  public void testSerialization() throws IOException {

    byte[] b = new byte[1024];
    new Random(23).nextBytes(b);

    ByteArraySerializer serializer = new ByteArraySerializer();

    ByteArrayOutputStream bOut = new ByteArrayOutputStream();
    serializer.serialize(Arrays.copyOf(b, b.length), bOut) ;

    Assert.assertArrayEquals(b, bOut.toByteArray());
    Assert.assertArrayEquals(b, serializer.create(new ByteArrayInputStream(b)));
  }
}
