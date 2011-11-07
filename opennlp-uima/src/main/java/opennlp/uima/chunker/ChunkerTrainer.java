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

package opennlp.uima.chunker;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import opennlp.maxent.GIS;
import opennlp.tools.chunker.ChunkSample;
import opennlp.tools.chunker.ChunkerME;
import opennlp.tools.chunker.ChunkerModel;
import opennlp.tools.util.ObjectStreamUtils;
import opennlp.uima.util.CasConsumerUtil;
import opennlp.uima.util.ContainingConstraint;
import opennlp.uima.util.OpennlpUtil;
import opennlp.uima.util.UimaUtil;

import org.apache.uima.UimaContext;
import org.apache.uima.cas.CAS;
import org.apache.uima.cas.FSIndex;
import org.apache.uima.cas.Feature;
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
 * OpenNLP Chunker trainer.
 * <p>
 * Mandatory parameters
 * <table border=1>
 *   <tr><th>Type</th> <th>Name</th> <th>Description</th></tr>
 *   <tr><td>String</td> <td>opennlp.uima.ModelName</td> <td>The name of the model file</td></tr>
 *   <tr><td>String</td> <td>opennlp.uima.SentenceType</td> <td>The full name of the sentence type</td></tr>
 *   <tr><td>String</td> <td>opennlp.uima.TokenType</td> <td>The full name of the token type</td></tr>
 *   <tr><td>String</td> <td>opennlp.uima.POSFeature</td></tr>
 *   <tr><td>String</td> <td>opennlp.uima.ChunkType</td></tr>
 *   <tr><td>String</td> <td>opennlp.uima.ChunkTagFeature</td></tr>
 * </table>
 */
public class ChunkerTrainer extends CasConsumer_ImplBase {

  private List<ChunkSample> mChunkSamples = new ArrayList<ChunkSample>();
  
  private UimaContext mContext;

  private String mModelName;
  
  private Type mSentenceType;

  private Type mTokenType;
  
  private Feature mPOSFeature;
  
  private Type mChunkType;
  
  private Feature mChunkTagFeature;
  
  private Logger mLogger;
  
  private String language;
  
  /**
   * Initializes the current instance.
   */
  public void initialize() throws ResourceInitializationException {
    
    super.initialize();
    
    mContext = getUimaContext();

    mLogger = mContext.getLogger();
    
    if (mLogger.isLoggable(Level.INFO)) {
      mLogger.log(Level.INFO, "Initializing the OpenNLP Chunker Trainer.");
    }    

    mModelName = CasConsumerUtil.getRequiredStringParameter(mContext,
        UimaUtil.MODEL_PARAMETER);
    
    language = CasConsumerUtil.getRequiredStringParameter(mContext,
        UimaUtil.LANGUAGE_PARAMETER);
  }
  
  /**
   * Initialize the current instance with the given type system.
   */
  public void typeSystemInit(TypeSystem typeSystem) 
        throws ResourceInitializationException {
    String sentenceTypeName = 
        CasConsumerUtil.getRequiredStringParameter(mContext,
        UimaUtil.SENTENCE_TYPE_PARAMETER);

    mSentenceType = CasConsumerUtil.getType(typeSystem, sentenceTypeName);

    String chunkTypeName = CasConsumerUtil.getRequiredStringParameter(mContext,
        Chunker.CHUNK_TYPE_PARAMETER);
    
    mChunkType = CasConsumerUtil.getType(typeSystem, chunkTypeName);
    
    String chunkTagFeature = CasConsumerUtil.getRequiredStringParameter(
        mContext, Chunker.CHUNK_TAG_FEATURE_PARAMETER);
    
    mChunkTagFeature = mChunkType.getFeatureByBaseName(chunkTagFeature);
      
    CasConsumerUtil.checkFeatureType(mChunkTagFeature, CAS.TYPE_NAME_STRING);
    
    String tokenTypeName = CasConsumerUtil.getRequiredStringParameter(mContext,
        UimaUtil.TOKEN_TYPE_PARAMETER);

    mTokenType = CasConsumerUtil.getType(typeSystem, tokenTypeName);
    
    String posFeatureName = CasConsumerUtil.getRequiredStringParameter(mContext,
        UimaUtil.POS_FEATURE_PARAMETER);
    
    mPOSFeature = mTokenType.getFeatureByBaseName(posFeatureName);
    
    CasConsumerUtil.checkFeatureType(mPOSFeature, CAS.TYPE_NAME_STRING);
  }
  
  /**
   * Process the given CAS object.
   */
  public void processCas(CAS cas) {
    
    FSIndex<AnnotationFS> sentenceIndex = cas.getAnnotationIndex(mSentenceType);

    Iterator<AnnotationFS> sentenceIterator = sentenceIndex.iterator();
    while (sentenceIterator.hasNext()) {
      AnnotationFS sentenceAnnotation = (AnnotationFS) sentenceIterator.next();

      processSentence(cas, sentenceAnnotation);
    }
  }
  
  private void processSentence(CAS tcas, AnnotationFS sentence) {
    FSIndex<AnnotationFS> chunkIndex = tcas.getAnnotationIndex(mChunkType);
    
    ContainingConstraint containingConstraint = 
      new ContainingConstraint(sentence);

    Iterator<AnnotationFS> chunkIterator = tcas.createFilteredIterator(
        chunkIndex.iterator(), containingConstraint);
    
    while (chunkIterator.hasNext()) {
      AnnotationFS chunkAnnotation = (AnnotationFS) chunkIterator.next();
      processChunk(tcas, (chunkAnnotation));
    }
  }
  
  private void processChunk(CAS tcas, AnnotationFS chunk) {
    
    String chunkTag = chunk.getFeatureValueAsString(mChunkTagFeature);
    
    FSIndex<AnnotationFS> tokenIndex = tcas.getAnnotationIndex(mTokenType);
    
    ContainingConstraint containingConstraint = 
      new ContainingConstraint(chunk);
    
    Iterator<AnnotationFS> tokenIterator = tcas.createFilteredIterator(tokenIndex.iterator(), 
        containingConstraint);
    
    List<String> tokens = new ArrayList<String>();
    List<String> tags = new ArrayList<String>();;
    List<String> chunkTags = new ArrayList<String>();;
    
    while (tokenIterator.hasNext()) {
      AnnotationFS tokenAnnotation = tokenIterator.next();
      
      tokens.add(tokenAnnotation.getCoveredText().trim());
      tags.add(tokenAnnotation.getFeatureValueAsString(mPOSFeature));
      chunkTags.add(chunkTag);
    }
    
    mChunkSamples.add(new ChunkSample(tokens, tags, chunkTags));
  }
  
  /**
   * Called if the processing is finished, this method
   * does the training.
   */
  public void collectionProcessComplete(ProcessTrace trace) 
      throws ResourceProcessException, IOException {
    GIS.PRINT_MESSAGES = false;
    
    ChunkerModel chunkerModel = ChunkerME.train(language, ObjectStreamUtils.createObjectStream(mChunkSamples), 100, 5);
    
    // dereference to allow garbage collection
    mChunkSamples  = null;
    
    File modelFile = new File(getUimaContextAdmin().getResourceManager()
        .getDataPath() + File.separatorChar + mModelName);

    OpennlpUtil.serialize(chunkerModel, modelFile);
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
    mChunkSamples = null;
  }
}