///////////////////////////////////////////////////////////////////////////////
//Copyright (C) 2001 Jason Baldridge and Gann Bierner

//This library is free software; you can redistribute it and/or
//modify it under the terms of the GNU Lesser General Public
//License as published by the Free Software Foundation; either
//version 2.1 of the License, or (at your option) any later version.

//This library is distributed in the hope that it will be useful,
//but WITHOUT ANY WARRANTY; without even the implied warranty of
//MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
//GNU General Public License for more details.

//You should have received a copy of the GNU Lesser General Public
//License along with this program; if not, write to the Free Software
//Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.

package opennlp.perceptron;

import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.zip.GZIPOutputStream;

import opennlp.model.AbstractModel;

/**
 * Model writer that saves models in binary format.
 *
 * @author      Jason Baldridge
 * @version     $Revision: 1.1 $, $Date: 2009-01-22 23:23:34 $
 */
public class BinaryPerceptronModelWriter extends PerceptronModelWriter {
  DataOutputStream output;

  /**
   * Constructor which takes a GISModel and a File and prepares itself to
   * write the model to that file. Detects whether the file is gzipped or not
   * based on whether the suffix contains ".gz".
   *
   * @param model The GISModel which is to be persisted.
   * @param f The File in which the model is to be persisted.
   */
  public BinaryPerceptronModelWriter (AbstractModel model, File f) throws IOException {

    super(model);

    if (f.getName().endsWith(".gz")) {
      output = new DataOutputStream(
          new GZIPOutputStream(new FileOutputStream(f)));
    }
    else {
      output = new DataOutputStream(new FileOutputStream(f));
    }
  }

  /**
   * Constructor which takes a GISModel and a DataOutputStream and prepares
   * itself to write the model to that stream.
   *
   * @param model The GISModel which is to be persisted.
   * @param dos The stream which will be used to persist the model.
   */
  public BinaryPerceptronModelWriter (AbstractModel model, DataOutputStream dos) {
    super(model);
    output = dos;
  }

  public void writeUTF (String s) throws java.io.IOException {
    output.writeUTF(s);
  }

  public void writeInt (int i) throws java.io.IOException {
    output.writeInt(i);
  }

  public void writeDouble (double d) throws java.io.IOException {
    output.writeDouble(d);
  }

  public void close () throws java.io.IOException {
    output.flush();
    output.close();
  }

}
