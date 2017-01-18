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

package opennlp.tools.sentdetect.lang.th;

import opennlp.tools.sentdetect.DefaultSDContextGenerator;

/**
 * Creates contexts/features for end-of-sentence detection in Thai text.
 */
public class SentenceContextGenerator extends DefaultSDContextGenerator {

  public static final char[] eosCharacters =  {' ','\n'};

  public SentenceContextGenerator() {
    super(eosCharacters);
  }

  @Override
  protected void collectFeatures(String prefix, String suffix, String previous, String next) {
    buf.append("p=");
    buf.append(prefix);
    collectFeats.add(buf.toString());
    buf.setLength(0);

    buf.append("s=");
    buf.append(suffix);
    collectFeats.add(buf.toString());
    buf.setLength(0);

    collectFeats.add("p1=" + prefix.substring(Math.max(prefix.length() - 1,0)));
    collectFeats.add("p2=" + prefix.substring(Math.max(prefix.length() - 2,0)));
    collectFeats.add("p3=" + prefix.substring(Math.max(prefix.length() - 3,0)));
    collectFeats.add("p4=" + prefix.substring(Math.max(prefix.length() - 4,0)));
    collectFeats.add("p5=" + prefix.substring(Math.max(prefix.length() - 5,0)));
    collectFeats.add("p6=" + prefix.substring(Math.max(prefix.length() - 6,0)));
    collectFeats.add("p7=" + prefix.substring(Math.max(prefix.length() - 7,0)));

    collectFeats.add("n1=" + suffix.substring(0,Math.min(1, suffix.length())));
    collectFeats.add("n2=" + suffix.substring(0,Math.min(2, suffix.length())));
    collectFeats.add("n3=" + suffix.substring(0,Math.min(3, suffix.length())));
    collectFeats.add("n4=" + suffix.substring(0,Math.min(4, suffix.length())));
    collectFeats.add("n5=" + suffix.substring(0,Math.min(5, suffix.length())));
    collectFeats.add("n6=" + suffix.substring(0,Math.min(6, suffix.length())));
    collectFeats.add("n7=" + suffix.substring(0,Math.min(7, suffix.length())));
  }
}
