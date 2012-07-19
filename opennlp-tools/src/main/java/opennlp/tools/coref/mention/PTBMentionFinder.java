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

package opennlp.tools.coref.mention;

/**
 * Finds mentions from Penn Treebank style parses.
 */
public class PTBMentionFinder extends AbstractMentionFinder {

  private static PTBMentionFinder instance = null;

  /**
   * Creates a new mention finder with the specified head finder.
   * @param hf The head finder.
   */
  private PTBMentionFinder(HeadFinder hf) {
    collectPrenominalNamedEntities = false;
    collectCoordinatedNounPhrases = true;
    headFinder = hf;
  }

  /**
   * Retrives the one and only existing instance.
   *
   * @param hf
   * @return the one and only existing instance
   */
  public static PTBMentionFinder getInstance(HeadFinder hf) {
    if (instance == null) {
      instance = new PTBMentionFinder(hf);
    }
    else if (instance.headFinder != hf) {
      instance = new PTBMentionFinder(hf);
    }
    return instance;
  }




  /*
  private boolean isTraceNp(Parse np){
    List sc = np.getSyntacticChildren();
    return (sc.size() == 0);
  }

  protected List getNounPhrases(Parse p) {
    List nps = new ArrayList(p.getNounPhrases());
    for (int npi = 0; npi < nps.size(); npi++) {
      Parse np = (Parse) nps.get(npi);
      if (!isTraceNp(np)) {
        if (np.getSyntacticChildren().size()!=0) {
          List snps = np.getNounPhrases();
          for (int snpi=0,snpl=snps.size();snpi<snpl;snpi++) {
            Parse snp = (Parse) snps.get(snpi);
            if (!snp.isParentNAC() && !isTraceNp(snp)) {
              nps.add(snp);
            }
          }
        }
      }
      else {
        nps.remove(npi);
        npi--;
      }
    }
    return (nps);
  }
  */

  /** Moves entity ids assigned to basal nps and possesives to their
   * maximaly containing np.  Also assign head information of basal
   * noun phase to the maximally containing np.
   * @deprecated No on uses this any more.
   *
  private void propigateEntityIds(Map headMap) {
    for (Iterator ki = headMap.keySet().iterator(); ki.hasNext();) {
      Parse np = (Parse) ki.next();
      if (isBasalNounPhrase(np) || isPossessive(np)) {
        int ei = np.getEntityId();
        if (ei != -1) {
          Parse curHead = np;
          Parse newHead = null;
          while ((newHead = (Parse) headMap.get(curHead)) != null) {
            curHead.removeEntityId();
            curHead = newHead;
          }
          curHead.setEntityId(ei);
          curHead.setProperty("head", np.getSpan().toString());
        }
      }
    }
  }
  */
}
