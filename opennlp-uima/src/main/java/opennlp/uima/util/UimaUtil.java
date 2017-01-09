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

package opennlp.uima.util;

import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedList;

import org.apache.uima.cas.CAS;
import org.apache.uima.cas.FSIndex;
import org.apache.uima.cas.Type;
import org.apache.uima.cas.text.AnnotationFS;

/**
 * This is a util class for uima operations.
 */
public final class UimaUtil {

  private UimaUtil(){
    // this is util class must not be instantiated
  }

  /**
   * The token type parameter.
   */
  public static final String TOKEN_TYPE_PARAMETER = "opennlp.uima.TokenType";

  /**
   * The pos feature parameter.
   */
  public static final String POS_FEATURE_PARAMETER = "opennlp.uima.POSFeature";

  /**
   * The model parameter.
   */
  public static String MODEL_PARAMETER = "opennlp.uima.ModelName";

  /**
   * The sentence type parameter.
   */
  public static String SENTENCE_TYPE_PARAMETER = "opennlp.uima.SentenceType";

  /**
   * The beam size parameter.
   */
  public static final String BEAM_SIZE_PARAMETER = "opennlp.uima.BeamSize";

  public static final String LANGUAGE_PARAMETER = "opennlp.uima.Language";

  public static final String DICTIONARY_PARAMETER = "opennlp.uima.Dictionary";

  public static final String TRAINING_PARAMS_FILE_PARAMETER = "opennlp.uima.TrainingParamsFile";

  public static final String CUTOFF_PARAMETER = "opennlp.uima.Cutoff";

  public static final String ITERATIONS_PARAMETER = "opennlp.uima.Iterations";

  public static final String PROBABILITY_FEATURE_PARAMETER =
      "opennlp.uima.ProbabilityFeature";

  public static final String IS_REMOVE_EXISTINGS_ANNOTAIONS =
      "opennlp.uima.IsRemoveExistingAnnotations";

  public static final String ADDITIONAL_TRAINING_DATA_FILE =
      "opennlp.uima.AdditionalTrainingDataFile";

  public static final String ADDITIONAL_TRAINING_DATA_ENCODING =
      "opennlp.uima.AdditionalTrainingDataEncoding";

  /**
   * Removes all annotations of type removeAnnotationType which are contained
   * by annotations of type containerAnnotationType.
   *
   * @param cas
   * @param containerAnnotation
   * @param removeAnnotationType
   */
  public static void removeAnnotations(CAS cas,
      AnnotationFS containerAnnotation, Type removeAnnotationType) {

    FSIndex<AnnotationFS> allRemoveAnnotations = cas
        .getAnnotationIndex(removeAnnotationType);

    ContainingConstraint containingConstraint = new ContainingConstraint(
        containerAnnotation);

    Iterator<AnnotationFS> containingTokens = cas.createFilteredIterator(
        allRemoveAnnotations.iterator(), containingConstraint);

    Collection<AnnotationFS> removeAnnotations = new LinkedList<>();

    while (containingTokens.hasNext()) {
      removeAnnotations.add(containingTokens.next());
    }

    for (Iterator<AnnotationFS> it = removeAnnotations.iterator(); it.hasNext();) {
      cas.removeFsFromIndexes(it.next());
    }
  }
}
