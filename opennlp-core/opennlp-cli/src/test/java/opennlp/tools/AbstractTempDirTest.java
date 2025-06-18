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

package opennlp.tools;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

// TODO: OPENNLP-1430 Remove workaround for @TempDir
// after https://github.com/junit-team/junit5/issues/2811 is fixed.
public abstract class AbstractTempDirTest {

  protected Path tempDir;

  @BeforeEach
  public void before() throws IOException {
    tempDir = Files.createTempDirectory(this.getClass().getSimpleName());
  }

  @AfterEach
  void after() {
    tempDir.toFile().deleteOnExit();
  }

}
