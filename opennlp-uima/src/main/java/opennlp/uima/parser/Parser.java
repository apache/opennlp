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

package opennlp.uima.parser;

import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.apache.uima.UimaContext;
import org.apache.uima.analysis_component.CasAnnotator_ImplBase;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.cas.ArrayFS;
import org.apache.uima.cas.CAS;
import org.apache.uima.cas.FSIndex;
import org.apache.uima.cas.Feature;
import org.apache.uima.cas.Type;
import org.apache.uima.cas.TypeSystem;
import org.apache.uima.cas.text.AnnotationFS;
import org.apache.uima.resource.ResourceAccessException;
import org.apache.uima.resource.ResourceInitializationException;
import org.apache.uima.util.Level;
import org.apache.uima.util.Logger;

import opennlp.tools.parser.Parse;
import opennlp.tools.parser.ParserFactory;
import opennlp.tools.parser.ParserModel;
import opennlp.tools.util.Span;
import opennlp.uima.util.AnnotatorUtil;
import opennlp.uima.util.ContainingConstraint;
import opennlp.uima.util.UimaUtil;

/**
 * Abstract base class for OpenNLP Parser annotators.
 * <p>
 * Mandatory parameters
 * <table border=1>
 * <caption></caption>
 * <tr><th>Type</th> <th>Name</th> <th>Description</th></tr>
 * <tr><td>String</td> <td>opennlp.uima.ModelName</td> <td>The name of the model file</td></tr>
 * <tr><td>String</td> <td>opennlp.uima.SentenceType</td> <td>The full name of the sentence type</td></tr>
 * <tr><td>String</td> <td>opennlp.uima.TokenType</td> <td>The full name of the token type</td></tr>
 * <tr><td>String</td> <td>opennlp.uima.ParseType</td> <td>The full name of the parse type</td></tr>
 * <tr><td>String</td> <td>opennlp.uima.TypeFeature</td> <td>The name of the type feature</td></tr>
 * </table>
 * <p>
 * Optional parameters
 * <table border=1>
 * <caption></caption>
 * <tr><th>Type</th> <th>Name</th> <th>Description</th></tr>
 * <tr><td>Integer</td> <td>opennlp.uima.BeamSize</td></tr>
 * </table>
 */
public class Parser extends CasAnnotator_ImplBase {

  public static final String PARSE_TYPE_PARAMETER = "opennlp.uima.ParseType";
  public static final String TYPE_FEATURE_PARAMETER =
      "opennlp.uima.TypeFeature";
  public static final String CHILDREN_FEATURE_PARAMETER =
      "opennlp.uima.ChildrenFeature";
  public static final String PROBABILITY_FEATURE_PARAMETER =
      "opennlp.uima.ProbabilityFeature";
  protected UimaContext context;
  protected Logger mLogger;
  protected opennlp.tools.parser.Parser mParser;
  private Type mSentenceType;

  private Type mTokenType;
  private Type mParseType;
  private Feature mTypeFeature;
  private Feature childrenFeature;
  private Feature probabilityFeature;

  /**
   * Initializes the current instance with the given context.
   */
  public void initialize(UimaContext context)
      throws ResourceInitializationException {

    super.initialize(context);

    this.context = context;

    mLogger = context.getLogger();

    if (mLogger.isLoggable(Level.INFO)) {
      mLogger.log(Level.INFO, "Initializing the OpenNLP Parser.");
    }

    ParserModel model;

    try {
      ParserModelResource modelResource = (ParserModelResource) context
          .getResourceObject(UimaUtil.MODEL_PARAMETER);

      model = modelResource.getModel();
    } catch (ResourceAccessException e) {
      throw new ResourceInitializationException(e);
    }

    mParser = ParserFactory.create(model);
  }

  /**
   * Initializes the type system.
   */
  public void typeSystemInit(TypeSystem typeSystem)
      throws AnalysisEngineProcessException {

    mSentenceType = AnnotatorUtil.getRequiredTypeParameter(context, typeSystem,
        UimaUtil.SENTENCE_TYPE_PARAMETER);

    mTokenType = AnnotatorUtil.getRequiredTypeParameter(context, typeSystem,
        UimaUtil.TOKEN_TYPE_PARAMETER);

    mParseType = AnnotatorUtil.getRequiredTypeParameter(context, typeSystem,
        PARSE_TYPE_PARAMETER);

    mTypeFeature = AnnotatorUtil.getRequiredFeatureParameter(context,
        mParseType, TYPE_FEATURE_PARAMETER, CAS.TYPE_NAME_STRING);

    childrenFeature = AnnotatorUtil.getRequiredFeatureParameter(context,
        mParseType, CHILDREN_FEATURE_PARAMETER, CAS.TYPE_NAME_FS_ARRAY);

    probabilityFeature = AnnotatorUtil.getOptionalFeatureParameter(context,
        mParseType, PROBABILITY_FEATURE_PARAMETER, CAS.TYPE_NAME_DOUBLE);
  }

  /**
   * Performs parsing on the given {@link CAS} object.
   */
  public void process(CAS cas) {
    FSIndex<AnnotationFS> sentences = cas.getAnnotationIndex(mSentenceType);

    for (AnnotationFS sentence : sentences) {
      process(cas, sentence);
    }
  }

  protected void process(CAS cas, AnnotationFS sentenceAnnotation) {
    FSIndex<AnnotationFS> allTokens = cas.getAnnotationIndex(mTokenType);

    ContainingConstraint containingConstraint =
        new ContainingConstraint(sentenceAnnotation);

    String sentence = sentenceAnnotation.getCoveredText();

    Iterator<AnnotationFS> containingTokens = cas.createFilteredIterator(
        allTokens.iterator(), containingConstraint);

    List<Span> tokenSpans = new LinkedList<>();

    while (containingTokens.hasNext()) {
      AnnotationFS token = containingTokens.next();

      tokenSpans.add(new Span(token.getBegin() - sentenceAnnotation.getBegin(),
          token.getEnd() - sentenceAnnotation.getBegin()));
    }

    ParseConverter converter = new ParseConverter(sentence, tokenSpans.toArray(new Span[tokenSpans.size()]));

    Parse unparsedTree = converter.getParseForTagger();

    if (unparsedTree.getChildCount() > 0) {

      Parse parse = mParser.parse(unparsedTree);

      // TODO: We need a strategy to handle the case that a full
      //       parse could not be found. What to do in this case?

      parse = converter.transformParseFromTagger(parse);

      if (mLogger.isLoggable(Level.INFO)) {
        StringBuffer parseString = new StringBuffer();
        parse.show(parseString);

        mLogger.log(Level.INFO, parseString.toString());
      }

      createAnnotation(cas, sentenceAnnotation.getBegin(), parse);
    }
  }

  protected AnnotationFS createAnnotation(CAS cas, int offset, Parse parse) {

    Parse parseChildren[] = parse.getChildren();
    AnnotationFS parseChildAnnotations[] = new AnnotationFS[parseChildren.length];

    // do this for all children
    for (int i = 0; i < parseChildren.length; i++) {
      parseChildAnnotations[i] = createAnnotation(cas, offset, parseChildren[i]);
    }

    AnnotationFS parseAnnotation = cas.createAnnotation(mParseType, offset +
        parse.getSpan().getStart(), offset + parse.getSpan().getEnd());

    parseAnnotation.setStringValue(mTypeFeature, parse.getType());

    if (probabilityFeature != null) {
      parseAnnotation.setDoubleValue(probabilityFeature, parse.getProb());
    }

    ArrayFS childrenArray = cas.createArrayFS(parseChildAnnotations.length);
    childrenArray.copyFromArray(parseChildAnnotations, 0, 0, parseChildAnnotations.length);
    parseAnnotation.setFeatureValue(childrenFeature, childrenArray);

    cas.getIndexRepository().addFS(parseAnnotation);

    return parseAnnotation;
  }

  /**
   * Releases allocated resources.
   */

  public void destroy() {
    mParser = null;
  }

  private static class ParseConverter {
    private final String mSentence;
    private Map<Integer, Integer> mIndexMap = new HashMap<>();
    private Parse mParseForTagger;

    /**
     * Initializes a new instance.
     *
     * @param sentence
     * @param tokens
     */
    public ParseConverter(String sentence, Span tokens[]) {

      mSentence = sentence;

      StringBuilder sentenceStringBuilder = new StringBuilder();

      String tokenList[] = new String[tokens.length];

      for (int i = 0; i < tokens.length; i++) {
        String tokenString = tokens[i].getCoveredText(sentence).toString();
        String escapedToken = escape(tokenString);
        tokenList[i] = escapedToken;

        int escapedStart = sentenceStringBuilder.length();
        int start = tokens[i].getStart();
        mIndexMap.put(escapedStart, start);

        int escapedEnd = escapedStart + escapedToken.length();
        int end = tokens[i].getEnd();
        mIndexMap.put(escapedEnd, end);

        sentenceStringBuilder.append(tokenList[i]);

        sentenceStringBuilder.append(' ');
      }

      // remove last space
      if (sentenceStringBuilder.length() > 0) {
        sentenceStringBuilder.setLength(sentenceStringBuilder.length() - 1);
      }

      String tokenizedSentence = sentenceStringBuilder.toString();

      mParseForTagger = new Parse(tokenizedSentence,
          new Span(0, tokenizedSentence.length()), "INC", 1, null);

      int start = 0;

      for (String token : tokenList) {
        mParseForTagger.insert(new Parse(tokenizedSentence, new Span(start,
            start + token.length()),
            opennlp.tools.parser.chunking.Parser.TOK_NODE, 0f, 0));

        start += token.length() + 1;
      }
    }

    private static String escape(String text) {
      return text;
    }

    /**
     * Creates the parse for the tagger.
     *
     * @return the parse which can be passed to the tagger
     */
    Parse getParseForTagger() {
      return mParseForTagger;
    }

    /**
     * Converts the parse from the tagger back.
     *
     * @param parseFromTagger
     * @return the final parse
     */
    Parse transformParseFromTagger(Parse parseFromTagger) {
      int start = parseFromTagger.getSpan().getStart();
      int end = parseFromTagger.getSpan().getEnd();

      Parse transformedParse = new Parse(mSentence, new Span(
          mIndexMap.get(start), mIndexMap.get(end)), parseFromTagger.getType(),
          parseFromTagger.getProb(), parseFromTagger.getHeadIndex());

      Parse[] parseFromTaggerChildrens = parseFromTagger.getChildren();

      for (Parse child : parseFromTaggerChildrens) {
        transformedParse.insert(transformParseFromTagger(child));
      }

      return transformedParse;
    }
  }
}
