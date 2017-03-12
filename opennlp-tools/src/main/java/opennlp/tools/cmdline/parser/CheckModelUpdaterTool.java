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
import opennlp.tools.ml.EventTrainer;
import opennlp.tools.ml.TrainerFactory;
import opennlp.tools.ml.model.Event;
import opennlp.tools.ml.model.MaxentModel;
import opennlp.tools.parser.Parse;
import opennlp.tools.parser.ParserEventTypeEnum;
import opennlp.tools.parser.ParserModel;
import opennlp.tools.parser.chunking.ParserEventStream;
import opennlp.tools.util.ObjectStream;
import opennlp.tools.util.model.ModelUtil;

// trains a new check model ...
public final class CheckModelUpdaterTool extends ModelUpdaterTool {

  public String getShortDescription() {
    return "trains and updates the check model in a parser model";
  }

  @Override
  protected ParserModel trainAndUpdate(ParserModel originalModel,
      ObjectStream<Parse> parseSamples, ModelUpdaterParams parameters)
      throws IOException {

    Dictionary mdict = ParserTrainerTool.buildDictionary(parseSamples, originalModel.getHeadRules(), 5);

    parseSamples.reset();

    // TODO: Maybe that should be part of the ChunkingParser ...
    // Training build
    System.out.println("Training check model");
    ObjectStream<Event> bes = new ParserEventStream(parseSamples,
        originalModel.getHeadRules(), ParserEventTypeEnum.CHECK, mdict);

    EventTrainer trainer = TrainerFactory.getEventTrainer(
        ModelUtil.createDefaultTrainingParameters(), null);
    MaxentModel checkModel = trainer.train(bes);

    parseSamples.close();

    return originalModel.updateCheckModel(checkModel);
  }
}
