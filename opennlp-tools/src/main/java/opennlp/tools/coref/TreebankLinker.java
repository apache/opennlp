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

package opennlp.tools.coref;

import java.io.IOException;

import opennlp.tools.coref.mention.PTBMentionFinder;

/**
 * This class perform coreference for treebank style parses.
 * <p>
 * It will only perform coreference over constituents defined in the trees and
 * will not generate new constituents for pre-nominal entities or sub-entities in
 * simple coordinated noun phrases.
 * <p>
 * This linker requires that named-entity information also be provided.
 */
public class TreebankLinker extends DefaultLinker {

  public TreebankLinker(String project, LinkerMode mode) throws IOException {
    super(project,mode);
  }

  public TreebankLinker(String project, LinkerMode mode, boolean useDiscourseModel) throws IOException {
    super(project,mode,useDiscourseModel);
  }

  public TreebankLinker(String project, LinkerMode mode, boolean useDiscourseModel, double fixedNonReferentialProbability) throws IOException {
    super(project,mode,useDiscourseModel,fixedNonReferentialProbability);
  }

  @Override
  protected void initMentionFinder() {
    mentionFinder = PTBMentionFinder.getInstance(headFinder);
  }
}
