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

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

import opennlp.tools.chunker.Chunker;
import opennlp.tools.dictionary.Dictionary;
import opennlp.tools.ngram.NGramModel;
import opennlp.tools.parser.chunking.ParserEventStream;
import opennlp.tools.postag.POSTagger;
import opennlp.tools.util.ObjectStream;
import opennlp.tools.util.Sequence;
import opennlp.tools.util.Span;
import opennlp.tools.util.StringList;
import opennlp.tools.util.TrainingParameters;

/**
 * Abstract class which contains code to tag and chunk parses for bottom up parsing and
 * leaves implementation of advancing parses and completing parses to extend class.
 * <p>
 * <b>Note:</b> <br> The nodes within the returned parses are shared with other parses
 * and therefore their parent node references will not be consistent with their child
 * node reference.  {@link #setParents setParents} can be used to make the parents consistent
 * with a particular parse, but subsequent calls to <code>setParents</code> can invalidate
 * the results of earlier calls.<br>
 */
public abstract class AbstractBottomUpParser implements Parser {

  /**
   * The maximum number of parses advanced from all preceding
   * parses at each derivation step.
   */
  protected int M;

  /**
   * The maximum number of parses to advance from a single preceding parse.
   */
  protected int K;

  /**
   * The minimum total probability mass of advanced outcomes.
   */
  protected double Q;

  /**
   * The default beam size used if no beam size is given.
   */
  public static final int defaultBeamSize = 20;

  /**
   * The default amount of probability mass required of advanced outcomes.
   */
  public static final double defaultAdvancePercentage = 0.95;

  /**
   * Completed parses.
   */
  private SortedSet<Parse> completeParses;

  /**
   * Incomplete parses which will be advanced.
   */
  private SortedSet<Parse> odh;

  /**
   * Incomplete parses which have been advanced.
   */
  private SortedSet<Parse> ndh;

  /**
   * The head rules for the parser.
   */
  protected HeadRules headRules;

  /**
   * The set strings which are considered punctuation for the parser.
   * Punctuation is not attached, but floats to the top of the parse as attachment
   * decisions are made about its non-punctuation sister nodes.
   */
  protected Set<String> punctSet;

  /**
   * The label for the top node.
   */
  public static final String TOP_NODE = "TOP";

  /**
   * The label for the top if an incomplete node.
   */
  public static final String INC_NODE = "INC";

  /**
   * The label for a token node.
   */
  public static final String TOK_NODE = "TK";

  /**
   * The integer 0.
   */
  public static final Integer ZERO = 0;

  /**
   * Prefix for outcomes starting a constituent.
   */
  public static final String START = "S-";

  /**
   * Prefix for outcomes continuing a constituent.
   */
  public static final String CONT = "C-";

  /**
   * Outcome for token which is not contained in a basal constituent.
   */
  public static final String OTHER = "O";

  /**
   * Outcome used when a constituent is complete.
   */
  public static final String COMPLETE = "c";

  /**
   * Outcome used when a constituent is incomplete.
   */
  public static final String INCOMPLETE = "i";

  /**
   * The pos-tagger that the parser uses.
   */
  protected POSTagger tagger;

  /**
   * The chunker that the parser uses to chunk non-recursive structures.
   */
  protected Chunker chunker;

  /**
   * Specifies whether failed parses should be reported to standard error.
   */
  protected boolean reportFailedParse;

  /**
   * Specifies whether a derivation string should be created during parsing.
   * This is useful for debugging.
   */
  protected boolean createDerivationString = false;

  /**
   * Turns debug print on or off.
   */
  protected boolean debugOn = false;

  public AbstractBottomUpParser(POSTagger tagger, Chunker chunker, HeadRules headRules,
      int beamSize, double advancePercentage) {
    this.tagger = tagger;
    this.chunker = chunker;
    this.M = beamSize;
    this.K = beamSize;
    this.Q = advancePercentage;
    reportFailedParse = true;
    this.headRules = headRules;
    this.punctSet = headRules.getPunctuationTags();
    odh = new TreeSet<>();
    ndh = new TreeSet<>();
    completeParses = new TreeSet<>();
  }

  /**
   * Specifies whether the parser should report when it was unable to find a parse for
   * a particular sentence.
   * @param errorReporting If true then un-parsed sentences are reported, false otherwise.
   */
  public void setErrorReporting(boolean errorReporting) {
    this.reportFailedParse = errorReporting;
  }

  /**
   * Assigns parent references for the specified parse so that they
   * are consistent with the children references.
   * @param p The parse whose parent references need to be assigned.
   */
  public static void setParents(Parse p) {
    Parse[] children = p.getChildren();
    for (int ci = 0; ci < children.length; ci++) {
      children[ci].setParent(p);
      setParents(children[ci]);
    }
  }

  /**
   * Removes the punctuation from the specified set of chunks, adds it to the parses
   * adjacent to the punctuation is specified, and returns a new array of parses with
   * the punctuation removed.
   *
   * @param chunks A set of parses.
   * @param punctSet The set of punctuation which is to be removed.
   * @return An array of parses which is a subset of chunks with punctuation removed.
   */
  public static Parse[] collapsePunctuation(Parse[] chunks, Set<String> punctSet) {
    List<Parse> collapsedParses = new ArrayList<>(chunks.length);
    int lastNonPunct = -1;
    int nextNonPunct;
    for (int ci = 0, cn = chunks.length; ci < cn; ci++) {
      if (punctSet.contains(chunks[ci].getType())) {
        if (lastNonPunct >= 0) {
          chunks[lastNonPunct].addNextPunctuation(chunks[ci]);
        }
        for (nextNonPunct = ci + 1; nextNonPunct < cn; nextNonPunct++) {
          if (!punctSet.contains(chunks[nextNonPunct].getType())) {
            break;
          }
        }
        if (nextNonPunct < cn) {
          chunks[nextNonPunct].addPreviousPunctuation(chunks[ci]);
        }
      }
      else {
        collapsedParses.add(chunks[ci]);
        lastNonPunct = ci;
      }
    }
    if (collapsedParses.size() == chunks.length) {
      return chunks;
    }
    //System.err.println("collapsedPunctuation: collapsedParses"+collapsedParses);
    return collapsedParses.toArray(new Parse[collapsedParses.size()]);
  }



  /**
   * Advances the specified parse and returns the an array advanced parses whose
   * probability accounts for more than the specified amount of probability mass.
   *
   * @param p The parse to advance.
   * @param probMass The amount of probability mass that should be accounted for
   *                 by the advanced parses.
   */
  protected abstract Parse[] advanceParses(final Parse p, double probMass);

  /**
   * Adds the "TOP" node to the specified parse.
   * @param p The complete parse.
   */
  protected abstract void advanceTop(Parse p);

  public Parse[] parse(Parse tokens, int numParses) {
    if (createDerivationString) tokens.setDerivation(new StringBuffer(100));
    odh.clear();
    ndh.clear();
    completeParses.clear();
    int derivationStage = 0; //derivation length
    int maxDerivationLength = 2 * tokens.getChildCount() + 3;
    odh.add(tokens);
    Parse guess = null;
    double minComplete = 2;
    double bestComplete = -100000; //approximating -infinity/0 in ln domain
    while (odh.size() > 0 && (completeParses.size() < M || (odh.first()).getProb() < minComplete)
        && derivationStage < maxDerivationLength) {
      ndh = new TreeSet<>();

      int derivationRank = 0;
      for (Iterator<Parse> pi = odh.iterator(); pi.hasNext()
          && derivationRank < K; derivationRank++) { // foreach derivation
        Parse tp = pi.next();
        //TODO: Need to look at this for K-best parsing cases
        /*
         //this parse and the ones which follow will never win, stop advancing.
         if (tp.getProb() < bestComplete) {
         break;
         }
         */
        if (guess == null && derivationStage == 2) {
          guess = tp;
        }
        if (debugOn) {
          System.out.print(derivationStage + " " + derivationRank + " " + tp.getProb());
          tp.show();
          System.out.println();
        }
        Parse[] nd;
        if (0 == derivationStage) {
          nd = advanceTags(tp);
        }
        else if (1 == derivationStage) {
          if (ndh.size() < K) {
            //System.err.println("advancing ts "+j+" "+ndh.size()+" < "+K);
            nd = advanceChunks(tp,bestComplete);
          }
          else {
            //System.err.println("advancing ts "+j+" prob="+((Parse) ndh.last()).getProb());
            nd = advanceChunks(tp,(ndh.last()).getProb());
          }
        }
        else { // i > 1
          nd = advanceParses(tp, Q);
        }
        if (nd != null) {
          for (int k = 0, kl = nd.length; k < kl; k++) {
            if (nd[k].complete()) {
              advanceTop(nd[k]);
              if (nd[k].getProb() > bestComplete) {
                bestComplete = nd[k].getProb();
              }
              if (nd[k].getProb() < minComplete) {
                minComplete = nd[k].getProb();
              }
              completeParses.add(nd[k]);
            }
            else {
              ndh.add(nd[k]);
            }
          }
        }
        else {
          //if (reportFailedParse) {
          //  System.err.println("Couldn't advance parse " + derivationStage
          //      + " stage " + derivationRank + "!\n");
          //}
          advanceTop(tp);
          completeParses.add(tp);
        }
      }
      derivationStage++;
      odh = ndh;
    }
    if (completeParses.size() == 0) {
      // if (reportFailedParse) System.err.println("Couldn't find parse for: " + tokens);
      //Parse r = (Parse) odh.first();
      //r.show();
      //System.out.println();
      return new Parse[] {guess};
    }
    else if (numParses == 1) {
      return new Parse[] {completeParses.first()};
    }
    else {
      List<Parse> topParses = new ArrayList<>(numParses);
      while (!completeParses.isEmpty() && topParses.size() < numParses) {
        Parse tp = completeParses.last();
        completeParses.remove(tp);
        topParses.add(tp);
        //parses.remove(tp);
      }
      return topParses.toArray(new Parse[topParses.size()]);
    }
  }

  public Parse parse(Parse tokens) {

    if (tokens.getChildCount() > 0) {
      Parse p = parse(tokens,1)[0];
      setParents(p);
      return p;
    }
    else {
      return tokens;
    }
  }

  /**
   * Returns the top chunk sequences for the specified parse.
   * @param p A pos-tag assigned parse.
   * @param minChunkScore A minimum score below which chunks should not be advanced.
   * @return The top chunk assignments to the specified parse.
   */
  protected Parse[] advanceChunks(final Parse p, double minChunkScore) {
    // chunk
    Parse[] children = p.getChildren();
    String[] words = new String[children.length];
    String[] ptags = new String[words.length];
    double[] probs = new double[words.length];

    for (int i = 0, il = children.length; i < il; i++) {
      Parse sp = children[i];
      words[i] = sp.getHead().getCoveredText();
      ptags[i] = sp.getType();
    }
    //System.err.println("adjusted mcs = "+(minChunkScore-p.getProb()));
    Sequence[] cs = chunker.topKSequences(words, ptags,minChunkScore - p.getProb());
    Parse[] newParses = new Parse[cs.length];
    for (int si = 0, sl = cs.length; si < sl; si++) {
      newParses[si] = (Parse) p.clone(); //copies top level
      if (createDerivationString) newParses[si].getDerivation().append(si).append(".");
      String[] tags = cs[si].getOutcomes().toArray(new String[words.length]);
      cs[si].getProbs(probs);
      int start = -1;
      int end = 0;
      String type = null;
      //System.err.print("sequence "+si+" ");
      for (int j = 0; j <= tags.length; j++) {
        // if (j != tags.length) {System.err.println(words[j]+" "
        // +ptags[j]+" "+tags[j]+" "+probs.get(j));}
        if (j != tags.length) {
          newParses[si].addProb(Math.log(probs[j]));
        }
        // if continue just update end chunking tag don't use contTypeMap
        if (j != tags.length && tags[j].startsWith(CONT)) {
          end = j;
        }
        else { //make previous constituent if it exists
          if (type != null) {
            //System.err.println("inserting tag "+tags[j]);
            Parse p1 = p.getChildren()[start];
            Parse p2 = p.getChildren()[end];
            // System.err.println("Putting "+type+" at "+start+","+end+" for "
            // +j+" "+newParses[si].getProb());
            Parse[] cons = new Parse[end - start + 1];
            cons[0] = p1;
            //cons[0].label="Start-"+type;
            if (end - start != 0) {
              cons[end - start] = p2;
              //cons[end-start].label="Cont-"+type;
              for (int ci = 1; ci < end - start; ci++) {
                cons[ci] = p.getChildren()[ci + start];
                //cons[ci].label="Cont-"+type;
              }
            }
            Parse chunk = new Parse(p1.getText(), new Span(p1.getSpan().getStart(),
                p2.getSpan().getEnd()), type, 1, headRules.getHead(cons, type));
            chunk.isChunk(true);
            newParses[si].insert(chunk);
          }
          if (j != tags.length) { //update for new constituent
            if (tags[j].startsWith(START)) { // don't use startTypeMap these are chunk tags
              type = tags[j].substring(START.length());
              start = j;
              end = j;
            }
            else { // other
              type = null;
            }
          }
        }
      }
      //newParses[si].show();System.out.println();
    }
    return newParses;
  }

  /**
   * Advances the parse by assigning it POS tags and returns multiple tag sequences.
   * @param p The parse to be tagged.
   * @return Parses with different POS-tag sequence assignments.
   */
  protected Parse[] advanceTags(final Parse p) {
    Parse[] children = p.getChildren();
    String[] words = new String[children.length];
    double[] probs = new double[words.length];
    for (int i = 0,il = children.length; i < il; i++) {
      words[i] = children[i].getCoveredText();
    }
    Sequence[] ts = tagger.topKSequences(words);
    Parse[] newParses = new Parse[ts.length];
    for (int i = 0; i < ts.length; i++) {
      String[] tags = ts[i].getOutcomes().toArray(new String[words.length]);
      ts[i].getProbs(probs);
      newParses[i] = (Parse) p.clone(); //copies top level
      if (createDerivationString) newParses[i].getDerivation().append(i).append(".");
      for (int j = 0; j < words.length; j++) {
        Parse word = children[j];
        //System.err.println("inserting tag "+tags[j]);
        double prob = probs[j];
        newParses[i].insert(new Parse(word.getText(), word.getSpan(), tags[j], prob,j));
        newParses[i].addProb(Math.log(prob));
      }
    }
    return newParses;
  }

  /**
   * Determines the mapping between the specified index into the specified parses without punctuation to
   * the corresponding index into the specified parses.
   * @param index An index into the parses without punctuation.
   * @param nonPunctParses The parses without punctuation.
   * @param parses The parses wit punctuation.
   * @return An index into the specified parses which corresponds to the same node the specified index
   *     into the parses with punctuation.
   */
  protected int mapParseIndex(int index, Parse[] nonPunctParses, Parse[] parses) {
    int parseIndex = index;
    while (parses[parseIndex] != nonPunctParses[index]) {
      parseIndex++;
    }
    return parseIndex;
  }

  private static boolean lastChild(Parse child, Parse parent, Set<String> punctSet) {
    if (parent == null) {
      return false;
    }

    Parse[] kids = collapsePunctuation(parent.getChildren(), punctSet);
    return (kids[kids.length - 1] == child);
  }

  /**
   * Creates a n-gram dictionary from the specified data stream using the specified
   * head rule and specified cut-off.
   *
   * @param data The data stream of parses.
   * @param rules The head rules for the parses.
   * @param params can contain a cutoff, the minimum number of entries required for the
   *        n-gram to be saved as part of the dictionary.
   * @return A dictionary object.
   */
  public static Dictionary buildDictionary(ObjectStream<Parse> data, HeadRules rules,
      TrainingParameters params) throws IOException {

    int cutoff = params.getIntParameter("dict", TrainingParameters.CUTOFF_PARAM, 5);

    NGramModel mdict = new NGramModel();
    Parse p;
    while ((p = data.read()) != null) {
      p.updateHeads(rules);
      Parse[] pwords = p.getTagNodes();
      String[] words = new String[pwords.length];
      //add all uni-grams
      for (int wi = 0;wi < words.length; wi++) {
        words[wi] = pwords[wi].getCoveredText();
      }

      mdict.add(new StringList(words), 1, 1);
      //add tri-grams and bi-grams for inital sequence
      Parse[] chunks = collapsePunctuation(ParserEventStream.getInitialChunks(p),
          rules.getPunctuationTags());
      String[] cwords = new String[chunks.length];
      for (int wi = 0; wi < cwords.length; wi++) {
        cwords[wi] = chunks[wi].getHead().getCoveredText();
      }
      mdict.add(new StringList(cwords), 2, 3);

      //emulate reductions to produce additional n-grams
      int ci = 0;
      while (ci < chunks.length) {
        // System.err.println("chunks["+ci+"]="+chunks[ci].getHead().getCoveredText()
        // +" chunks.length="+chunks.length + "  " + chunks[ci].getParent());

        if (chunks[ci].getParent() == null) {
          chunks[ci].show();
        }
        if (lastChild(chunks[ci], chunks[ci].getParent(),rules.getPunctuationTags())) {
          //perform reduce
          int reduceStart = ci;
          while (reduceStart >= 0 && chunks[reduceStart].getParent() == chunks[ci].getParent()) {
            reduceStart--;
          }
          reduceStart++;
          chunks = ParserEventStream.reduceChunks(chunks,ci,chunks[ci].getParent());
          ci = reduceStart;
          if (chunks.length != 0) {
            String[] window = new String[5];
            int wi = 0;
            if (ci - 2 >= 0) window[wi++] = chunks[ci - 2].getHead().getCoveredText();
            if (ci - 1 >= 0) window[wi++] = chunks[ci - 1].getHead().getCoveredText();
            window[wi++] = chunks[ci].getHead().getCoveredText();
            if (ci + 1 < chunks.length) window[wi++] = chunks[ci + 1].getHead().getCoveredText();
            if (ci + 2 < chunks.length) window[wi++] = chunks[ci + 2].getHead().getCoveredText();
            if (wi < 5) {
              String[] subWindow = new String[wi];
              System.arraycopy(window, 0, subWindow, 0, wi);
              window = subWindow;
            }
            if (window.length >= 3) {
              mdict.add(new StringList(window), 2, 3);
            }
            else if (window.length == 2) {
              mdict.add(new StringList(window), 2, 2);
            }
          }
          ci = reduceStart - 1; //ci will be incremented at end of loop
        }
        ci++;
      }
    }
    //System.err.println("gas,and="+mdict.getCount((new TokenList(new String[] {"gas","and"}))));
    mdict.cutoff(cutoff, Integer.MAX_VALUE);
    return mdict.toDictionary(true);
  }

  /**
   * Creates a n-gram dictionary from the specified data stream using the specified
   * head rule and specified cut-off.
   *
   * @param data The data stream of parses.
   * @param rules The head rules for the parses.
   * @param cutoff The minimum number of entries required for the n-gram to be
   *               saved as part of the dictionary.
   * @return A dictionary object.
   */
  public static Dictionary buildDictionary(ObjectStream<Parse> data, HeadRules rules, int cutoff)
      throws IOException {

    TrainingParameters params = new TrainingParameters();
    params.put("dict", TrainingParameters.CUTOFF_PARAM, cutoff);

    return buildDictionary(data, rules, params);
  }
}
