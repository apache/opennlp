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

package opennlp.uima.doccat;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import opennlp.maxent.GIS;
import opennlp.tools.doccat.DoccatModel;
import opennlp.tools.doccat.DocumentCategorizerME;
import opennlp.tools.doccat.DocumentSample;
import opennlp.tools.util.ObjectStreamUtils;
import opennlp.uima.util.CasConsumerUtil;
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
 * OpenNLP NameFinder trainer.
 * 
 * Note: This class is still work in progress, and should not be used!   
 */
public class DocumentCategorizerTrainer extends CasConsumer_ImplBase {

  private UimaContext mContext;

  private Logger mLogger;
  
  private String mModelName;
  
  private List<DocumentSample> documentSamples = new ArrayList<DocumentSample>();
  
  private Type mTokenType;

  private Type mCategoryType;

  private Feature mCategoryFeature;
  
  private String language;
  
  public void initialize() throws ResourceInitializationException {
    
    super.initialize();
    
    mContext = getUimaContext();

    mLogger = mContext.getLogger();
    
    if (mLogger.isLoggable(Level.INFO)) {
      mLogger.log(Level.INFO, "Initializing the OpenNLP Doccat Trainer.");
    }
    
    mModelName = CasConsumerUtil.getRequiredStringParameter(mContext,
        UimaUtil.MODEL_PARAMETER);
    
    language = CasConsumerUtil.getRequiredStringParameter(mContext,
        UimaUtil.LANGUAGE_PARAMETER);
  }
  
  public void typeSystemInit(TypeSystem typeSystem) 
      throws ResourceInitializationException {
    
    String tokenTypeName = CasConsumerUtil.getRequiredStringParameter(mContext,
        UimaUtil.SENTENCE_TYPE_PARAMETER);

    mTokenType = CasConsumerUtil.getType(typeSystem, tokenTypeName);

    String categoryTypeName = CasConsumerUtil.getRequiredStringParameter(mContext,
        "opennlp.uima.doccat.CategoryType");
    
    mCategoryType = CasConsumerUtil.getType(typeSystem, categoryTypeName);
    
    // get feature name
    String categoryFeatureName = CasConsumerUtil.getRequiredStringParameter(mContext,
        "opennlp.uima.doccat.CategoryFeature");
    
    mCategoryFeature = mCategoryType.getFeatureByBaseName(categoryFeatureName);
  }
  
  public void processCas(CAS cas) throws ResourceProcessException {
    
    FSIndex categoryIndex = cas.getAnnotationIndex(mCategoryType);
    
    if (categoryIndex.size() > 0) {
      AnnotationFS categoryAnnotation  = 
          (AnnotationFS) categoryIndex.iterator().next();
      
      // add to event collection
      
      DocumentSample sample = new DocumentSample(
	      categoryAnnotation.getStringValue(mCategoryFeature), 
	      cas.getDocumentText());
      
      documentSamples.add(sample);
    }
  }
  
  public void collectionProcessComplete(ProcessTrace trace) 
      throws ResourceProcessException, IOException {
    
    GIS.PRINT_MESSAGES = false;

    DoccatModel categoryModel = DocumentCategorizerME.train(language, ObjectStreamUtils.createObjectStream(documentSamples));
    
    File modelFile = new File(getUimaContextAdmin().getResourceManager()
        .getDataPath() + File.separatorChar + mModelName);

    OpennlpUtil.serialize(categoryModel, modelFile);
  }
  
  /**
   * The trainer is not stateless.
   */
  public boolean isStateless() {
    return false;
  }
  
  /**
   * Destroys the current instance.
   */
  public void destroy() {
    // dereference to allow garbage collection
    documentSamples = null;
  }
}