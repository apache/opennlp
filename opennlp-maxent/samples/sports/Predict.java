///////////////////////////////////////////////////////////////////////////////
// Copyright (C) 2001 Chieu Hai Leong and Jason Baldridge
//
// This library is free software; you can redistribute it and/or
// modify it under the terms of the GNU Lesser General Public
// License as published by the Free Software Foundation; either
// version 2.1 of the License, or (at your option) any later version.
//
// This library is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
// GNU Lesser General Public License for more details.
//
// You should have received a copy of the GNU Lesser General Public
// License along with this program; if not, write to the Free Software
// Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
//////////////////////////////////////////////////////////////////////////////   

import java.io.File;
import java.io.FileReader;

import opennlp.maxent.BasicContextGenerator;
import opennlp.maxent.ContextGenerator;
import opennlp.maxent.DataStream;
import opennlp.maxent.PlainTextByLineDataStream;
import opennlp.model.GenericModelReader;
import opennlp.model.MaxentModel;
import opennlp.model.RealValueFileEventStream;

/**
 * Test the model on some input.
 *
 * @author  Jason Baldridge
 * @version $Revision: 1.4 $, $Date: 2008-11-06 20:00:34 $
 */
public class Predict {
    MaxentModel _model;
    ContextGenerator _cg = new BasicContextGenerator();
    
    public Predict (MaxentModel m) {
	_model = m;
    }
    
    private void eval (String predicates) {
      eval(predicates,false);
    }
    
    private void eval (String predicates, boolean real) {
      String[] contexts = predicates.split(" ");
      double[] ocs;
      if (!real) {
        ocs = _model.eval(contexts);
      }
      else {
        float[] values = RealValueFileEventStream.parseContexts(contexts);
        ocs = _model.eval(contexts,values);
      }
      System.out.println("For context: " + predicates+ "\n" + _model.getAllOutcomes(ocs) + "\n");
	
    }
    
    private static void usage() {
      
    }

    /**
     * Main method. Call as follows:
     * <p>
     * java Predict dataFile (modelFile)
     */
    public static void main(String[] args) {
	String dataFileName, modelFileName;
    boolean real = false;
    String type = "maxent";
    int ai = 0;
	if (args.length > 0) {
      while (args[ai].startsWith("-")) {
        if (args[ai].equals("-real")) {
          real = true;
        }
        else if (args[ai].equals("-perceptron")) {
          type = "perceptron";
        }
        else {
          usage();
        }
        ai++;
      }      
      dataFileName = args[ai++];
      if (args.length > ai) { 
        modelFileName = args[ai++];
      }
      else {
          modelFileName = dataFileName.substring(0,dataFileName.lastIndexOf('.')) + "Model.txt";
      }
	}
	else {
	    dataFileName = "";
	    modelFileName = "weatherModel.txt";
	}
	Predict predictor = null;
	try {
      MaxentModel m = new GenericModelReader(new File(modelFileName)).getModel();
	  predictor = new Predict(m);
	} catch (Exception e) {
	    e.printStackTrace();
	    System.exit(0);
	}

	if (dataFileName.equals("")) {
	    predictor.eval("Rainy Happy Humid");
	    predictor.eval("Rainy");
	    predictor.eval("Blarmey");
	}
	else {
	    try {
		DataStream ds =
		    new PlainTextByLineDataStream(
			new FileReader(new File(dataFileName)));

		while (ds.hasNext()) {
		    String s = (String)ds.nextToken();
		    predictor.eval(s.substring(0, s.lastIndexOf(' ')),real);
		}
		return;
	    }
	    catch (Exception e) {
	      System.out.println("Unable to read from specified file: "+modelFileName);
	      System.out.println();
	      e.printStackTrace();
	    }
	}
    }
    
}
