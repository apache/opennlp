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

import java.io.*;
import java.util.zip.*;

/**
 * A reader for GIS models stored in plain text format.
 *
 * @author      Jason Baldridge
 * @version     $Revision: 1.2 $, $Date: 2008-09-28 18:04:30 $
 */
public class PlainTextGISModelReader extends GISModelReader {
    private BufferedReader input;

    /**
     * Constructor which directly instantiates the BufferedReader containing
     * the model contents.
     *
     * @param br The BufferedReader containing the model information.
     */
    public PlainTextGISModelReader (BufferedReader br) {
	input = br;
    }

    /**
     * Constructor which takes a File and creates a reader for it. Detects
     * whether the file is gzipped or not based on whether the suffix contains
     * ".gz".
     *
     * @param f The File in which the model is stored.
     */
    public PlainTextGISModelReader (File f) throws IOException {

	if (f.getName().endsWith(".gz")) {
	    input = new BufferedReader(new InputStreamReader(
  	                 new GZIPInputStream(new FileInputStream(f))));
	}
	else {
	    input = new BufferedReader(new FileReader(f));
	}

    }

    public int readInt () throws IOException {
	return Integer.parseInt(input.readLine());
    }

    public double readDouble () throws IOException {
	return Double.parseDouble(input.readLine());
    }

    public String readUTF () throws IOException {
	return input.readLine();
    }

}
