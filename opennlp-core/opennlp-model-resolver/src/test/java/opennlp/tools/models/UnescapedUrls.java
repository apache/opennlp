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
package opennlp.tools.models;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLStreamHandler;

/**
 * Builds URLs whose path is kept unescaped, as some class loaders report their file URLs.
 * Such a URL is not a valid URI when its path contains a space. The JDK does not allow a
 * custom handler for {@code file}, so the URLs use the {@code test} protocol instead.
 */
final class UnescapedUrls {

  private UnescapedUrls() {
  }

  /**
   * Creates a {@code test} URL with {@code path} taken as is.
   *
   * @param path The absolute path, which may contain spaces. Must not be {@code null}.
   * @return A {@link URL} whose {@link URL#getFile()} is {@code path}.
   * @throws MalformedURLException Thrown if the URL cannot be created.
   */
  static URL of(String path) throws MalformedURLException {
    return URL.of(URI.create("test:/placeholder"), new URLStreamHandler() {
      @Override
      protected void parseURL(URL url, String spec, int start, int limit) {
        setURL(url, "test", "", -1, null, null, path, null, null);
      }

      @Override
      protected URLConnection openConnection(URL url) throws IOException {
        throw new IOException("the finders do not open this URL directly");
      }
    });
  }
}
