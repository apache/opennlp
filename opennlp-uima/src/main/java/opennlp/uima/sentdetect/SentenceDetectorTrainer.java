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

package opennlp.uima.sentdetect;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import opennlp.maxent.GIS;
import opennlp.tools.sentdetect.SentenceDetectorME;
import opennlp.tools.sentdetect.SentenceModel;
import opennlp.tools.sentdetect.SentenceSample;
import opennlp.tools.util.ObjectStreamUtils;
import opennlp.tools.util.Span;
import opennlp.uima.util.CasConsumerUtil;
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
 * OpenNLP SentenceDetector trainer.
 * <p>
 * Mandatory parameters
 * <table border=1>
 *   <tr><th>Type</th> <th>Name</th> <th>Description</th></tr>
 *   <tr><td>String</td> <td>opennlp.uima.ModelName</td> <td>The name of the model file</td></tr>
 *   <tr><td>String</td> <td>opennlp.uima.SentenceType</td> <td>The full name of the sentence type</td></tr>
 * </table>
 */
public final class SentenceDetectorTrainer extends CasConsumer_ImplBase {
  
  private List<SentenceSample> sentenceSamples = new ArrayList<SentenceSample>();

  private Type mSentenceType;

  private String mModelName;
  
  private String language = "en";
  
  private Logger mLogger;

  private UimaContext mContext;
  
  /**
   * Initializes the current instance.
   */
  public void initialize() throws ResourceInitializationException {
    
    super.initialize();
    
    mContext = getUimaContext();
    
    mLogger = mContext.getLogger();
    
    if (mLogger.isLoggable(Level.INFO)) {
      mLogger.log(Level.INFO, "Initializing the OpenNLP SentenceDetector " +
          "trainer.");
    } 
    
    mModelName = CasConsumerUtil.getRequiredStringParameter(mContext, 
        UimaUtil.MODEL_PARAMETER);
    
    language = CasConsumerUtil.getRequiredStringParameter(mContext,
        UimaUtil.LANGUAGE_PARAMETER);
  }
  
  /**
   * Initializes the current instance with the given type system.
   */
  public void typeSystemInit(TypeSystem typeSystem)
      throws ResourceInitializationException {
    
    String sentenceTypeName = 
        CasConsumerUtil.getRequiredStringParameter(mContext,
        UimaUtil.SENTENCE_TYPE_PARAMETER);
    
    mSentenceType = CasConsumerUtil.getType(typeSystem, sentenceTypeName);
  }

  /**
   * Process the given CAS object.
   */
  public void processCas(CAS cas) {

    FSIndex<AnnotationFS> sentenceIndex = cas.getAnnotationIndex(mSentenceType);

    Span[] sentSpans = new Span[sentenceIndex.size()];

    int i = 0;
    Iterator<AnnotationFS> sentenceIterator = sentenceIndex.iterator();
    while (sentenceIterator.hasNext()) {
      AnnotationFS sentenceAnnotation = (AnnotationFS) sentenceIterator.next();

      sentSpans[i++] = new Span(sentenceAnnotation.getBegin(), sentenceAnnotation.getEnd());
    }

    sentenceSamples.add(new SentenceSample(cas.getDocumentText(), sentSpans));
  }

  /**
   * Called if the processing is finished, this method
   * does the training.
   */
  public void collectionProcessComplete(ProcessTrace trace)
      throws ResourceProcessException, IOException {
    GIS.PRINT_MESSAGES = false;

    SentenceModel sentenceModel = SentenceDetectorME.train(language,
        ObjectStreamUtils.createObjectStream(sentenceSamples), true, null);
    
    // dereference to allow garbage collection
    sentenceSamples = null;
    
    File modelFile = new File(getUimaContextAdmin().getResourceManager()
        .getDataPath() + File.separatorChar + mModelName);

    OpennlpUtil.serialize(sentenceModel,modelFile);
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
    sentenceSamples = null;
  }
}