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

package opennlp.tools.cmdline;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

import opennlp.tools.postag.POSModel;
import opennlp.tools.postag.POSSample;
import opennlp.tools.postag.POSTaggerME;
import opennlp.tools.postag.WordTagSampleStream;
import opennlp.tools.util.InvalidFormatException;
import opennlp.tools.util.ObjectStream;
import opennlp.tools.util.ObjectStreamException;

/**
 * The command line utility for the {@link POSEvaluator}.
 *
 * @see POSTaggerME
 * @see POSEvaluator
 */
public class POSEvaluator {

  public static void main(String[] args) throws InvalidFormatException, IOException, ObjectStreamException {

    if (args.length != 2) {
      System.err.println("Usage: java opennlp.tools.cmdline.POSTaggerEvaluator model training");
      System.exit(1);
    }

    POSModel model = new POSModel(new FileInputStream(args[0]));

    POSTaggerME tagger = new POSTaggerME(model);

    opennlp.tools.postag.POSEvaluator evaluator =
        new opennlp.tools.postag.POSEvaluator(tagger);

    InputStream in = new FileInputStream(args[1]);

    ObjectStream<POSSample> samples = new WordTagSampleStream((
          new InputStreamReader(in)));

    evaluator.evaluate(samples);

    in.close();

    System.out.println(evaluator.toString());
  }
}
