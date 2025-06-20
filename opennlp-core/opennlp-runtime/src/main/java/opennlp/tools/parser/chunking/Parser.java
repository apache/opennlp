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


package opennlp.tools.parser.chunking;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import opennlp.tools.chunker.Chunker;
import opennlp.tools.chunker.ChunkerME;
import opennlp.tools.chunker.ChunkerModel;
import opennlp.tools.dictionary.Dictionary;
import opennlp.tools.ml.BeamSearch;
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
import opennlp.tools.util.Span;
import opennlp.tools.util.TrainingParameters;

/**
 * A shift reduce style {@link opennlp.tools.parser.Parser} implementation
 * based on Adwait Ratnaparkhi's 1998 thesis.
 *
 * @see AbstractBottomUpParser
 * @see opennlp.tools.parser.Parser
 */
public class Parser extends AbstractBottomUpParser {

  private static final Logger logger = LoggerFactory.getLogger(Parser.class);
  private final MaxentModel buildModel;
  private final MaxentModel checkModel;

  private final BuildContextGenerator buildContextGenerator;
  private final CheckContextGenerator checkContextGenerator;

  private final double[] bprobs;
  private final double[] cprobs;

  private static final String TOP_START = START + TOP_NODE;
  private final int topStartIndex;
  private final Map<String, String> startTypeMap;
  private final Map<String, String> contTypeMap;

  private final int completeIndex;
  private final int incompleteIndex;

  /**
   * Instantiates a {@link Parser} via a given {@code model} and
   * other configuration parameters. Uses the default implementations of
   * {@link POSTaggerME} and {@link ChunkerME}.
   *
   * @param model             The {@link ParserModel} to use.
   * @param beamSize          The number of different parses kept during parsing.
   * @param advancePercentage The minimal amount of probability mass which advanced outcomes
   *                          must represent. Only outcomes which contribute to the top
   *                          {@code advancePercentage} will be explored.
   * @throws IllegalStateException Thrown if the {@link ParserType} is not supported.
   * @see ParserModel
   * @see POSTaggerME
   * @see ChunkerME
   */
  public Parser(ParserModel model, int beamSize, double advancePercentage) {
    this(model.getBuildModel(), model.getCheckModel(),
        new POSTaggerME(model.getParserTaggerModel()),
        new ChunkerME(model.getParserChunkerModel()),
        model.getHeadRules(), beamSize, advancePercentage);
  }

  /**
   * Instantiates a {@link Parser} via a given {@code model}.
   * Uses the default implementations of {@link POSTaggerME} and {@link ChunkerME}
   * and default values for {@code beamSize} and {@code advancePercentage}.
   *
   * @param model The {@link ParserModel} to use.
   * @throws IllegalStateException Thrown if the {@link ParserType} is not supported.
   * @see ParserModel
   * @see POSTaggerME
   * @see ChunkerME
   */
  public Parser(ParserModel model) {
    this(model, defaultBeamSize, defaultAdvancePercentage);
  }

  /**
   * Instantiates a {@link Parser} via a given {@code model} and other configuration parameters.
   *
   * @param buildModel        A valid {@link MaxentModel} used to build.
   * @param checkModel        A valid {@link MaxentModel} used to check.
   * @param tagger            A valid {@link POSModel} used to tag.
   * @param chunker           A valid {@link ChunkerModel} used to chunk.
   * @param headRules         The {@link HeadRules} for head word percolation.
   * @param beamSize          The number of different parses kept during parsing.
   * @param advancePercentage The minimal amount of probability mass which advanced outcomes
   *                          must represent. Only outcomes which contribute to the top
   *                          {@code advancePercentage} will be explored.
   * @see POSTagger
   * @see Chunker
   */
  private Parser(MaxentModel buildModel, MaxentModel checkModel, POSTagger tagger, Chunker chunker,
                 HeadRules headRules, int beamSize, double advancePercentage) {
    super(tagger, chunker, headRules, beamSize, advancePercentage);
    this.buildModel = buildModel;
    this.checkModel = checkModel;
    bprobs = new double[buildModel.getNumOutcomes()];
    cprobs = new double[checkModel.getNumOutcomes()];
    this.buildContextGenerator = new BuildContextGenerator();
    this.checkContextGenerator = new CheckContextGenerator();
    startTypeMap = new HashMap<>();
    contTypeMap = new HashMap<>();
    for (int boi = 0, bon = buildModel.getNumOutcomes(); boi < bon; boi++) {
      String outcome = buildModel.getOutcome(boi);
      if (outcome.startsWith(START)) {
        if (logger.isTraceEnabled()) {
          logger.trace("startMap {} -> {} ", outcome, outcome.substring(START.length()));
        }
        startTypeMap.put(outcome, outcome.substring(START.length()));
      } else if (outcome.startsWith(CONT)) {
        if (logger.isTraceEnabled()) {
          logger.trace("contMap {} -> {}", outcome, outcome.substring(CONT.length()));
        }
        contTypeMap.put(outcome, outcome.substring(CONT.length()));
      }
    }
    topStartIndex = buildModel.getIndex(TOP_START);
    completeIndex = checkModel.getIndex(COMPLETE);
    incompleteIndex = checkModel.getIndex(INCOMPLETE);
  }

  @Override
  protected void advanceTop(Parse p) {
    buildModel.eval(buildContextGenerator.getContext(p.getChildren(), 0), bprobs);
    p.addProb(StrictMath.log(bprobs[topStartIndex]));
    checkModel.eval(checkContextGenerator.getContext(p.getChildren(), TOP_NODE, 0, 0), cprobs);
    p.addProb(StrictMath.log(cprobs[completeIndex]));
    p.setType(TOP_NODE);
  }

  @Override
  protected Parse[] advanceParses(final Parse p, double probMass) {
    double q = 1 - probMass;
    /* The closest previous node which has been labeled as a start node. */
    Parse lastStartNode = null;
    /* The index of the closest previous node which has been labeled as a start node. */
    int lastStartIndex = -1;
    /* The type of the closest previous node which has been labeled as a start node. */
    String lastStartType = null;
    /* The index of the node which will be labeled in this iteration of advancing the parse. */
    int advanceNodeIndex;
    /* The node which will be labeled in this iteration of advancing the parse. */
    Parse advanceNode = null;
    Parse[] originalChildren = p.getChildren();
    Parse[] children = collapsePunctuation(originalChildren, punctSet);
    int numNodes = children.length;
    if (numNodes == 0) {
      return null;
    }
    //determines which node needs to be labeled and prior labels.
    for (advanceNodeIndex = 0; advanceNodeIndex < numNodes; advanceNodeIndex++) {
      advanceNode = children[advanceNodeIndex];
      if (advanceNode.getLabel() == null) {
        break;
      } else if (startTypeMap.containsKey(advanceNode.getLabel())) {
        lastStartType = startTypeMap.get(advanceNode.getLabel());
        lastStartNode = advanceNode;
        lastStartIndex = advanceNodeIndex;
        if (logger.isTraceEnabled()) {
          logger.trace("lastStart {} {} {}", lastStartIndex,
              lastStartNode.getLabel(), lastStartNode.getProb());
        }
      }
    }
    int originalAdvanceIndex = mapParseIndex(advanceNodeIndex, children, originalChildren);
    List<Parse> newParsesList = new ArrayList<>(buildModel.getNumOutcomes());
    //call build
    buildModel.eval(buildContextGenerator.getContext(children, advanceNodeIndex), bprobs);
    double bprobSum = 0;
    while (bprobSum < probMass) {
      // The largest unadvanced labeling.
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
      if (logger.isTraceEnabled()) {
        logger.trace("trying {} {} list={}", tag, bprobSum, tag);
      }
      if (max == topStartIndex) { // can't have top until complete
        continue;
      }
      if (logger.isTraceEnabled()) {
        logger.trace("{} {}", tag, bprob);
      }
      if (startTypeMap.containsKey(tag)) { //update last start
        lastStartIndex = advanceNodeIndex;
        lastStartNode = advanceNode;
        lastStartType = startTypeMap.get(tag);
      } else if (contTypeMap.containsKey(tag)) {
        if (lastStartNode == null || !lastStartType.equals(contTypeMap.get(tag))) {
          continue; //Cont must match previous start or continue
        }
      }
      Parse newParse1 = (Parse) p.clone(); //clone parse
      if (createDerivationString) {
        newParse1.getDerivation().append(max).append("-");
      }
      //replace constituent being labeled to create new derivation
      newParse1.setChild(originalAdvanceIndex, tag);
      newParse1.addProb(StrictMath.log(bprob));
      //check
      checkModel.eval(checkContextGenerator.getContext(
          collapsePunctuation(newParse1.getChildren(), punctSet), lastStartType, lastStartIndex,
          advanceNodeIndex), cprobs);
      if (logger.isTraceEnabled()) {
        String[] context = checkContextGenerator.getContext(newParse1.getChildren(),
            lastStartType, lastStartIndex, advanceNodeIndex);
        logger.trace("check {} {} {} {} {}", lastStartType, cprobs[completeIndex],
            cprobs[incompleteIndex], tag, java.util.Arrays.asList(context));
      }

      Parse newParse2;
      if (cprobs[completeIndex] > q) { //make sure a reduce is likely
        newParse2 = (Parse) newParse1.clone();
        if (createDerivationString) {
          newParse2.getDerivation().append(1).append(".");
        }
        newParse2.addProb(StrictMath.log(cprobs[completeIndex]));
        Parse[] cons = new Parse[advanceNodeIndex - lastStartIndex + 1];
        boolean flat = true;
        //first
        cons[0] = lastStartNode;
        flat &= cons[0].isPosTag();
        //last
        cons[advanceNodeIndex - lastStartIndex] = advanceNode;
        flat &= cons[advanceNodeIndex - lastStartIndex].isPosTag();
        //middle
        for (int ci = 1; ci < advanceNodeIndex - lastStartIndex; ci++) {
          cons[ci] = children[ci + lastStartIndex];
          flat &= cons[ci].isPosTag();
        }
        if (!flat) { //flat chunks are done by chunker
          //check for top node to include end and begining punctuation
          if (lastStartIndex == 0 && advanceNodeIndex == numNodes - 1) {
            if (logger.isTraceEnabled()) {
              logger.trace("ParserME.advanceParses: reducing entire span: {} {} {}",
                  new Span(lastStartNode.getSpan().getStart(), advanceNode.getSpan().getEnd()),
                  lastStartType, java.util.Arrays.asList(children));
            }

            newParse2.insert(new Parse(p.getText(), p.getSpan(), lastStartType, cprobs[1],
                headRules.getHead(cons, lastStartType)));
          } else {
            newParse2.insert(new Parse(p.getText(), new Span(lastStartNode.getSpan().getStart(),
                advanceNode.getSpan().getEnd()), lastStartType, cprobs[1],
                headRules.getHead(cons, lastStartType)));
          }
          newParsesList.add(newParse2);
        }
      }
      if (cprobs[incompleteIndex] > q) { //make sure a shift is likely
        if (createDerivationString) {
          newParse1.getDerivation().append(0).append(".");
        }
        if (advanceNodeIndex != numNodes - 1) { //can't shift last element
          newParse1.addProb(StrictMath.log(cprobs[incompleteIndex]));
          newParsesList.add(newParse1);
        }
      }
    }
    Parse[] newParses = new Parse[newParsesList.size()];
    newParsesList.toArray(newParses);
    return newParses;
  }

  public static void mergeReportIntoManifest(Map<String, String> manifest,
                                             Map<String, String> report, String namespace) {

    for (Map.Entry<String, String> entry : report.entrySet()) {
      manifest.put(namespace + "." + entry.getKey(), entry.getValue());
    }
  }

  /**
   * Starts a training of a {@link ParserModel}.
   *
   * @param languageCode An ISO conform language code.
   * @param parseSamples The {@link ObjectStream<Parse> samples} as input.
   * @param rules        The {@link HeadRules} to use.
   * @param mlParams     The {@link TrainingParameters parameters} for training.
   * @return A valid {@link ParserModel}.
   * @throws IOException Thrown if IO errors occurred during training.
   */
  public static ParserModel train(String languageCode, ObjectStream<Parse> parseSamples,
                                  HeadRules rules, TrainingParameters mlParams)
      throws IOException {

    logger.info("Building dictionary");

    Dictionary mdict = buildDictionary(parseSamples, rules, mlParams);

    parseSamples.reset();

    Map<String, String> manifestInfoEntries = new HashMap<>();

    // build
    logger.info("Training builder");
    ObjectStream<Event> bes = new ParserEventStream(parseSamples, rules, ParserEventTypeEnum.BUILD, mdict);
    Map<String, String> buildReportMap = new HashMap<>();
    EventTrainer<TrainingParameters> buildTrainer =
        TrainerFactory.getEventTrainer(mlParams.getParameters("build"), buildReportMap);
    MaxentModel buildModel = buildTrainer.train(bes);
    mergeReportIntoManifest(manifestInfoEntries, buildReportMap, "build");

    parseSamples.reset();

    // tag
    TrainingParameters posTaggerParams = mlParams.getParameters("tagger");

    if (!posTaggerParams.getObjectSettings().containsKey(BeamSearch.BEAM_SIZE_PARAMETER)) {
      mlParams.put("tagger", BeamSearch.BEAM_SIZE_PARAMETER, 10);
    }

    POSModel posModel = POSTaggerME.train(languageCode, new PosSampleStream(parseSamples),
        mlParams.getParameters("tagger"), new POSTaggerFactory());
    parseSamples.reset();

    // chunk
    ChunkerModel chunkModel = ChunkerME.train(languageCode,
        new ChunkSampleStream(parseSamples), mlParams.getParameters("chunker"), new ParserChunkerFactory());
    parseSamples.reset();

    // check
    logger.info("Training checker");
    ObjectStream<Event> kes = new ParserEventStream(parseSamples, rules, ParserEventTypeEnum.CHECK);
    Map<String, String> checkReportMap = new HashMap<>();
    EventTrainer<TrainingParameters> checkTrainer =
        TrainerFactory.getEventTrainer(mlParams.getParameters("check"), checkReportMap);
    MaxentModel checkModel = checkTrainer.train(kes);
    mergeReportIntoManifest(manifestInfoEntries, checkReportMap, "check");

    return new ParserModel(languageCode, buildModel, checkModel,
        posModel, chunkModel, rules,
        ParserType.CHUNKING, manifestInfoEntries);
  }
}
