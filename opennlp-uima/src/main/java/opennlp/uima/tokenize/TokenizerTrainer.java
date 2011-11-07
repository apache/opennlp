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

package opennlp.uima.tokenize;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import opennlp.maxent.GIS;
import opennlp.tools.namefind.NameSample;
import opennlp.tools.namefind.NameSampleDataStream;
import opennlp.tools.tokenize.TokenSample;
import opennlp.tools.tokenize.TokenSampleStream;
import opennlp.tools.tokenize.TokenizerME;
import opennlp.tools.tokenize.TokenizerModel;
import opennlp.tools.util.ObjectStream;
import opennlp.tools.util.ObjectStreamUtils;
import opennlp.tools.util.PlainTextByLineStream;
import opennlp.tools.util.Span;
import opennlp.uima.util.CasConsumerUtil;
import opennlp.uima.util.ContainingConstraint;
import opennlp.uima.util.OpennlpUtil;
import opennlp.uima.util.UimaUtil;

import org.apache.uima.UimaContext;
import org.apache.uima.cas.CAS;
import org.apache.uima.cas.FSIndex;
import org.apache.uima.cas.Type;
import org.apache.uima.cas.TypeSystem;
import org.apache.uima.cas.text.AnnotationFS;
import org.apache.uima.collection.CasConsumer_ImplBase;
import org.apache.uima.resource.ResourceInitializationException;
import org.apache.uima.resource.ResourceProcessException;
import org.apache.uima.util.Level;
import org.apache.uima.util.Logger;
import org.apache.uima.util.ProcessTrace;

/**
 * OpenNLP Tokenizer trainer.
 * <p>
 * Mandatory parameters
 * <table border=1>
 *   <tr><th>Type</th> <th>Name</th> <th>Description</th></tr>
 *   <tr><td>String</td> <td>opennlp.uima.ModelName</td> <td>The name of the model file</td></tr>
 *   <tr><td>String</td> <td>opennlp.uima.SentenceType</td> <td>The full name of the sentence type</td></tr>
 *   <tr><td>String</td> <td>opennlp.uima.TokenType</td> <td>The full name of the token type</td></tr>
 * </table>
 * <p>
 * Optional parameters
 * <table border=1>
 *   <tr><th>Type</th> <th>Name</th> <th>Description</th></tr>
 *   <tr><td>Boolean</td> <td>opennlp.uima.tokenizer.IsSkipAlphaNumerics</td></tr>
 * </table>
 */
public final class TokenizerTrainer extends CasConsumer_ImplBase {
  
  public static final String IS_ALPHA_NUMERIC_OPTIMIZATION = 
      "opennlp.uima.tokenizer.IsAlphaNumericOptimization";
	  
  private List<TokenSample> tokenSamples = new ArrayList<TokenSample>();

  private UimaContext mContext;

  private Type mSentenceType;

  private Type mTokenType;

  private String mModelName;

  private String additionalTrainingDataFile;
  
  private String additionalTrainingDataEncoding;
  
  private String language;
  
  private Boolean isSkipAlphaNumerics;
  
  private Logger mLogger;
  
  /**
   * Initializes the current instance.
   */
  public void initialize() throws ResourceInitializationException {
	  
	super.initialize();
	
    mContext = getUimaContext();
    
    mLogger = mContext.getLogger();
    
    if (mLogger.isLoggable(Level.INFO)) {
      mLogger.log(Level.INFO, "Initializing the OpenNLP Tokenizer trainer.");
    } 
    
    mModelName = CasConsumerUtil.getRequiredStringParameter(mContext,
        UimaUtil.MODEL_PARAMETER);
    
    language = CasConsumerUtil.getRequiredStringParameter(mContext,
        UimaUtil.LANGUAGE_PARAMETER);
    
    isSkipAlphaNumerics = 
        CasConsumerUtil.getOptionalBooleanParameter(
        mContext, IS_ALPHA_NUMERIC_OPTIMIZATION);
    
    if (isSkipAlphaNumerics == null)
    	isSkipAlphaNumerics = false;
    
    additionalTrainingDataFile = CasConsumerUtil.getOptionalStringParameter(
        getUimaContext(), UimaUtil.ADDITIONAL_TRAINING_DATA_FILE);
    
    // If the additional training data is specified, the encoding must be provided!
    if (additionalTrainingDataFile != null) {
      additionalTrainingDataEncoding = CasConsumerUtil.getRequiredStringParameter(
          getUimaContext(), UimaUtil.ADDITIONAL_TRAINING_DATA_ENCODING);
    }
  }

  /**
   * Initialize the current instance with the given type system.
   */
  public void typeSystemInit(TypeSystem typeSystem)
      throws ResourceInitializationException {

    String sentenceTypeName = CasConsumerUtil.getRequiredStringParameter(mContext,
        UimaUtil.SENTENCE_TYPE_PARAMETER);
    
    mSentenceType = CasConsumerUtil.getType(typeSystem, sentenceTypeName);

    String tokenTypeName = CasConsumerUtil.getRequiredStringParameter(mContext,
        UimaUtil.TOKEN_TYPE_PARAMETER);
    
    mTokenType = CasConsumerUtil.getType(typeSystem, tokenTypeName);
  }

  /**
   * Process the given CAS object.
   */
  public void processCas(CAS cas) {
    
    FSIndex<AnnotationFS> sentenceAnnotations = cas.getAnnotationIndex(mSentenceType);

    Iterator<AnnotationFS> sentenceAnnotationsIterator = sentenceAnnotations.iterator();

    while (sentenceAnnotationsIterator.hasNext()) {

      AnnotationFS sentence = 
        sentenceAnnotationsIterator.next();

      process(cas, sentence);
    }
  }
  
  private void process(CAS tcas, AnnotationFS sentence) {
    FSIndex<AnnotationFS> allTokens = tcas.getAnnotationIndex(mTokenType);

    ContainingConstraint containingConstraint = 
        new ContainingConstraint(sentence);

    Iterator<AnnotationFS> containingTokens = tcas.createFilteredIterator(
        allTokens.iterator(), containingConstraint);

    List<Span> openNLPSpans = new LinkedList<Span>();

    while (containingTokens.hasNext()) {
      AnnotationFS tokenAnnotation = 
        (AnnotationFS) containingTokens .next();

      openNLPSpans.add(new Span(tokenAnnotation.getBegin()
          - sentence.getBegin(), tokenAnnotation.getEnd()
          - sentence.getBegin()));
    }

    Span[] spans = openNLPSpans.toArray(new Span[openNLPSpans.size()]);
    
    Arrays.sort(spans);
    
    tokenSamples.add(new TokenSample(sentence.getCoveredText(), spans));
  }

  /**
   * Called if the processing is finished, this method
   * does the training.
   */
  public void collectionProcessComplete(ProcessTrace arg0)
      throws ResourceProcessException, IOException {
    
    if (mLogger.isLoggable(Level.INFO)) {
      mLogger.log(Level.INFO, "Collected " + tokenSamples.size() + 
          " token samples.");
    }
    
    GIS.PRINT_MESSAGES = false;
   
    ObjectStream<TokenSample> samples = ObjectStreamUtils.createObjectStream(tokenSamples);
    
    InputStream additionalTrainingDataIn = null;
    TokenizerModel tokenModel;
    
    try {
      if (additionalTrainingDataFile != null) {
        
        if (mLogger.isLoggable(Level.INFO)) {
          mLogger.log(Level.INFO, "Using addional training data file: " + additionalTrainingDataFile); 
        }
        
        additionalTrainingDataIn = new FileInputStream(additionalTrainingDataFile);
        
        ObjectStream<TokenSample> additionalSamples = new TokenSampleStream(
            new PlainTextByLineStream(new InputStreamReader(additionalTrainingDataIn, additionalTrainingDataEncoding)));
        
        samples = ObjectStreamUtils.createObjectStream(samples, additionalSamples);
      }
      
      tokenModel = TokenizerME.train(language, samples, isSkipAlphaNumerics);
    }
    finally {
      if (additionalTrainingDataIn != null)
        additionalTrainingDataIn.close();
    }
    
    // dereference to allow garbage collection
    tokenSamples = null;
    
    File modelFile = new File(getUimaContextAdmin().getResourceManager()
        .getDataPath() + File.separatorChar + mModelName);
    
    OpennlpUtil.serialize(tokenModel, modelFile);
  }
  
  /**
   * The trainer is not stateless.
   */
  public boolean isStateless() {
    return false;
  }
  
  /**
   * Releases allocated resources.
   */
  public void destroy() {
    // dereference to allow garbage collection
    tokenSamples = null;
  }
}