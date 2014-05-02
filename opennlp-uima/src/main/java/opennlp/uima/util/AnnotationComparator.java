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

import java.util.Comparator;

import org.apache.uima.cas.text.AnnotationFS;

/**
 * Checks two annotations for equality.
 */
public class AnnotationComparator implements Comparator<AnnotationFS>
{

  /**
   * Compares the begin indexes of the annotations.
   *
   * @param a - first annotation
   * @param b - second annotation
   *
   * @return 0 if equals,  &lt; 0 if before and &gt; 0 if after
   */
  public int compare(AnnotationFS a, AnnotationFS b) {
    return a.getBegin() - b.getBegin();
  }
}