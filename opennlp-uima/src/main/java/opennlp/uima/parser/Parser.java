/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreemnets.  See the NOTICE file distributed with
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

import opennlp.tools.parser.Parse;
import opennlp.tools.parser.ParserFactory;
import opennlp.tools.parser.ParserModel;
import opennlp.tools.util.Span;
import opennlp.uima.util.AnnotatorUtil;
import opennlp.uima.util.ContainingConstraint;
import opennlp.uima.util.UimaUtil;

import org.apache.uima.UimaContext;
import org.apache.uima.analysis_component.CasAnnotator_ImplBase;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
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

/**
 * Abstract base class for OpenNLP Parser annotators.
 * <p>
 * Mandatory parameters
 * <table border=1>
 *   <tr><th>Type</th> <th>Name</th> <th>Description</th></tr>
 *   <tr><td>String</td> <td>opennlp.uima.ModelName</td> <td>The name of the model file</td></tr>
 *   <tr><td>String</td> <td>opennlp.uima.SentenceType</td> <td>The full name of the sentence type</td></tr>
 *   <tr><td>String</td> <td>opennlp.uima.TokenType</td> <td>The full name of the token type</td></tr>
 *   <tr><td>String</td> <td>opennlp.uima.ParseType</td> <td>The full name of the parse type</td></tr>
 *   <tr><td>String</td> <td>opennlp.uima.TypeFeature</td> <td>The name of the type feature</td></tr>
 * </table>
 * <p>
 * Optional parameters
 * <table border=1>
 *   <tr><th>Type</th> <th>Name</th> <th>Description</th></tr>
 *   <tr><td>Integer</td> <td>opennlp.uima.BeamSize</td></tr>
 * </table>
 */
public class Parser extends CasAnnotator_ImplBase {
 
  private static class ParseConverter {
    private Map<Integer, Integer> mIndexMap = new HashMap<Integer, Integer>();
    
    private Parse mParseForTagger;
    
    private String mSentence;
    
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
        mIndexMap.put(new Integer(escapedStart), new Integer(start));

        int escapedEnd = escapedStart + escapedToken.length();
        int end = tokens[i].getEnd();
        mIndexMap.put(new Integer(escapedEnd), new Integer(end));

        sentenceStringBuilder.append(tokenList[i]);

        sentenceStringBuilder.append(' ');
      }
      
      // remove last space
      sentenceStringBuilder.setLength(sentenceStringBuilder.length() - 1);
      
      String tokenizedSentence = sentenceStringBuilder.toString();
      
      mParseForTagger = new Parse(tokenizedSentence, 
          new Span(0, tokenizedSentence.length()), "INC", 1, null);
      
      int start = 0;
      
      for (int i = 0; i < tokenList.length; i++) {

        mParseForTagger.insert(new Parse(tokenizedSentence, new Span(start,
            start + tokenList[i].length()),
            opennlp.tools.parser.chunking.Parser.TOK_NODE, 0f, 0));

        start += tokenList[i].length() + 1;
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
      
      
      Parse transformedParse = new Parse(mSentence, 
          new Span(((Integer) mIndexMap.get(new Integer(start))).intValue(), 
          ((Integer) mIndexMap.get(new Integer(end))).intValue()), 
          parseFromTagger.getType(), 
          parseFromTagger.getProb(), parseFromTagger.getHeadIndex());
      
      
      Parse[] parseFromTaggerChildrens = parseFromTagger.getChildren();
      
      // call this method for all childs ... 
      for (int i = 0; i < parseFromTaggerChildrens.length; i++) {
        
        Parse child = parseFromTaggerChildrens[i];
        
        if (!child.getType().equals(
            opennlp.tools.parser.chunking.Parser.TOK_NODE)) {
        
          // only insert if it has childs
          if (child.getChildCount() > 0 && 
              !child.getChildren()[0].getType().equals(opennlp.tools.parser.chunking.Parser.TOK_NODE)) {
            transformedParse.insert(transformParseFromTagger(child));
          }
        }
      }
      
      if (parseFromTagger.getType().equals("TOP")) {
        return transformedParse.getChildren()[0];
      }
      else {
        return transformedParse;
      }
    }
    
  }

  private static final String PARSE_TYPE_PARAMETER = "opennlp.uima.ParseType";

  public static final String TYPE_FEATURE_PARAMETER = 
      "opennlp.uima.TypeFeature";
  
  protected UimaContext context;
  
  protected Logger mLogger;

  private Type mSentenceType;

  private Type mTokenType;

  protected opennlp.tools.parser.Parser mParser;

  private Type mParseType;

  private Feature mTypeFeature;

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
  }
  
  /**
   * Performs parsing on the given {@link CAS} object.
   */
  public void process(CAS cas) {
    FSIndex<AnnotationFS> sentences = cas.getAnnotationIndex(mSentenceType);

    Iterator<AnnotationFS> sentencesIterator = sentences.iterator();

    while (sentencesIterator.hasNext()) {
      AnnotationFS sentence = (AnnotationFS) sentencesIterator.next();

      process(cas, sentence);
    }
  }
  
  protected void process(CAS cas, AnnotationFS sentenceAnnotation) {
    FSIndex<AnnotationFS> allTokens = cas.getAnnotationIndex(mTokenType);
    
    ContainingConstraint containingConstraint = 
        new ContainingConstraint(sentenceAnnotation);
    
    Iterator<AnnotationFS> containingTokens = cas.createFilteredIterator(
        allTokens.iterator(), containingConstraint);
  
    StringBuilder sentenceStringBuilder = new StringBuilder();
    
    while (containingTokens.hasNext()) {
      AnnotationFS token = (AnnotationFS) containingTokens.next();

      sentenceStringBuilder.append(token.getCoveredText());

      // attention the offsets moves inside the sentence...
      sentenceStringBuilder.append(' ');
    }
     
    String sentence = sentenceStringBuilder.toString();
    sentence = sentenceAnnotation.getCoveredText();

    containingTokens = cas.createFilteredIterator(
        allTokens.iterator(), containingConstraint);
   
    List<Span> tokenSpans = new LinkedList<Span>();
    
    while(containingTokens.hasNext()) {
      AnnotationFS token = (AnnotationFS) containingTokens.next();

      tokenSpans.add(new Span(token.getBegin() - sentenceAnnotation.getBegin(), 
          token.getEnd() - sentenceAnnotation.getBegin()));
    }
    
    ParseConverter converter = new ParseConverter(sentence,(Span[]) 
        tokenSpans.toArray(new Span[tokenSpans.size()]));
    
   Parse parse = mParser.parse(converter.getParseForTagger());
  
   parse = converter.transformParseFromTagger(parse);
   
   if (mLogger.isLoggable(Level.INFO)) {
     StringBuffer parseString = new StringBuffer();
     parse.show(parseString);
     
     mLogger.log(Level.INFO, parseString.toString());
   }
   
   createAnnotation(cas, sentenceAnnotation.getBegin(), parse);
  }
  
  protected void createAnnotation(CAS cas, int offset, Parse parse) {
    
    Parse parseChildrens[] = parse.getChildren();
    
    // do this for all children
    for (int i = 0; i < parseChildrens.length; i++) {
      Parse child = parseChildrens[i];
      createAnnotation(cas, offset, child);
    }
    
    AnnotationFS parseAnnotation = cas.createAnnotation(mParseType, offset + 
        parse.getSpan().getStart(), offset + parse.getSpan().getEnd());
    
    parseAnnotation.setStringValue(mTypeFeature, parse.getType());
    
    cas.getIndexRepository().addFS(parseAnnotation);
  }

  /**
   * Releases allocated resources.
   */
  public void destroy() {
    mParser = null;
  }
}
