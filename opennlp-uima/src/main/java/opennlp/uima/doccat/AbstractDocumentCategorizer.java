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

import java.util.ArrayList;
import java.util.List;

import org.apache.uima.UimaContext;
import org.apache.uima.analysis_component.CasAnnotator_ImplBase;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.cas.CAS;
import org.apache.uima.cas.FSIterator;
import org.apache.uima.cas.Type;
import org.apache.uima.cas.TypeSystem;
import org.apache.uima.cas.text.AnnotationFS;
import org.apache.uima.resource.ResourceAccessException;
import org.apache.uima.resource.ResourceInitializationException;
import org.apache.uima.util.Level;
import org.apache.uima.util.Logger;

import opennlp.tools.doccat.DoccatModel;
import opennlp.tools.doccat.DocumentCategorizerME;
import opennlp.uima.util.AnnotatorUtil;
import opennlp.uima.util.UimaUtil;

/**
 * Abstract document categorizer which can be implemented to define how the
 * output of the categorizer should be written into the CAS.
 */
abstract class AbstractDocumentCategorizer extends CasAnnotator_ImplBase {

  private UimaContext context;

  private opennlp.tools.doccat.DocumentCategorizer mCategorizer;

  private Type mTokenType;

  public void initialize(UimaContext context)
      throws ResourceInitializationException {

    super.initialize(context);

    this.context = context;

    Logger mLogger = context.getLogger();

    if (mLogger.isLoggable(Level.INFO)) {
      mLogger.log(Level.INFO, "Initializing the OpenNLP Categorizer.");
    }

    DoccatModel model;

    try {
      DoccatModelResource modelResource = (DoccatModelResource) context
          .getResourceObject(UimaUtil.MODEL_PARAMETER);

      model = modelResource.getModel();
    } catch (ResourceAccessException e) {
      throw new ResourceInitializationException(e);
    }

    mCategorizer = new DocumentCategorizerME(model);
  }

  public void typeSystemInit(TypeSystem typeSystem) throws AnalysisEngineProcessException {
    mTokenType = AnnotatorUtil.getRequiredTypeParameter(context, typeSystem,
        UimaUtil.TOKEN_TYPE_PARAMETER);
  }

  protected abstract void setBestCategory(CAS cas, String bestCategory);

  public void process(CAS cas) {

    FSIterator<AnnotationFS> tokenAnnotations = cas.getAnnotationIndex(mTokenType).iterator();
    List<String> tokensList = new ArrayList<>();

    while (tokenAnnotations.hasNext()) {
      tokensList.add(tokenAnnotations.next().getCoveredText());
    }

    double[] result =
        mCategorizer.categorize(tokensList.toArray(new String[tokensList.size()]));

    String bestCategory = mCategorizer.getBestCategory(result);

    setBestCategory(cas, bestCategory);
  }
}
