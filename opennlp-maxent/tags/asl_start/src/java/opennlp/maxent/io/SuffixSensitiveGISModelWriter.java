/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreemnets.  See the NOTICE file distributed with
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

package opennlp.maxent.io;

import opennlp.maxent.*;
import opennlp.model.AbstractModel;

import java.io.*;
import java.util.zip.*;

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
 * @version     $Revision: 1.2 $, $Date: 2008-09-28 18:04:22 $
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

    protected void writeUTF (String s) throws java.io.IOException {
	suffixAppropriateWriter.writeUTF(s);
    }

    protected void writeInt (int i) throws java.io.IOException {
	suffixAppropriateWriter.writeInt(i);
    }
    
    protected void writeDouble (double d) throws java.io.IOException {
	suffixAppropriateWriter.writeDouble(d);
    }

    protected void close () throws java.io.IOException {
	suffixAppropriateWriter.close();
    }

}
