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

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import opennlp.tools.AbstractTempDirTest;

public class DirectorySampleStreamTest extends AbstractTempDirTest {

  private FileFilter filter;

  @BeforeEach
  public void setup() {
    filter = new TempFileNameFilter();
  }

  @Test
  public void directoryTest() throws IOException {

    List<File> files = new ArrayList<>();
    
    File temp1 = createTempFile();
    files.add(temp1);
    
    File temp2 = createTempFile();
    files.add(temp2);
    
    DirectorySampleStream stream = new DirectorySampleStream(tempDir.toFile(), filter, false);
    
    File file = stream.read();
    Assertions.assertTrue(files.contains(file));
    
    file = stream.read();
    Assertions.assertTrue(files.contains(file));
    
    file = stream.read();
    Assertions.assertNull(file);
    
    stream.close();
    
  }
  
  @Test
  public void directoryNullFilterTest() throws IOException {

    List<File> files = new ArrayList<>();
    
    File temp1 = createTempFile();
    files.add(temp1);
    
    File temp2 = createTempFile();
    files.add(temp2);
    
    DirectorySampleStream stream = new DirectorySampleStream(tempDir.toFile(), null, false);
    
    File file = stream.read();
    Assertions.assertTrue(files.contains(file));
    
    file = stream.read();
    Assertions.assertTrue(files.contains(file));
    
    file = stream.read();
    Assertions.assertNull(file);
    
    stream.close();
    
  }
  
  @Test
  public void recursiveDirectoryTest() throws IOException {

    List<File> files = new ArrayList<>();
    
    File temp1 = createTempFile();
    files.add(temp1);
    
    File tempSubDirectory = createTempFolder("sub1");
    File temp2 = Files.createTempFile(tempSubDirectory.toPath(), "sub1", ".tmp").toFile();
    files.add(temp2);

    DirectorySampleStream stream = new DirectorySampleStream(tempDir.toFile(), filter, true);
    
    File file = stream.read();
    Assertions.assertTrue(files.contains(file));
    
    file = stream.read();
    Assertions.assertTrue(files.contains(file));
    
    file = stream.read();
    Assertions.assertNull(file);
    
    stream.close();
    
  }
  
  @Test
  public void resetDirectoryTest() throws IOException {

    List<File> files = new ArrayList<>();
    
    File temp1 = createTempFile();
    files.add(temp1);
    
    File temp2 = createTempFile();
    files.add(temp2);

    DirectorySampleStream stream = new DirectorySampleStream(tempDir.toFile(), filter, false);
    
    File file = stream.read();
    Assertions.assertTrue(files.contains(file));
    
    stream.reset();
    
    file = stream.read();
    Assertions.assertTrue(files.contains(file));
    
    file = stream.read();
    Assertions.assertTrue(files.contains(file));
    
    file = stream.read();
    Assertions.assertNull(file);
    
    stream.close();
    
  }
  
  @Test
  public void emptyDirectoryTest() throws IOException {

    DirectorySampleStream stream = new DirectorySampleStream(tempDir.toFile(), filter, false);
    Assertions.assertNull(stream.read());
    
    stream.close();
    
  }

  @Test
  public void invalidDirectoryTest() {
    Assertions.assertThrows(IllegalArgumentException.class, () -> {
      FileFilter filter = new TempFileNameFilter();

      DirectorySampleStream stream = new DirectorySampleStream(createTempFile(), filter, false);

      Assertions.assertNull(stream.read());

      stream.close();
    });
  }

  private File createTempFolder(String name) {

    Path subDir = tempDir.resolve(name);

    try {
      Files.createDirectory(subDir);
    } catch (IOException e) {
      throw new IllegalStateException(
          "Could not create sub directory " + subDir.toFile().getAbsolutePath(), e);
    }
    return subDir.toFile();

  }

  private File createTempFile() {

    Path tempFile = tempDir.resolve(UUID.randomUUID() + ".tmp");

    try {
      Files.createFile(tempFile);
    } catch (IOException e) {
      throw new IllegalStateException(
          "Could not create file " + tempFile.toFile().getAbsolutePath(), e);
    }
    return tempFile.toFile();

  }
  
  static class TempFileNameFilter implements FileFilter {
  
    @Override
    public boolean accept(File file) {
      return file.isDirectory() || file.getName().endsWith(".tmp");
    }
    
  }
      
}
