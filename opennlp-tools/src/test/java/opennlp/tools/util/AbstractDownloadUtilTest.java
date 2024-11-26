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

package opennlp.tools.util;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.junit.jupiter.api.BeforeAll;

import opennlp.tools.EnabledWhenCDNAvailable;

import static org.junit.jupiter.api.Assertions.fail;

@EnabledWhenCDNAvailable(hostname = "dlcdn.apache.org")
public abstract class AbstractDownloadUtilTest {

  private static final String APACHE_CDN = "dlcdn.apache.org";

  @BeforeAll
  public static void cleanupWhenOnline() {
    boolean isOnline;
    try (Socket socket = new Socket()) {
      socket.connect(new InetSocketAddress(APACHE_CDN, 80), EnabledWhenCDNAvailable.TIMEOUT_MS);
      isOnline = true;
    } catch (IOException e) {
      // Unreachable, unresolvable or timeout
      isOnline = false;
    }
    // If CDN is available -> go cleanup in preparation of the actual tests
    if (isOnline) {
      wipeExistingModelFiles("-tokens-");
      wipeExistingModelFiles("-sentence-");
      wipeExistingModelFiles("-pos-");
      wipeExistingModelFiles("-lemmas-");
    }
  }


  /*
   * Helper method that wipes out mode files if they exist on the text execution env.
   * Those model files are wiped from a hidden '.opennlp' subdirectory.
   *
   * Thereby, a clean download can be guaranteed - ín CDN is available and test are executed.
   */
  private static void wipeExistingModelFiles(final String fragment) {
    final Path dir = Paths.get(System.getProperty("OPENNLP_DOWNLOAD_HOME",
        System.getProperty("user.home"))).resolve(".opennlp");
    if (Files.exists(dir)) {
      try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir, "*opennlp-*" + fragment + "*")) {
        for (Path modelFileToWipe : stream) {
          Files.deleteIfExists(modelFileToWipe);
        }
      } catch (IOException e) {
        fail(e.getLocalizedMessage());
      }
    }
  }

}
