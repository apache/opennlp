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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * Generate event contexts for maxent decisions for sentence detection.
 *
 * @author      Jason Baldridge
 * @author      Eric D. Friedman
 * @version     $Revision: 1.5 $, $Date: 2008-09-28 18:12:11 $
 */

public class DefaultSDContextGenerator implements SDContextGenerator {

  /** 
   * String buffer for generating features.
   */
  protected StringBuffer buf;
  
  /** 
   * List for holding features as they are generated.
   */
  protected List<String> collectFeats;
  
  private Set<String> inducedAbbreviations;
  
  private char[] eosCharacters;

  /**
   * Creates a new <code>SDContextGenerator</code> instance with
   * no induced abbreviations.
   * 
   * @param eosCharacters 
   */
  @SuppressWarnings("unchecked")
  public DefaultSDContextGenerator(char[] eosCharacters) {
    this(Collections.EMPTY_SET, eosCharacters);
  }

  /**
   * Creates a new <code>SDContextGenerator</code> instance which uses
   * the set of induced abbreviations.
   *
   * @param inducedAbbreviations a <code>Set</code> of Strings
   * representing induced abbreviations in the training data.
   * Example: &quot;Mr.&quot;
   * 
   * @param eosCharacters 
   */
  public DefaultSDContextGenerator(Set<String> inducedAbbreviations, char[] eosCharacters) {
    this.inducedAbbreviations = inducedAbbreviations;
    this.eosCharacters = eosCharacters;
    buf = new StringBuffer();
    collectFeats = new ArrayList<String>();
  }
  
  /* (non-Javadoc)
   * @see opennlp.tools.sentdetect.SDContextGenerator#getContext(java.lang.StringBuffer, int)
   */
  public String[] getContext(StringBuffer sb, int position) {
    
    /** 
     * String preceding the eos character in the eos token.
     */
    String prefix;
    
    /** 
     * Space delimited token preceding token containing eos character.
     */
    String previous;
    
    /** 
     * String following the eos character in the eos token.
     */
    String suffix;
    
    /** 
     * Space delimited token following token containing eos character.
     */
    String next;
    
    int lastIndex = sb.length() - 1;
    { // compute space previous and space next features.
      if (position > 0 && sb.charAt(position - 1) == ' ')
        collectFeats.add("sp");
      if (position < lastIndex && sb.charAt(position + 1) == ' ')
        collectFeats.add("sn");
      collectFeats.add("eos=" + sb.charAt(position));
    }
    int prefixStart = previousSpaceIndex(sb, position);
    
    int c = position;
    { ///assign prefix, stop if you run into a period though otherwise stop at space
      while (--c > prefixStart) {
        for (int eci = 0, ecl = eosCharacters.length; eci < ecl; eci++) {
          if (sb.charAt(c) == eosCharacters[eci]) {
            prefixStart = c;
            c++; // this gets us out of while loop.
            break;
          }
        }
      }
      prefix = sb.substring(prefixStart, position).trim();
    }
    int prevStart = previousSpaceIndex(sb, prefixStart);
    previous = sb.substring(prevStart, prefixStart).trim();
    
    int suffixEnd = nextSpaceIndex(sb, position, lastIndex);
    {
      c = position;
      while (++c < suffixEnd) {
        for (int eci = 0, ecl = eosCharacters.length; eci < ecl; eci++) {
          if (sb.charAt(c) == eosCharacters[eci]) {
            suffixEnd = c;
            c--; // this gets us out of while loop.
            break;
          }
        }
      }
    }
    int nextEnd = nextSpaceIndex(sb, suffixEnd + 1, lastIndex + 1);
    if (position == lastIndex) {
      suffix = "";
      next = "";
    }
    else {
      suffix = sb.substring(position + 1, suffixEnd).trim();
      next = sb.substring(suffixEnd + 1, nextEnd).trim();
    }
    
    collectFeatures(prefix,suffix,previous,next);
    
    String[] context = new String[collectFeats.size()];
    context = (String[]) collectFeats.toArray(context);
    collectFeats.clear();
    return context;
  }
  
  /**
   * Determines some of the features for the sentence detector and adds them to list features.
   * 
   * @param prefix String preceeding the eos character in the eos token.
   * @param suffix String following the eos character in the eos token.
   * @param previous Space delimited token preceeding token containing eos character.
   * @param next Space delimited token following token containsing eos character.
   */
  protected void collectFeatures(String prefix, String suffix, String previous, String next) {
    buf.append("x=");
    buf.append(prefix);
    collectFeats.add(buf.toString());
    buf.setLength(0);
    if (!prefix.equals("")) {
      collectFeats.add(Integer.toString(prefix.length()));
      if (isFirstUpper(prefix)) {
        collectFeats.add("xcap");
      }
      if (inducedAbbreviations.contains(prefix)) {
        collectFeats.add("xabbrev");
      }
    }

    buf.append("v=");
    buf.append(previous);
    collectFeats.add(buf.toString());
    buf.setLength(0);
    if (!previous.equals("")) {
      if (isFirstUpper(previous)) {
        collectFeats.add("vcap");
      }
      if (inducedAbbreviations.contains(previous)) {
        collectFeats.add("vabbrev");
      }
    }

    buf.append("s=");
    buf.append(suffix);
    collectFeats.add(buf.toString());
    buf.setLength(0);
    if (!suffix.equals("")) {
      if (isFirstUpper(suffix)) {
        collectFeats.add("scap");
      }
      if (inducedAbbreviations.contains(suffix)) {
        collectFeats.add("sabbrev");
      }
    }

    buf.append("n=");
    buf.append(next);
    collectFeats.add(buf.toString());
    buf.setLength(0);
    if (!next.equals("")) {
      if (isFirstUpper(next)) {
        collectFeats.add("ncap");
      }
      if (inducedAbbreviations.contains(next)) {
        collectFeats.add("nabbrev");
      }
    }
  }

  private static final boolean isFirstUpper(String s) {
    return Character.isUpperCase(s.charAt(0));
  }

  /**
   * Finds the index of the nearest space before a specified index.
   *
   * @param sb   The string buffer which contains the text being examined.
   * @param seek The index to begin searching from.
   * @return The index which contains the nearest space.
   */
  private static final int previousSpaceIndex(CharSequence sb, int seek) {
    seek--;
    while (seek > 0) {
      if (sb.charAt(seek) == ' ') {
        while (seek > 0 && sb.charAt(seek - 1) == ' ')
          seek--;
        return seek;
      }
      seek--;
    }
    return 0;
  }

  /**
   * Finds the index of the nearest space after a specified index.
   *
   * @param sb The string buffer which contains the text being examined.
   * @param seek The index to begin searching from.
   * @param lastIndex The highest index of the StringBuffer sb.
   * @return The index which contains the nearest space.
   */
  private static final int nextSpaceIndex(CharSequence sb, int seek, int lastIndex) {
    seek++;
    char c;
    while (seek < lastIndex) {
      c = sb.charAt(seek);
      if (c == ' ' || c == '\n') {
        while (sb.length() > seek + 1 && sb.charAt(seek + 1) == ' ')
          seek++;
        return seek;
      }
      seek++;
    }
    return lastIndex;
  }
}