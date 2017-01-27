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

package opennlp.tools.parser.treeinsert;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import opennlp.tools.chunker.Chunker;
import opennlp.tools.chunker.ChunkerME;
import opennlp.tools.chunker.ChunkerModel;
import opennlp.tools.dictionary.Dictionary;
import opennlp.tools.ml.EventTrainer;
import opennlp.tools.ml.TrainerFactory;
import opennlp.tools.ml.model.Event;
import opennlp.tools.ml.model.MaxentModel;
import opennlp.tools.parser.AbstractBottomUpParser;
import opennlp.tools.parser.ChunkSampleStream;
import opennlp.tools.parser.HeadRules;
import opennlp.tools.parser.Parse;
import opennlp.tools.parser.ParserChunkerFactory;
import opennlp.tools.parser.ParserEventTypeEnum;
import opennlp.tools.parser.ParserModel;
import opennlp.tools.parser.ParserType;
import opennlp.tools.parser.PosSampleStream;
import opennlp.tools.postag.POSModel;
import opennlp.tools.postag.POSTagger;
import opennlp.tools.postag.POSTaggerFactory;
import opennlp.tools.postag.POSTaggerME;
import opennlp.tools.util.ObjectStream;
import opennlp.tools.util.TrainingParameters;

/**
 * Built/attach parser.  Nodes are built when their left-most
 * child is encountered.  Subsequent children are attached as
 * daughters.  Attachment is based on node in the right-frontier
 * of the tree.  After each attachment or building, nodes are
 * assesed as either complete or incomplete.  Complete nodes
 * are no longer elligable for daughter attachment.
 * Complex modifiers which produce additional node
 * levels of the same type are attached with sister-adjunction.
 * Attachment can not take place higher in the right-frontier
 * than an incomplete node.
 */
public class Parser extends AbstractBottomUpParser {

  /** Outcome used when a constituent needs an no additional parent node/building. */
  public static final String DONE = "d";

  /** Outcome used when a node should be attached as a sister to another node. */
  public static final String ATTACH_SISTER = "s";
  /** Outcome used when a node should be attached as a daughter to another node. */
  public static final String ATTACH_DAUGHTER = "d";
  /** Outcome used when a node should not be attached to another node. */
  public static final String NON_ATTACH = "n";

  /** Label used to distinguish build nodes from non-built nodes. */
  public static final String BUILT = "built";
  private MaxentModel buildModel;
  private MaxentModel attachModel;
  private MaxentModel checkModel;

  static boolean checkComplete = false;

  private BuildContextGenerator buildContextGenerator;
  private AttachContextGenerator attachContextGenerator;
  private CheckContextGenerator checkContextGenerator;

  private double[] bprobs;
  private double[] aprobs;
  private double[] cprobs;

  private int doneIndex;
  private int sisterAttachIndex;
  private int daughterAttachIndex;
  private int nonAttachIndex;
  private int completeIndex;

  private int[] attachments;

  public Parser(ParserModel model, int beamSize, double advancePercentage) {
    this(model.getBuildModel(), model.getAttachModel(), model.getCheckModel(),
        new POSTaggerME(model.getParserTaggerModel()),
        new ChunkerME(model.getParserChunkerModel()),
        model.getHeadRules(),
        beamSize, advancePercentage);
  }

  public Parser(ParserModel model) {
    this(model, defaultBeamSize, defaultAdvancePercentage);
  }

  private Parser(MaxentModel buildModel, MaxentModel attachModel, MaxentModel checkModel,
                 POSTagger tagger, Chunker chunker, HeadRules headRules, int beamSize,
                 double advancePercentage) {
    super(tagger,chunker,headRules,beamSize,advancePercentage);
    this.buildModel = buildModel;
    this.attachModel = attachModel;
    this.checkModel = checkModel;

    this.buildContextGenerator = new BuildContextGenerator();
    this.attachContextGenerator = new AttachContextGenerator(punctSet);
    this.checkContextGenerator = new CheckContextGenerator(punctSet);

    this.bprobs = new double[buildModel.getNumOutcomes()];
    this.aprobs = new double[attachModel.getNumOutcomes()];
    this.cprobs = new double[checkModel.getNumOutcomes()];

    this.doneIndex = buildModel.getIndex(DONE);
    this.sisterAttachIndex = attachModel.getIndex(ATTACH_SISTER);
    this.daughterAttachIndex = attachModel.getIndex(ATTACH_DAUGHTER);
    this.nonAttachIndex = attachModel.getIndex(NON_ATTACH);
    attachments = new int[] {daughterAttachIndex,sisterAttachIndex};
    this.completeIndex = checkModel.getIndex(Parser.COMPLETE);
  }

  /**
   * Returns the right frontier of the specified parse tree with nodes ordered from deepest
   * to shallowest.
   * @param root The root of the parse tree.
   * @return The right frontier of the specified parse tree.
   */
  public static List<Parse> getRightFrontier(Parse root,Set<String> punctSet) {
    List<Parse> rf = new LinkedList<>();
    Parse top;
    if (AbstractBottomUpParser.TOP_NODE.equals(root.getType()) ||
        AbstractBottomUpParser.INC_NODE.equals(root.getType())) {
      top = collapsePunctuation(root.getChildren(),punctSet)[0];
    }
    else {
      top = root;
    }
    while (!top.isPosTag()) {
      rf.add(0,top);
      Parse[] kids = top.getChildren();
      top = kids[kids.length - 1];
    }
    return new ArrayList<>(rf);
  }

  private void setBuilt(Parse p) {
    String l = p.getLabel();
    if (l == null) {
      p.setLabel(Parser.BUILT);
    }
    else {
      if (isComplete(p)) {
        p.setLabel(Parser.BUILT + "." + Parser.COMPLETE);
      }
      else {
        p.setLabel(Parser.BUILT + "." + Parser.INCOMPLETE);
      }
    }
  }

  private void setComplete(Parse p) {
    String l = p.getLabel();
    if (!isBuilt(p)) {
      p.setLabel(Parser.COMPLETE);
    }
    else {
      p.setLabel(Parser.BUILT + "." + Parser.COMPLETE);
    }
  }

  private void setIncomplete(Parse p) {
    if (!isBuilt(p)) {
      p.setLabel(Parser.INCOMPLETE);
    }
    else {
      p.setLabel(Parser.BUILT + "." + Parser.INCOMPLETE);
    }
  }

  private boolean isBuilt(Parse p) {
    String l = p.getLabel();
    return l != null && l.startsWith(Parser.BUILT);
  }

  private boolean isComplete(Parse p) {
    String l = p.getLabel();
    return l != null && l.endsWith(Parser.COMPLETE);
  }

  @Override
  protected Parse[] advanceChunks(Parse p, double minChunkScore) {
    Parse[] parses = super.advanceChunks(p, minChunkScore);
    for (Parse parse : parses) {
      Parse[] chunks = parse.getChildren();
      for (int ci = 0; ci < chunks.length; ci++) {
        setComplete(chunks[ci]);
      }
    }
    return parses;
  }

  @Override
  protected Parse[] advanceParses(Parse p, double probMass) {
    double q = 1 - probMass;
    /* The index of the node which will be labeled in this iteration of advancing the parse. */
    int advanceNodeIndex;
    /* The node which will be labeled in this iteration of advancing the parse. */
    Parse advanceNode = null;
    Parse[] originalChildren = p.getChildren();
    Parse[] children = collapsePunctuation(originalChildren,punctSet);
    int numNodes = children.length;
    if (numNodes == 0) {
      return null;
    }
    else if (numNodes == 1) {  //put sentence initial and final punct in top node
      if (children[0].isPosTag()) {
        return null;
      }
      else {
        p.expandTopNode(children[0]);
        return new Parse[] { p };
      }
    }
    //determines which node needs to adanced.
    for (advanceNodeIndex = 0; advanceNodeIndex < numNodes; advanceNodeIndex++) {
      advanceNode = children[advanceNodeIndex];
      if (!isBuilt(advanceNode)) {
        break;
      }
    }
    int originalZeroIndex = mapParseIndex(0,children,originalChildren);
    int originalAdvanceIndex = mapParseIndex(advanceNodeIndex,children,originalChildren);
    List<Parse> newParsesList = new ArrayList<>();
    //call build model
    buildModel.eval(buildContextGenerator.getContext(children, advanceNodeIndex), bprobs);
    double doneProb = bprobs[doneIndex];
    if (debugOn)
      System.out.println("adi=" + advanceNodeIndex + " " + advanceNode.getType() + "."
          + advanceNode.getLabel() + " " + advanceNode + " choose build=" + (1 - doneProb)
          + " attach=" + doneProb);

    if (1 - doneProb > q) {
      double bprobSum = 0;
      while (bprobSum < probMass) {
        /* The largest unadvanced labeling. */
        int max = 0;
        for (int pi = 1; pi < bprobs.length; pi++) { //for each build outcome
          if (bprobs[pi] > bprobs[max]) {
            max = pi;
          }
        }
        if (bprobs[max] == 0) {
          break;
        }
        double bprob = bprobs[max];
        bprobs[max] = 0; //zero out so new max can be found
        bprobSum += bprob;
        String tag = buildModel.getOutcome(max);
        if (!tag.equals(DONE)) {
          Parse newParse1 = (Parse) p.clone();
          Parse newNode = new Parse(p.getText(),advanceNode.getSpan(),tag,bprob,advanceNode.getHead());
          newParse1.insert(newNode);
          newParse1.addProb(Math.log(bprob));
          newParsesList.add(newParse1);
          if (checkComplete) {
            cprobs = checkModel.eval(checkContextGenerator.getContext(newNode, children,
                advanceNodeIndex,false));
            if (debugOn) System.out.println("building " + tag + " " + bprob + " c=" + cprobs[completeIndex]);
            if (cprobs[completeIndex] > probMass) { //just complete advances
              setComplete(newNode);
              newParse1.addProb(Math.log(cprobs[completeIndex]));
              if (debugOn) System.out.println("Only advancing complete node");
            }
            else if (1 - cprobs[completeIndex] > probMass) { //just incomplete advances
              setIncomplete(newNode);
              newParse1.addProb(Math.log(1 - cprobs[completeIndex]));
              if (debugOn) System.out.println("Only advancing incomplete node");
            }
            else { //both complete and incomplete advance
              if (debugOn) System.out.println("Advancing both complete and incomplete nodes");
              setComplete(newNode);
              newParse1.addProb(Math.log(cprobs[completeIndex]));

              Parse newParse2 = (Parse) p.clone();
              Parse newNode2 = new Parse(p.getText(),advanceNode.getSpan(),tag,bprob,advanceNode.getHead());
              newParse2.insert(newNode2);
              newParse2.addProb(Math.log(bprob));
              newParsesList.add(newParse2);
              newParse2.addProb(Math.log(1 - cprobs[completeIndex]));
              setIncomplete(newNode2); //set incomplete for non-clone
            }
          }
          else {
            if (debugOn) System.out.println("building " + tag + " " + bprob);
          }
        }
      }
    }
    //advance attaches
    if (doneProb > q) {
      Parse newParse1 = (Parse) p.clone(); //clone parse
      //mark nodes as built
      if (checkComplete) {
        if (isComplete(advanceNode)) {
          //replace constituent being labeled to create new derivation
          newParse1.setChild(originalAdvanceIndex,Parser.BUILT + "." + Parser.COMPLETE);
        }
        else {
          //replace constituent being labeled to create new derivation
          newParse1.setChild(originalAdvanceIndex,Parser.BUILT + "." + Parser.INCOMPLETE);
        }
      }
      else {
        //replace constituent being labeled to create new derivation
        newParse1.setChild(originalAdvanceIndex,Parser.BUILT);
      }
      newParse1.addProb(Math.log(doneProb));
      if (advanceNodeIndex == 0) { //no attach if first node.
        newParsesList.add(newParse1);
      }
      else {
        List<Parse> rf = getRightFrontier(p,punctSet);
        for (int fi = 0,fs = rf.size(); fi < fs; fi++) {
          Parse fn = rf.get(fi);
          attachModel.eval(attachContextGenerator.getContext(children, advanceNodeIndex, rf, fi), aprobs);
          if (debugOn) {
            // List cs = java.util.Arrays.asList(attachContextGenerator.getContext(children,
            //     advanceNodeIndex,rf,fi,punctSet));
            System.out.println("Frontier node(" + fi + "): " + fn.getType() + "." + fn.getLabel()
                + " " + fn + " <- " + advanceNode.getType() + " " + advanceNode + " d="
                + aprobs[daughterAttachIndex] + " s=" + aprobs[sisterAttachIndex] + " ");
          }
          for (int ai = 0; ai < attachments.length; ai++) {
            double prob = aprobs[attachments[ai]];
            //should we try an attach if p > threshold and
            // if !checkComplete then prevent daughter attaching to chunk
            // if checkComplete then prevent daughter attacing to complete node or
            //    sister attaching to an incomplete node
            if (prob > q && (
                (!checkComplete && (attachments[ai] != daughterAttachIndex || !isComplete(fn)))
                ||
                (checkComplete && ((attachments[ai] == daughterAttachIndex && !isComplete(fn))
                    || (attachments[ai] == sisterAttachIndex && isComplete(fn)))))) {
              Parse newParse2 = newParse1.cloneRoot(fn,originalZeroIndex);
              Parse[] newKids = Parser.collapsePunctuation(newParse2.getChildren(),punctSet);
              //remove node from top level since were going to attach it (including punct)
              for (int ri = originalZeroIndex + 1; ri <= originalAdvanceIndex; ri++) {
                //System.out.println(at"-removing "+(originalZeroIndex+1)+" "
                // +newParse2.getChildren()[originalZeroIndex+1]);
                newParse2.remove(originalZeroIndex + 1);
              }
              List<Parse> crf = getRightFrontier(newParse2,punctSet);
              Parse updatedNode;
              if (attachments[ai] == daughterAttachIndex) { //attach daughter
                updatedNode = crf.get(fi);
                updatedNode.add(advanceNode,headRules);
              }
              else { //attach sister
                Parse psite;
                if (fi + 1 < crf.size()) {
                  psite = crf.get(fi + 1);
                  updatedNode = psite.adjoin(advanceNode,headRules);
                }
                else {
                  psite = newParse2;
                  updatedNode = psite.adjoinRoot(advanceNode,headRules,originalZeroIndex);
                  newKids[0] = updatedNode;
                }
              }
              //update spans affected by attachment
              for (int ni = fi + 1; ni < crf.size(); ni++) {
                Parse node = crf.get(ni);
                node.updateSpan();
              }
              //if (debugOn) {System.out.print(ai+"-result: ");newParse2.show();System.out.println();}
              newParse2.addProb(Math.log(prob));
              newParsesList.add(newParse2);
              if (checkComplete) {
                cprobs = checkModel.eval(
                    checkContextGenerator.getContext(updatedNode,newKids,advanceNodeIndex,true));
                if (cprobs[completeIndex] > probMass) {
                  setComplete(updatedNode);
                  newParse2.addProb(Math.log(cprobs[completeIndex]));
                  if (debugOn) System.out.println("Only advancing complete node");
                }
                else if (1 - cprobs[completeIndex] > probMass) {
                  setIncomplete(updatedNode);
                  newParse2.addProb(Math.log(1 - cprobs[completeIndex]));
                  if (debugOn) System.out.println("Only advancing incomplete node");
                }
                else {
                  setComplete(updatedNode);
                  Parse newParse3 = newParse2.cloneRoot(updatedNode,originalZeroIndex);
                  newParse3.addProb(Math.log(cprobs[completeIndex]));
                  newParsesList.add(newParse3);
                  setIncomplete(updatedNode);
                  newParse2.addProb(Math.log(1 - cprobs[completeIndex]));
                  if (debugOn)
                    System.out.println("Advancing both complete and incomplete nodes; c="
                        + cprobs[completeIndex]);
                }
              }
            }
            else {
              if (debugOn)
                System.out.println("Skipping " + fn.getType() + "." + fn.getLabel() + " "
                    + fn + " daughter=" + (attachments[ai] == daughterAttachIndex)
                    + " complete=" + isComplete(fn) + " prob=" + prob);
            }
          }
          if (checkComplete && !isComplete(fn)) {
            if (debugOn)
              System.out.println("Stopping at incomplete node(" + fi + "): "
                  + fn.getType() + "." + fn.getLabel() + " " + fn);
            break;
          }
        }
      }
    }
    Parse[] newParses = new Parse[newParsesList.size()];
    newParsesList.toArray(newParses);
    return newParses;
  }

  @Override
  protected void advanceTop(Parse p) {
    p.setType(TOP_NODE);
  }

  public static ParserModel train(String languageCode,
      ObjectStream<Parse> parseSamples, HeadRules rules, TrainingParameters mlParams)
      throws IOException {

    Map<String, String> manifestInfoEntries = new HashMap<>();

    System.err.println("Building dictionary");
    Dictionary mdict = buildDictionary(parseSamples, rules, mlParams);

    parseSamples.reset();

    // tag
    POSModel posModel = POSTaggerME.train(languageCode, new PosSampleStream(
        parseSamples), mlParams.getParameters("tagger"), new POSTaggerFactory());

    parseSamples.reset();

    // chunk
    ChunkerModel chunkModel = ChunkerME.train(languageCode, new ChunkSampleStream(
        parseSamples), mlParams.getParameters("chunker"), new ParserChunkerFactory());

    parseSamples.reset();

    // build
    System.err.println("Training builder");
    ObjectStream<Event> bes = new ParserEventStream(parseSamples, rules,
        ParserEventTypeEnum.BUILD, mdict);
    Map<String, String> buildReportMap = new HashMap<>();

    EventTrainer buildTrainer = TrainerFactory.getEventTrainer(
        mlParams.getSettings("build"), buildReportMap);
    MaxentModel buildModel = buildTrainer.train(bes);
    opennlp.tools.parser.chunking.Parser.mergeReportIntoManifest(
        manifestInfoEntries, buildReportMap, "build");

    parseSamples.reset();

    // check
    System.err.println("Training checker");
    ObjectStream<Event>  kes = new ParserEventStream(parseSamples, rules,
        ParserEventTypeEnum.CHECK);
    Map<String, String> checkReportMap = new HashMap<>();

    EventTrainer checkTrainer = TrainerFactory.getEventTrainer(
        mlParams.getSettings("check"), checkReportMap);
    MaxentModel checkModel = checkTrainer.train(kes);
    opennlp.tools.parser.chunking.Parser.mergeReportIntoManifest(
        manifestInfoEntries, checkReportMap, "check");

    parseSamples.reset();

    // attach
    System.err.println("Training attacher");
    ObjectStream<Event>  attachEvents = new ParserEventStream(parseSamples, rules,
        ParserEventTypeEnum.ATTACH);
    Map<String, String> attachReportMap = new HashMap<>();
    EventTrainer attachTrainer = TrainerFactory.getEventTrainer(
        mlParams.getSettings("attach"), attachReportMap);
    MaxentModel attachModel = attachTrainer.train(attachEvents);
    opennlp.tools.parser.chunking.Parser.mergeReportIntoManifest(
        manifestInfoEntries, attachReportMap, "attach");

    // TODO: Remove cast for HeadRules
    return new ParserModel(languageCode, buildModel, checkModel,
        attachModel, posModel, chunkModel,
        rules, ParserType.TREEINSERT, manifestInfoEntries);
  }

  public static ParserModel train(String languageCode,
      ObjectStream<Parse> parseSamples, HeadRules rules, int iterations, int cut)
      throws IOException {

    TrainingParameters params = new TrainingParameters();
    params.put("dict", TrainingParameters.CUTOFF_PARAM, Integer.toString(cut));

    params.put("tagger", TrainingParameters.CUTOFF_PARAM, Integer.toString(cut));
    params.put("tagger", TrainingParameters.ITERATIONS_PARAM, Integer.toString(iterations));
    params.put("chunker", TrainingParameters.CUTOFF_PARAM, Integer.toString(cut));
    params.put("chunker", TrainingParameters.ITERATIONS_PARAM, Integer.toString(iterations));
    params.put("check", TrainingParameters.CUTOFF_PARAM, Integer.toString(cut));
    params.put("check", TrainingParameters.ITERATIONS_PARAM, Integer.toString(iterations));
    params.put("build", TrainingParameters.CUTOFF_PARAM, Integer.toString(cut));
    params.put("build", TrainingParameters.ITERATIONS_PARAM, Integer.toString(iterations));

    return train(languageCode, parseSamples, rules, params);
  }

}
