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

import java.io.File;

import opennlp.tools.cmdline.ArgumentParser.OptionalParameter;
import opennlp.tools.cmdline.ArgumentParser.ParameterDescription;
import opennlp.tools.cmdline.params.BasicTrainingParams;

/**
 * TrainingParams for Parser.
 *
 * Note: Do not use this class, internal use only!
 */
interface TrainingParams extends BasicTrainingParams {

  @ParameterDescription(valueName = "CHUNKING|TREEINSERT",
      description = "one of CHUNKING or TREEINSERT, default is CHUNKING.")
  @OptionalParameter(defaultValue = "CHUNKING")
  String getParserType();


  @ParameterDescription(valueName = "className", description = "head rules artifact serializer class name")
  @OptionalParameter
  String getHeadRulesSerializerImpl();

  @ParameterDescription(valueName = "headRulesFile", description = "head rules file.")
  File getHeadRules();

  @ParameterDescription(valueName = "true|false", description = "Learn to generate function tags.")
  @OptionalParameter(defaultValue = "false")
  Boolean getFun();

}
