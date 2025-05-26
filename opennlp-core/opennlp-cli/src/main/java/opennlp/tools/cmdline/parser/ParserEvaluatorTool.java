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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import opennlp.tools.cmdline.AbstractEvaluatorTool;
import opennlp.tools.cmdline.TerminateToolException;
import opennlp.tools.cmdline.params.EvaluatorParams;
import opennlp.tools.parser.Parse;
import opennlp.tools.parser.Parser;
import opennlp.tools.parser.ParserEvaluator;
import opennlp.tools.parser.ParserFactory;
import opennlp.tools.parser.ParserModel;

/**
 * A default {@link Parse}-centric implementation of {@link AbstractEvaluatorTool}
 * that prints to an output stream.
 *
 * @see AbstractEvaluatorTool
 * @see EvaluatorParams
 */
public class ParserEvaluatorTool extends AbstractEvaluatorTool<Parse, EvaluatorParams> {

  private static final Logger logger = LoggerFactory.getLogger(ParserEvaluatorTool.class);

  public ParserEvaluatorTool() {
    super(Parse.class, EvaluatorParams.class);
  }

  @Override
  public String getShortDescription() {
    return "Measures the performance of the Parser model with the reference data";
  }

  @Override
  public void run(String format, String[] args) {

    super.run(format, args);

    ParserModel model = new ParserModelLoader().load(params.getModel());

    Parser parser = ParserFactory.create(model);

    ParserEvaluator evaluator = new ParserEvaluator(parser);

    logger.info("Evaluating ... ");
    try {
      evaluator.evaluate(sampleStream);
    }
    catch (IOException e) {
      throw new TerminateToolException(-1, "IO error while reading test data: " + e.getMessage(), e);
    } finally {
      try {
        sampleStream.close();
      } catch (IOException e) {
        // sorry that this can fail
      }
    }
    logger.info("done");

    logger.info(evaluator.getFMeasure().toString());
  }
}
