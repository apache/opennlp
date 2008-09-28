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


package opennlp.tools.sentdetect;

/**
 * The interface for sentence detectors, which find the sentence boundaries in
 * a text.
 *
 * @author      Jason Baldridge
 * @version     $Revision: 1.2 $, $Date: 2008-09-28 18:12:11 $
 */

public interface SentenceDetector {

    /**
     * Sentence detect a string.
     *
     * @param s The string to be sentence detected.
     * @return  The String[] with the individual sentences as the array
     *          elements.
     */
    public String[] sentDetect(String s);

    /**
     * Sentence detect a string.
     *
     * @param s The string to be sentence detected.
     * @return  An int[] with the starting offset positions of each
     *          detected sentences. 
     */
    public int[] sentPosDetect(String s);
    
}
