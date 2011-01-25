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

package opennlp.model;

import junit.framework.TestCase;

public class IndexHashTableTest extends TestCase {

  public void testWithoutCollision() {

    String array[] = new String[3];

    array[0] = "4";
    array[1] = "7";
    array[2] = "5";

    IndexHashTable<String> arrayIndex = new IndexHashTable<String>(array, 1d);

    for (int i = 0; i < array.length; i++)
      assertEquals(i, arrayIndex.get(array[i]));
  }

  public void testWitCollision() {

    String array[] = new String[3];

    array[0] = "7";
    array[1] = "21";
    array[2] = "0";

    IndexHashTable<String> arrayIndex = new IndexHashTable<String>(array, 1d);

    for (int i = 0; i < array.length; i++)
      assertEquals(i, arrayIndex.get(array[i]));

    // has the same slot as as ""
    assertEquals(-1, arrayIndex.get("4"));
  }
}
