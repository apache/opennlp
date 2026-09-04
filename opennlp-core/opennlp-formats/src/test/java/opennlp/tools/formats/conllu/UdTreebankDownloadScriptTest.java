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

package opennlp.tools.formats.conllu;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@EnabledOnOs({OS.LINUX, OS.MAC})
public class UdTreebankDownloadScriptTest {

  @Test
  void testRequiresACommitForReproducibleInput(@TempDir Path tempDir)
      throws IOException, InterruptedException {
    final Path fakeBin = Files.createDirectory(tempDir.resolve("bin"));
    final Path fakeGit = fakeBin.resolve("git");
    Files.writeString(fakeGit, "#!/usr/bin/env bash\nexit 99\n", StandardCharsets.UTF_8);
    Files.setPosixFilePermissions(fakeGit, PosixFilePermissions.fromString("rwxr-xr-x"));

    final ProcessBuilder processBuilder = new ProcessBuilder("bash",
        Path.of("dev", "download-ud-treebank.sh").toString(),
        "UD_English-EWT", tempDir.resolve("treebank").toString());
    processBuilder.redirectErrorStream(true);
    processBuilder.environment().put("PATH", fakeBin + ":"
        + processBuilder.environment().get("PATH"));
    final Process process = processBuilder.start();
    final String output = new String(process.getInputStream().readAllBytes(),
        StandardCharsets.UTF_8);

    assertEquals(2, process.waitFor(), output);
    assertTrue(output.contains("<commit>"), output);
  }
}
