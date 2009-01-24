/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreemnets.  See the NOTICE file distributed with
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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

import opennlp.tools.util.InvalidFormatException;

import junit.framework.TestCase;

/**
 * Tests for the {@link POSDictionary} class.
 */
public class POSDictionaryTest extends TestCase {

  public void testSerialization() throws IOException, InvalidFormatException {
    POSDictionary dictionary = new POSDictionary();

    dictionary.addTags("a", "1", "2", "3");
    dictionary.addTags("b", "4", "5", "6");
    dictionary.addTags("c", "7", "8", "9");
    dictionary.addTags("Always", "RB","NNP");


    ByteArrayOutputStream out = new ByteArrayOutputStream();

    try {
      dictionary.serialize(out);
    }
    finally {
       out.close();
    }

    InputStream in = new ByteArrayInputStream(out.toByteArray());

    POSDictionary serializedDictionary = null;
    try {
      serializedDictionary = POSDictionary.create(in);
    }
    finally {
        in.close();
    }

    assertTrue(dictionary.equals(serializedDictionary));
  }
}