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

package opennlp.tools.util.ext;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Registered through {@code META-INF/services} in the test resources so the
 * discovery of the default registry can be verified.
 */
public class TestExtensionRegistrar implements ExtensionRegistrar {

  static final AtomicInteger CREATED = new AtomicInteger();

  /**
   * An extension without a public constructor: only the registered supplier can
   * create it, the reflective path cannot.
   */
  public static final class RegisteredExtension {
    private RegisteredExtension() {
      CREATED.incrementAndGet();
    }

    public String value() {
      return "registered";
    }
  }

  @Override
  public void register(ExtensionRegistry registry) {
    registry.register(RegisteredExtension.class, RegisteredExtension::new);
  }
}
