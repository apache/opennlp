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

package opennlp.tools.formats;

import java.io.InputStream;
import java.net.URL;

public abstract class AbstractFormatTest {

  protected static final String FORMATS_BASE_DIR = "/opennlp/tools/formats/";

  protected URL getResource(String resource) {
    return AbstractFormatTest.class.getResource(FORMATS_BASE_DIR + resource);
  }

  protected URL getResourceWithoutPrefix(String resource) {
    return getClass().getClassLoader().getResource(resource);
  }

  protected InputStream getResourceStream(String resource) {
    return AbstractFormatTest.class.getResourceAsStream(FORMATS_BASE_DIR + resource);
  }
}
