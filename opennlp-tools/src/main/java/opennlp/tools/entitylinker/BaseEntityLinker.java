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
import java.util.List;
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
  protected Map<String, List<EntityLinker>> linkerMap = new HashMap<String, List<EntityLinker>>();

  /**
   * Sets the LinkerMap to empty
   */
  protected void clearLinkerMap() {
    linkerMap = new HashMap<String, List<EntityLinker>>();
  }

  /**
   *
   * @param entitytypes the list of types (to corresponding properties keys) to
   *                    get linkers for
   * @param docText     the document text
   * @param sentences   the sentence spans that correspond to the doc text
   * @param tokens      the token spans that correspond to one of the sentences
   * @param nameSpans   the name spans that correspond to the tokens
   * @param properties  the EntityLinkerProperties file with the proper
   *                    configuration
   * @return
   */
  protected ArrayList<LinkedSpan<BaseLink>> getAggregatedLinkedSpans(String[] entitytypes, String docText, Span[] sentences, Span[] tokens,
          Span[] nameSpans, EntityLinkerProperties properties) {

    ArrayList<LinkedSpan<BaseLink>> outLinkedSpans = new ArrayList<LinkedSpan<BaseLink>>();
    for (String type : entitytypes) {
      List<EntityLinker> linkers = getInstances(type, properties);
      for (EntityLinker linker : linkers) {
        outLinkedSpans.addAll(linker.find(docText, sentences, tokens, nameSpans));
      }
    }
    return outLinkedSpans;
  }

  /**
   *
   * @param docText       the document text
   * @param sentences     the sentence spans that correspond to the doc text
   * @param tokens        the token spans that correspond to one of the
   *                      sentences
   * @param nameSpans     the name spans that correspond to the tokens
   * @param sentenceIndex the index to the sentence span that the tokens[]
   *                      Span[] corresponds to
   * @param properties    the EntityLinkerProperties file with the proper
   *                      configuration
   * @return
   */
  public ArrayList<LinkedSpan<BaseLink>> getLinkedSpans(String docText, Span[] sentences, Span[] tokens,
          Span[] nameSpans, int sentenceIndex, EntityLinkerProperties properties) {
    ArrayList<LinkedSpan<BaseLink>> outLinkedSpans = new ArrayList<LinkedSpan<BaseLink>>();
    if (nameSpans.length == 0 || nameSpans == null) {
      return outLinkedSpans;
    }
    List<EntityLinker> linkers;
    boolean multiType = isMultitype(nameSpans);
    if (multiType) {
      for (Span s : nameSpans) {
        linkers = getInstances(s.getType(), properties);
        for (EntityLinker linker : linkers) {
          outLinkedSpans.addAll(linker.find(docText, sentences, tokens, nameSpans, sentenceIndex));
        }
      }
    } else {
      linkers = getInstances(nameSpans[0].getType(), properties);
      for (Span s : nameSpans) {
        for (EntityLinker linker : linkers) {
          outLinkedSpans.addAll(linker.find(docText, sentences, tokens, nameSpans, sentenceIndex));
        }
      }
    }
    return outLinkedSpans;
  }

  /**
   *
   * @param docText    the document text
   * @param sentences  the sentence spans that correspond to the doc text
   * @param tokens     the token spans that correspond to one of the sentences
   * @param nameSpans  the name spans that correspond to the tokens
   *
   * @param properties the EntityLinkerProperties file with the proper
   *                   configuration
   * @return
   */
  public ArrayList<LinkedSpan<BaseLink>> getLinkedSpans(String docText, Span[] sentences, Span[] tokens,
          Span[] nameSpans, EntityLinkerProperties properties) {
    ArrayList<LinkedSpan<BaseLink>> outLinkedSpans = new ArrayList<LinkedSpan<BaseLink>>();
    if (nameSpans.length == 0 || nameSpans == null) {
      return outLinkedSpans;
    }
    List<EntityLinker> linkers;
    boolean multiType = isMultitype(nameSpans);
    if (multiType) {
      for (Span s : nameSpans) {
        linkers = getInstances(s.getType(), properties);
        for (EntityLinker linker : linkers) {
          outLinkedSpans.addAll(linker.find(docText, sentences, tokens, nameSpans));
        }
      }
    } else {
      linkers = getInstances(nameSpans[0].getType(), properties);
      for (Span s : nameSpans) {
        for (EntityLinker linker : linkers) {
          outLinkedSpans.addAll(linker.find(docText, sentences, tokens, nameSpans));
        }
      }
    }
    return outLinkedSpans;
  }

  /**
   *
   * @param docText       the document text
   * @param sentences     the sentence spans that correspond to the doc text
   * @param tokens        the token strings that correspond to one of the
   *                      sentences
   * @param nameSpans     the name spans that correspond to the tokens
   * @param sentenceIndex the index to the sentence span that the tokens[]
   *                      Span[] corresponds to
   * @param properties    the EntityLinkerProperties file with the proper
   *                      configuration
   * @return
   */
  public ArrayList<LinkedSpan<BaseLink>> getLinkedSpans(String docText, Span[] sentences, String[] tokens,
          Span[] nameSpans, EntityLinkerProperties properties) {
    ArrayList<LinkedSpan<BaseLink>> outLinkedSpans = new ArrayList<LinkedSpan<BaseLink>>();
    if (nameSpans.length == 0 || nameSpans == null) {
      return outLinkedSpans;
    }
    List<EntityLinker> linkers;
    boolean multiType = isMultitype(nameSpans);
    if (multiType) {
      for (Span s : nameSpans) {
        linkers = getInstances(s.getType(), properties);
        for (EntityLinker linker : linkers) {
          outLinkedSpans.addAll(linker.find(docText, sentences, tokens, nameSpans));
        }
      }
    } else {
      linkers = getInstances(nameSpans[0].getType(), properties);
      for (Span s : nameSpans) {
        for (EntityLinker linker : linkers) {
          outLinkedSpans.addAll(linker.find(docText, sentences, tokens, nameSpans));
        }
      }
    }
    return outLinkedSpans;
  }

  /**
   * checks to see if a list of spans contains more than one type
   *
   * @param spans
   * @return
   */
  private boolean isMultitype(Span[] spans) {
    boolean multitype = false;
    String type = spans[0].getType();
    for (int i = 1; i < spans.length; i++) {
      if (!type.equals(spans[i].getType())) {
        multitype = true;
        break;
      }
    }
    return multitype;
  }

  /**
   * returns instances of entitylinkers, and caches them in a map so they are
   * lazily instantiated
   *
   * @param type       the entitytype
   * @param properties the entity liker properties
   * @return
   */
  private List<EntityLinker> getInstances(String type, EntityLinkerProperties properties) {
    List<EntityLinker> linkers = new ArrayList<EntityLinker>();
    if (linkerMap.containsKey(type)) {
      linkers = linkerMap.get(type);
    } else {
      linkers = EntityLinkerFactory.getLinkers(type, properties);
      linkerMap.put(type, linkers);
    }
    return linkers;
  }
}
