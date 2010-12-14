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

import opennlp.maxent.BasicEventStream;
import opennlp.maxent.EventStream;
import opennlp.maxent.GIS;
import opennlp.maxent.GISModel;
import opennlp.maxent.OnePassRealValueDataIndexer;
import opennlp.maxent.PlainTextByLineDataStream;
import opennlp.maxent.RealBasicEventStream;
import opennlp.maxent.io.GISModelWriter;
import opennlp.maxent.io.SuffixSensitiveGISModelWriter;

/**
 * Main class which calls the GIS procedure after building the EventStream
 * from the data.
 *
 * @author  Chieu Hai Leong and Jason Baldridge
 * @version $Revision: 1.6 $, $Date: 2007-04-13 16:24:06 $
 */
public class CreateModel {

    // some parameters if you want to play around with the smoothing option
    // for model training.  This can improve model accuracy, though training
    // will potentially take longer and use more memory.  Model size will also
    // be larger.  Initial testing indicates improvements for models built on
    // small data sets and few outcomes, but performance degradation for those
    // with large data sets and lots of outcomes.
    public static boolean USE_SMOOTHING = false;
    public static double SMOOTHING_OBSERVATION = 0.1;
    
    private static void usage() {
      System.err.println("java CreateModel [-real] dataFile");
      System.exit(1);
    }
    
    /**
     * Main method. Call as follows:
     * <p>
     * java CreateModel dataFile
     */
    public static void main (String[] args) {
      int ai = 0;
      boolean real = false;
      while (args[ai].startsWith("-")) {
        if (args[ai].equals("-real")) {
          real = true;
        }
        else {
          System.err.println("Unknown option: "+args[ai]);
          usage();
        }
        ai++;
      }
      String dataFileName = new String(args[ai]);
      String modelFileName =
        dataFileName.substring(0,dataFileName.lastIndexOf('.'))
        + "Model.txt";
      try {
        FileReader datafr = new FileReader(new File(dataFileName));
        EventStream es;
        if (!real) { 
          es = new BasicEventStream(new PlainTextByLineDataStream(datafr));
        }
        else {
          es = new RealBasicEventStream(new PlainTextByLineDataStream(datafr));
        }
        GIS.SMOOTHING_OBSERVATION = SMOOTHING_OBSERVATION;
        GISModel model;
        if (!real) {
          model = GIS.trainModel(es,USE_SMOOTHING);
        }
        else {
          model = GIS.trainModel(100, new OnePassRealValueDataIndexer(es,0), USE_SMOOTHING);
        }
        
        File outputFile = new File(modelFileName);
        GISModelWriter writer =
          new SuffixSensitiveGISModelWriter(model, outputFile);
        writer.persist();
      } catch (Exception e) {
        System.out.print("Unable to create model due to exception: ");
        System.out.println(e);
        e.printStackTrace();
      }
    }

}
