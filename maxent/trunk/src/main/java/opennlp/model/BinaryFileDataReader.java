/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 * 
 *   http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package opennlp.model;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.zip.GZIPInputStream;

public class BinaryFileDataReader implements DataReader {

  private DataInputStream input;
  
  public BinaryFileDataReader(File f) throws IOException {
    if (f.getName().endsWith(".gz")) {
      input = new DataInputStream(new BufferedInputStream(
          new GZIPInputStream(new BufferedInputStream(new FileInputStream(f)))));
    }
    else {
      input = new DataInputStream(new BufferedInputStream(new FileInputStream(f)));
    }
  }
  
  public BinaryFileDataReader(InputStream in) {
    input = new DataInputStream(in);
  }
  
  public BinaryFileDataReader(DataInputStream in) {
    input = in;
  }
  
  public double readDouble() throws IOException {
    return input.readDouble();
  }

  public int readInt() throws IOException {
    return input.readInt();
  }

  public String readUTF() throws IOException {
    return input.readUTF();
  }

}
