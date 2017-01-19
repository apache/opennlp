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

package opennlp.uima.doccat;

import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.cas.CAS;
import org.apache.uima.cas.FSIndex;
import org.apache.uima.cas.Feature;
import org.apache.uima.cas.Type;
import org.apache.uima.cas.TypeSystem;
import org.apache.uima.cas.text.AnnotationFS;

import opennlp.uima.util.AnnotatorUtil;

/**
 * OpenNLP Document Categorizer.
 * <p>
 * Mandatory parameters:
 */
public class DocumentCategorizer extends AbstractDocumentCategorizer {

  private Type mCategoryType;

  private Feature mCategoryFeature;


  public void typeSystemInit(TypeSystem typeSystem)
      throws AnalysisEngineProcessException {

    // get category type and feature (it a document propery, one object with a feature)
    mCategoryType = AnnotatorUtil.getRequiredTypeParameter(getContext(), typeSystem,
        "opennlp.uima.doccat.CategoryType");

    // get feature name
    mCategoryFeature = AnnotatorUtil.getRequiredFeatureParameter(getContext(), mCategoryType,
        "opennlp.uima.doccat.CategoryFeature", CAS.TYPE_NAME_STRING);
  }

  @Override
  protected void setBestCategory(CAS tcas, String bestCategory) {
    FSIndex<AnnotationFS> categoryIndex = tcas.getAnnotationIndex(mCategoryType);

    AnnotationFS categoryAnnotation;

    if (categoryIndex.size() > 0) {
      categoryAnnotation = categoryIndex.iterator().next();
    } else {
      categoryAnnotation = tcas.createAnnotation(mCategoryType, 0,
          tcas.getDocumentText().length());

      tcas.getIndexRepository().addFS(categoryAnnotation);
    }

    categoryAnnotation.setStringValue(mCategoryFeature, bestCategory);
  }
}