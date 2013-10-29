/*
 * Copyright 2013 The Apache Software Foundation.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package opennlp.tools.entitylinker;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import opennlp.tools.entitylinker.domain.BaseLink;
import opennlp.tools.entitylinker.domain.LinkedSpan;
import opennlp.tools.util.Span;

/**
 * Utilized for abstracting the EntityLinker factory and covering the majority
 * of use cases.
 *
 */
public abstract class BaseEntityLinker {

  /**
   * Cache of linkers
   */
 
  protected Map<String, EntityLinker> singleLinkerMap = new HashMap<String, EntityLinker>();

  /**
   * Sets the LinkerMap to empty
   */
  protected void clearLinkerMap() {
    singleLinkerMap = new HashMap<>();
  }

  public ArrayList<LinkedSpan<BaseLink>> link(String docText, Span[] sentences, Span[] tokens,
          Span[] nameSpans, int sentenceIndex, EntityLinkerProperties properties) {
    ArrayList<LinkedSpan<BaseLink>> outLinkedSpans = new ArrayList<LinkedSpan<BaseLink>>();
    if (nameSpans.length == 0 || nameSpans == null) {
      return outLinkedSpans;
    }

    for (Span s : nameSpans) {
      EntityLinker linker = getInstance(s.getType(), properties);
      outLinkedSpans.addAll(linker.find(docText, sentences, tokens, nameSpans, sentenceIndex));
    }
    return outLinkedSpans;
  }

 

  private EntityLinker getInstance(String type, EntityLinkerProperties properties) {
    EntityLinker linker = null;
    if (singleLinkerMap.containsKey(type)) {
      linker = singleLinkerMap.get(type);
    } else {
      linker = EntityLinkerFactory.getLinker(type, properties);
      singleLinkerMap.put(type, linker);
    }
    return linker;
  }
}
