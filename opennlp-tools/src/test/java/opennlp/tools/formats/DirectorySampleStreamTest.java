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
import java.util.ArrayList;
import java.util.List;

import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class DirectorySampleStreamTest {
  
  @Rule
  public TemporaryFolder tempDirectory = new TemporaryFolder();
  
  @Test
  public void directoryTest() throws IOException {

    FileFilter filter = new TempFileNameFilter();
    
    List<File> files = new ArrayList<>();
    
    File temp1 = tempDirectory.newFile();
    files.add(temp1);
    
    File temp2 = tempDirectory.newFile();
    files.add(temp2);
    
    DirectorySampleStream stream = new DirectorySampleStream(tempDirectory.getRoot(), filter, false);
    
    File file = stream.read();
    Assert.assertTrue(files.contains(file));
    
    file = stream.read();
    Assert.assertTrue(files.contains(file));
    
    file = stream.read();
    Assert.assertNull(file);
    
    stream.close();
    
  }
  
  @Test
  public void directoryNullFilterTest() throws IOException {

    List<File> files = new ArrayList<>();
    
    File temp1 = tempDirectory.newFile();
    files.add(temp1);
    
    File temp2 = tempDirectory.newFile();
    files.add(temp2);
    
    DirectorySampleStream stream = new DirectorySampleStream(tempDirectory.getRoot(), null, false);
    
    File file = stream.read();
    Assert.assertTrue(files.contains(file));
    
    file = stream.read();
    Assert.assertTrue(files.contains(file));
    
    file = stream.read();
    Assert.assertNull(file);
    
    stream.close();
    
  }
  
  @Test
  public void recursiveDirectoryTest() throws IOException {

    FileFilter filter = new TempFileNameFilter();
    
    List<File> files = new ArrayList<>();
    
    File temp1 = tempDirectory.newFile();
    files.add(temp1);
    
    File tempSubDirectory = tempDirectory.newFolder("sub1");
    File temp2 = File.createTempFile("sub1", ".tmp", tempSubDirectory);
    files.add(temp2);

    DirectorySampleStream stream = new DirectorySampleStream(tempDirectory.getRoot(), filter, true);
    
    File file = stream.read();
    Assert.assertTrue(files.contains(file));
    
    file = stream.read();
    Assert.assertTrue(files.contains(file));
    
    file = stream.read();
    Assert.assertNull(file);
    
    stream.close();
    
  }
  
  @Test
  public void resetDirectoryTest() throws IOException {

    FileFilter filter = new TempFileNameFilter();
    
    List<File> files = new ArrayList<>();
    
    File temp1 = tempDirectory.newFile();
    files.add(temp1);
    
    File temp2 = tempDirectory.newFile();
    files.add(temp2);

    DirectorySampleStream stream = new DirectorySampleStream(tempDirectory.getRoot(), filter, false);
    
    File file = stream.read();
    Assert.assertTrue(files.contains(file));
    
    stream.reset();
    
    file = stream.read();
    Assert.assertTrue(files.contains(file));
    
    file = stream.read();
    Assert.assertTrue(files.contains(file));
    
    file = stream.read();
    Assert.assertNull(file);
    
    stream.close();
    
  }
  
  @Test
  public void emptyDirectoryTest() throws IOException {

    FileFilter filter = new TempFileNameFilter();
    
    DirectorySampleStream stream = new DirectorySampleStream(tempDirectory.getRoot(), filter, false);
    
    Assert.assertNull(stream.read());
    
    stream.close();
    
  }
  
  @Test(expected = IllegalArgumentException.class)
  public void invalidDirectoryTest() throws IOException {

    FileFilter filter = new TempFileNameFilter();
    
    DirectorySampleStream stream = new DirectorySampleStream(tempDirectory.newFile(), filter, false);
    
    Assert.assertNull(stream.read());
    
    stream.close();
    
  }
  
  class TempFileNameFilter implements FileFilter {
  
    @Override
    public boolean accept(File file) {
      return file.isDirectory() || file.getName().endsWith(".tmp");
    }
    
  }
      
}
