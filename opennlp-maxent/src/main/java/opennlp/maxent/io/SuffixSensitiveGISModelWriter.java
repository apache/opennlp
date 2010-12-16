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

package opennlp.maxent.io;

import java.io.BufferedWriter;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.util.zip.GZIPOutputStream;

import opennlp.model.AbstractModel;

/**
 * A writer for GIS models which inspects the filename and invokes the
 * appropriate GISModelWriter depending on the filename's suffixes.
 *
 * <p>The following assumption are made about suffixes:
 *    <li>.gz  --> the file is gzipped (must be the last suffix)
 *    <li>.txt --> the file is plain text
 *    <li>.bin --> the file is binary
 */
public class SuffixSensitiveGISModelWriter extends GISModelWriter {
  private final GISModelWriter suffixAppropriateWriter;

  /**
   * Constructor which takes a GISModel and a File and invokes the
   * GISModelWriter appropriate for the suffix.
   *
   * @param model The GISModel which is to be persisted.
   * @param f The File in which the model is to be stored.
   */
  public SuffixSensitiveGISModelWriter (AbstractModel model, File f)
  throws IOException {

    super (model);

    OutputStream output;
    String filename = f.getName();

    // handle the zipped/not zipped distinction
    if (filename.endsWith(".gz")) {
      output = new GZIPOutputStream(new FileOutputStream(f));
      filename = filename.substring(0,filename.length()-3);
    }
    else {
      output = new DataOutputStream(new FileOutputStream(f));
    }

    // handle the different formats
    if (filename.endsWith(".bin")) {
      suffixAppropriateWriter =
        new BinaryGISModelWriter(model,
            new DataOutputStream(output));
    }
    else { // default is ".txt"
      suffixAppropriateWriter =
        new PlainTextGISModelWriter(model,
            new BufferedWriter(new OutputStreamWriter(output)));
    }    
  }

  public void writeUTF (String s) throws java.io.IOException {
    suffixAppropriateWriter.writeUTF(s);
  }

  public void writeInt (int i) throws java.io.IOException {
    suffixAppropriateWriter.writeInt(i);
  }

  public void writeDouble (double d) throws java.io.IOException {
    suffixAppropriateWriter.writeDouble(d);
  }

  public void close () throws java.io.IOException {
    suffixAppropriateWriter.close();
  }
}
