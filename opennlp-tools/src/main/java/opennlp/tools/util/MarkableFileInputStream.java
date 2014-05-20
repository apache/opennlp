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

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;

/**
 * A markable File Input Stream.
 */
class MarkableFileInputStream extends InputStream {

  private FileInputStream in;

  private long markedPosition = -1;
  private IOException markException;

  MarkableFileInputStream(File file) throws FileNotFoundException {
    in = new FileInputStream(file);
  }

  @Override
  public synchronized void mark(int readlimit) {
    try {
      markedPosition = in.getChannel().position();
    } catch (IOException e) {
      markedPosition = -1;
    }
  }

  @Override
  public boolean markSupported() {
    return true;
  }

  private void throwMarkExceptionIfOccured() throws IOException {
    if (markException != null) {
      throw markException;
    }
  }

  @Override
  public synchronized void reset() throws IOException {
    throwMarkExceptionIfOccured();

    if (markedPosition >= 0) {
      in.getChannel().position(markedPosition);
    }
    else {
      throw new IOException("Stream has to be marked before it can be reset!");
    }
  }

  @Override
  public int read() throws IOException {
    return in.read();
  }

  @Override
  public int read(byte[] b) throws IOException {
    return in.read(b);
  }

  @Override
  public int read(byte[] b, int off, int len) throws IOException {
    return in.read(b, off, len);
  }
}
