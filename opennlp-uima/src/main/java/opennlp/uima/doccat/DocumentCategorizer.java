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

import opennlp.tools.doccat.DoccatModel;
import opennlp.tools.doccat.DocumentCategorizerME;
import opennlp.uima.util.AnnotatorUtil;
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
 * OpenNLP NameFinder trainer.
 * 
 * Mandatory parameters:
 */
public class DocumentCategorizer extends CasAnnotator_ImplBase {
  
  private UimaContext context;
	
  private Logger mLogger;
  
  private opennlp.tools.doccat.DocumentCategorizer mCategorizer;

  private Type mTokenType;

  private Type mCategoryType;

  private Feature mCategoryFeature;
  
  public void initialize(UimaContext context) 
      throws ResourceInitializationException {
    
    super.initialize(context);
    
    this.context = context;
	  
	mLogger = context.getLogger();
	  
    if (mLogger.isLoggable(Level.INFO)) {
      mLogger.log(Level.INFO, "Initializing the OpenNLP Categorizer.");
    }  
    
    DoccatModel model;
    
    try {
      DoccatModelResource modelResource = 
            (DoccatModelResource) context.getResourceObject(UimaUtil.MODEL_PARAMETER);
        
        model = modelResource.getModel();
    }
    catch (ResourceAccessException e) {
        throw new ResourceInitializationException(e);
    }
    
    mCategorizer = new DocumentCategorizerME(model);
  }
  
  public void typeSystemInit(TypeSystem typeSystem) 
      throws AnalysisEngineProcessException {
    
    // yes it must, the user later would use a very simple tokenizer and pass it to the
    // doccat for language detection
	  mTokenType = AnnotatorUtil.getRequiredTypeParameter(context, typeSystem,
        UimaUtil.SENTENCE_TYPE_PARAMETER);
    
    // get category type and feature (it a document propery, one object with a feature)
    mCategoryType = AnnotatorUtil.getRequiredTypeParameter(context, typeSystem,
        "opennlp.uima.doccat.CategoryType");
    
    // get feature name
    mCategoryFeature = AnnotatorUtil.getRequiredFeatureParameter(context, mCategoryType, 
    		"opennlp.uima.doccat.CategoryFeature", CAS.TYPE_NAME_STRING);
  }
  
  public void process(CAS tcas) {
    
    double result[];
    
    if (mTokenType != null) {
      // TODO:
      // count tokens
      // create token array
      // pass array to doccat
      // create result annotation
      result = mCategorizer.categorize(tcas.getDocumentText());
    }
    else {
      result = mCategorizer.categorize(tcas.getDocumentText());
    }
    
    String bestCategroy = mCategorizer.getBestCategory(result);
    
    // get cat fs 
    FSIndex<AnnotationFS> categoryIndex = tcas.getAnnotationIndex(mCategoryType);
    
    AnnotationFS categoryAnnotation = (AnnotationFS) (categoryIndex.size() > 0 ? 
        categoryIndex.iterator().next() : null);
    
    if (categoryIndex.size() > 0) {
      categoryAnnotation = (AnnotationFS) categoryIndex.iterator().next();
    }
    else {
      categoryAnnotation = tcas.createAnnotation(mCategoryType, 0, 
          tcas.getDocumentText().length());
      
      tcas.getIndexRepository().addFS(categoryAnnotation);
    }    
    
    categoryAnnotation.setStringValue(mCategoryFeature, bestCategroy);
  }
}