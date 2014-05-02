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

package opennlp.tools.parser;

public class ParserFactory {

  private ParserFactory() {
  }

  public static Parser create(ParserModel model, int beamSize, double advancePercentage) {

    if (ParserType.CHUNKING.equals(model.getParserType())) {
      return new opennlp.tools.parser.chunking.Parser(model, beamSize, advancePercentage);
    }
    else if (ParserType.TREEINSERT.equals(model.getParserType())) {
      return new opennlp.tools.parser.treeinsert.Parser(model, beamSize, advancePercentage);
    }
    else {
      throw new IllegalStateException("Unexpected ParserType: " +
          model.getParserType().name());
    }
  }

  public static Parser create(ParserModel model) {
    return create(model, AbstractBottomUpParser.defaultBeamSize,
        AbstractBottomUpParser.defaultAdvancePercentage);
  }
}
