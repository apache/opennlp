///////////////////////////////////////////////////////////////////////////////
// Copyright (C) 2001 Jason Baldridge and Gann Bierner
//
// This library is free software; you can redistribute it and/or
// modify it under the terms of the GNU Lesser General Public
// License as published by the Free Software Foundation; either
// version 2.1 of the License, or (at your option) any later version.
//
// This library is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
// GNU General Public License for more details.
//
// You should have received a copy of the GNU Lesser General Public
// License along with this program; if not, write to the Free Software
// Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
//////////////////////////////////////////////////////////////////////////////   
package opennlp.perceptron;

import java.io.BufferedWriter;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.util.zip.GZIPOutputStream;

import opennlp.model.AbstractModel;
import opennlp.model.AbstractModelWriter;

/**
 * A writer for GIS models which inspects the filename and invokes the
 * appropriate GISModelWriter depending on the filename's suffixes.
 *
 * <p>The following assumption are made about suffixes:
 *    <li>.gz  --> the file is gzipped (must be the last suffix)
 *    <li>.txt --> the file is plain text
 *    <li>.bin --> the file is binary
 *
 * @author      Jason Baldridge
 * @version     $Revision: 1.1 $, $Date: 2009-01-22 23:23:34 $
 */
public class SuffixSensitivePerceptronModelWriter extends PerceptronModelWriter {
    private final AbstractModelWriter suffixAppropriateWriter;

    /**
     * Constructor which takes a GISModel and a File and invokes the
     * GISModelWriter appropriate for the suffix.
     *
     * @param model The GISModel which is to be persisted.
     * @param f The File in which the model is to be stored.
     */
    public SuffixSensitivePerceptronModelWriter (AbstractModel model, File f)
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
		new BinaryPerceptronModelWriter(model,
					 new DataOutputStream(output));
	}
	else { // default is ".txt"
	    suffixAppropriateWriter =
		new PlainTextPerceptronModelWriter(model,
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
