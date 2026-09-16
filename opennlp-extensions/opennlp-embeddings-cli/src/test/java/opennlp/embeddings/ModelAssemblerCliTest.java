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
package opennlp.embeddings;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import opennlp.embeddings.cmdline.AssembleModelTool;
import opennlp.tools.cmdline.TerminateToolException;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Exercises model assembly through the CLI distribution. */
class ModelAssemblerCliTest {

  @Test
  void testToolPrintsASummaryAndRejectsABadDirectory(@TempDir Path dir) throws IOException {
    ModelAssemblerTest.writeWordpieceDistillation(dir);
    // The tool runs the assembly without throwing on a good directory.
    new AssembleModelTool().run(new String[] {"-modelDir", dir.toString()});

    // A directory that is not a model fails as a TerminateToolException, not a raw exception.
    final Path empty = Files.createDirectory(dir.resolve("empty"));
    final TerminateToolException e = assertThrows(TerminateToolException.class,
        () -> new AssembleModelTool().run(new String[] {"-modelDir", empty.toString()}));
    assertTrue(e.getMessage().contains("tokenizer.json") || e.getMessage().contains("distilled"),
        e.getMessage());
  }
}
