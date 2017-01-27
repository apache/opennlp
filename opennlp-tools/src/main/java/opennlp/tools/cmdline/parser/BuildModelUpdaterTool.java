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

package opennlp.tools.cmdline.parser;

import java.io.IOException;

import opennlp.tools.dictionary.Dictionary;
import opennlp.tools.ml.maxent.GIS;
import opennlp.tools.ml.model.Event;
import opennlp.tools.ml.model.MaxentModel;
import opennlp.tools.parser.Parse;
import opennlp.tools.parser.ParserEventTypeEnum;
import opennlp.tools.parser.ParserModel;
import opennlp.tools.parser.chunking.ParserEventStream;
import opennlp.tools.util.ObjectStream;

public final class BuildModelUpdaterTool extends ModelUpdaterTool {

  public String getShortDescription() {
    return "trains and updates the build model in a parser model";
  }

  @Override
  protected ParserModel trainAndUpdate(ParserModel originalModel,
      ObjectStream<Parse> parseSamples, ModelUpdaterParams parameters)
      throws IOException {

    Dictionary mdict = ParserTrainerTool.buildDictionary(parseSamples, originalModel.getHeadRules(), 5);

    parseSamples.reset();

    // TODO: training individual models should be in the chunking parser, not here
    // Training build
    System.out.println("Training builder");
    ObjectStream<Event> bes = new ParserEventStream(parseSamples,
        originalModel.getHeadRules(), ParserEventTypeEnum.BUILD, mdict);

    GIS trainer = new GIS();
    MaxentModel buildModel = trainer.train(bes);

    parseSamples.close();

    return originalModel.updateBuildModel(buildModel);
  }
}
