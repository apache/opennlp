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

package opennlp.maxent;

import java.io.IOException;
import java.io.Reader;

import opennlp.model.Event;
import opennlp.model.EventStream;
import opennlp.model.MaxentModel;

/**
 * Trains or evaluates maxent components which have implemented the Evalable
 * interface.
 */
public class TrainEval {
    
    public static void eval(MaxentModel model, Reader r, Evalable e) {
	eval(model, r, e, false);
    }

    public static void eval(MaxentModel model, Reader r,
			    Evalable e, boolean verbose) {

	float totPos=0, truePos=0, falsePos=0;
	Event[] events = (e.getEventCollector(r)).getEvents(true);
	//MaxentModel model = e.getModel(dir, name);
	String negOutcome = e.getNegativeOutcome();
	for(int i=0; i<events.length; i++) {
	    String guess =
		model.getBestOutcome(model.eval(events[i].getContext()));
	    String ans = events[i].getOutcome();
	    if(verbose)
		System.out.println(ans + " " + guess);
	    if(!ans.equals(negOutcome)) totPos++;
	    if(!guess.equals(negOutcome) && !guess.equals(ans))
		falsePos++;
	    else if(ans.equals(guess))
		truePos++;
	}
	
	System.out.println("Precision: " + truePos/(truePos+falsePos));
	System.out.println("Recall:    " + truePos/totPos);
	
    }

    public static MaxentModel train(EventStream events, int cutoff) throws IOException {
	return GIS.trainModel(events, 100, cutoff);
    }

    public static void run(String[] args, Evalable e) throws IOException {
    	
    // TOM: Was commented out to remove dependency on gnu getopt.    	
    	
//	String dir = "./";
//	String stem = "maxent";
//	int cutoff = 0; // default to no cutoff
//	boolean train = false;
//	boolean verbose = false;
//	boolean local = false;
//	gnu.getopt.Getopt g =
//	    new gnu.getopt.Getopt("maxent", args, "d:s:c:tvl");
//	int c;
//	while ((c = g.getopt()) != -1) {
//	    switch(c) {
//	    case 'd':
//		dir = g.getOptarg()+"/";
//		break;
//	    case 's':
//		stem = g.getOptarg();
//		break;
//	    case 'c':
//		cutoff = Integer.parseInt(g.getOptarg());
//		break;
//	    case 't':
//		train = true;
//		break;
//	    case 'l':
//		local = true;
//		break;
//	    case 'v':
//		verbose = true;
//		break;
//	    }
//	}
//
//	int lastIndex = g.getOptind();
//	if (lastIndex >= args.length) {
//	    System.out.println("This is a usage message from opennlp.maxent.TrainEval. You have called the training procedure for a maxent application with the incorrect arguments.  These are the options:");
//
//	    System.out.println("\nOptions for defining the model location and name:");
//	    System.out.println(" -d <directoryName>");
//	    System.out.println("\tThe directory in which to store the model.");
//	    System.out.println(" -s <modelName>");
//	    System.out.println("\tThe name of the model, e.g. EnglishPOS.bin.gz or NameFinder.txt.");
//	    
//	    System.out.println("\nOptions for training:");
//	    System.out.println(" -c <cutoff>");
//	    System.out.println("\tAn integer cutoff level to reduce infrequent contextual predicates.");
//	    System.out.println(" -t\tTrain a model. If absent, the given model will be loaded and evaluated.");
//	    System.out.println("\nOptions for evaluation:");
//	    System.out.println(" -l\t the evaluation method of class that uses the model. If absent, TrainEval's eval method is used.");
//	    System.out.println(" -v\t verbose.");
//	    System.out.println("\nThe final argument is the data file to be loaded and used for either training or evaluation.");
//	    System.out.println("\nAs an example for training:\n java opennlp.grok.preprocess.postag.POSTaggerME -t -d ./ -s EnglishPOS.bin.gz -c 7 postag.data");
//	    System.exit(0);
//	}
//
//	FileReader datafr = new FileReader(args[lastIndex]);
//	
//	if (train) {
//	    MaxentModel m =
//		train(new EventCollectorAsStream(e.getEventCollector(datafr)),
//		      cutoff);
//	    new SuffixSensitiveGISModelWriter((AbstractModel)m,
//					      new File(dir+stem)).persist();
//	}
//	else {
//	    MaxentModel model =
//		new SuffixSensitiveGISModelReader(new File(dir+stem)).getModel();
//	    if (local) {
//		e.localEval(model, datafr, e, verbose);
//	    } else {
//		eval(model, datafr, e, verbose);
//	    }
//	}
    }

}