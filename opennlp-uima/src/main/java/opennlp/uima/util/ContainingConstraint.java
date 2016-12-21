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
import java.util.LinkedList;

import org.apache.uima.cas.FSMatchConstraint;
import org.apache.uima.cas.FeatureStructure;
import org.apache.uima.cas.text.AnnotationFS;

/**
 * Checks if an AnnotationFS is contained by the given AnnotationFS.
 */
public final class ContainingConstraint implements FSMatchConstraint {
  private static final long serialVersionUID = 1;

  private Collection<AnnotationFS> mContainingAnnotations = new LinkedList<>();

  /**
   * Initializes a new instance.
   */
  public ContainingConstraint() {
    // does currently nothing
  }

  /**
   * Initializes a new instance.
   *
   * @param containingAnnotation
   */
  public ContainingConstraint(AnnotationFS containingAnnotation) {
    mContainingAnnotations.add(containingAnnotation);
  }

  /**
   * Checks if the given FeatureStructure match the constraint.
   */
  public boolean match(FeatureStructure featureStructure) {
    if (!(featureStructure instanceof AnnotationFS)) {
      return false;
    }

    AnnotationFS annotation = (AnnotationFS) featureStructure;

    for (AnnotationFS containingAnnotation : mContainingAnnotations) {
      if (isContaining(annotation, containingAnnotation)) {
        return true;
      }
    }

    return false;
  }

  private boolean isContaining(AnnotationFS annotation, AnnotationFS containing) {
    return (containing.getBegin() <= annotation.getBegin())
      && (containing.getEnd() >= annotation.getEnd());
  }

}