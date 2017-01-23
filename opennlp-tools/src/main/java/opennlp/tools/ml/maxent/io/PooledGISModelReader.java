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

package opennlp.tools.ml.maxent.io;

import java.io.File;
import java.io.IOException;

/**
 * This class works exactly like the SuffisSensitiveGISModelReader except that it
 * attempts to pool all context strings.  This is useful when loading models which
 * share many context strings.
 *
 * @deprecated will be removed after 1.7.1 release. Don't use it.
 */
@Deprecated
public class PooledGISModelReader extends SuffixSensitiveGISModelReader {

  /**
   * A reader for GIS models which inspects the filename and invokes the
   * appropriate GISModelReader depending on the filename's suffixes.
   *
   * <p>The following assumption are made about suffixes:
   * <ul>
   *    <li>.gz  --&gt; the file is gzipped (must be the last suffix)</li>
   *    <li>.txt --&gt; the file is plain text</li>
   *    <li>.bin --&gt; the file is binary</li>
   * </ul>
   *
   * @param f
   * @throws IOException
   */
  public PooledGISModelReader(File f) throws IOException {
    super(f);
  }

  public String readUTF() throws IOException {
    return super.readUTF().intern();
  }
}
