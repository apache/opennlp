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

package opennlp.tools.coref.resolver;

import opennlp.tools.coref.DiscourseEntity;
import opennlp.tools.coref.DiscourseModel;
import opennlp.tools.coref.mention.MentionContext;

/**
 * Resolver used in training to update the discourse model based on the coreference annotation.
 */
public class PerfectResolver extends  AbstractResolver {

  public PerfectResolver() {
    super(0);
  }

  public boolean canResolve(MentionContext ec) {
    return true;
  }

  @Override
  protected boolean outOfRange(MentionContext ec, DiscourseEntity de) {
    return false;
  }

  public DiscourseEntity resolve(MentionContext ec, DiscourseModel dm) {
    return null;
  }
}
