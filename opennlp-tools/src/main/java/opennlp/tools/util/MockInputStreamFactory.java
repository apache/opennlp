/*
 * Copyright 2014 The Apache Software Foundation.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package opennlp.tools.util;

import java.io.IOException;
import java.io.InputStream;

/**
 *
 * Simple implementation for majority use case of passing input stream to
 * trainer
 */
public class MockInputStreamFactory implements InputStreamFactory {

  InputStream stream;

  public MockInputStreamFactory(InputStream stream) {
    this.stream = stream;
  }


  public MockInputStreamFactory() {
  }

  @Override
  public InputStream createInputStream() throws IOException {
    return stream;
  }
}
