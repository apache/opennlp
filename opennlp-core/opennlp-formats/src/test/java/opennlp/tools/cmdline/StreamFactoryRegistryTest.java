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

package opennlp.tools.cmdline;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import opennlp.tools.tokenize.TokenSample;
import opennlp.tools.util.ObjectStream;

public class StreamFactoryRegistryTest {

  public static class DummyFactory implements ObjectStreamFactory<String, Object> {

    public DummyFactory() {
    }

    @Override
    public Class<Object> getParameters() {
      return Object.class;
    }

    @Override
    public ObjectStream<String> create(String[] args) {
      return null;
    }
  }

  @Test
  void testGetRegisteredFactory() {
    ObjectStreamFactory<TokenSample, ?> factory =
        StreamFactoryRegistry.getFactory(TokenSample.class, StreamFactoryRegistry.DEFAULT_FORMAT);
    Assertions.assertNotNull(factory);
  }

  /**
   * Positive: an unregistered format name referencing an opennlp.* factory class
   * is loaded via the ExtensionLoader. opennlp.* is in the default allowlist and
   * the type matches ObjectStreamFactory.
   */
  @Test
  void testGetFactoryViaClassNameAllowedOpennlpPackage() {
    ObjectStreamFactory<String, Object> factory =
        StreamFactoryRegistry.getFactory(String.class, DummyFactory.class.getName());
    Assertions.assertNotNull(factory);
    Assertions.assertInstanceOf(DummyFactory.class, factory);
  }

  /**
   * Negative: a format name referencing a non-opennlp.* class is rejected by
   * the ExtensionLoader allowlist before Class.forName() is called.
   */
  @Test
  void testGetFactoryViaClassNameBlockedPackageReturnsNull() {
    Assertions.assertNull(StreamFactoryRegistry.getFactory(String.class, "com.malicious.Exploit"));
  }

  /**
   * Negative: a format name referencing an opennlp.* class that does NOT implement
   * ObjectStreamFactory is rejected by the isAssignableFrom type check.
   */
  @Test
  void testGetFactoryViaClassNameWrongTypeReturnsNull() {
    Assertions.assertNull(StreamFactoryRegistry.getFactory(String.class,
        "opennlp.tools.tokenize.SimpleTokenizer"));
  }
}
