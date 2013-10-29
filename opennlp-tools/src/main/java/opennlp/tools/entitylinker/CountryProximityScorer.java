/*
 * Copyright 2013 The Apache Software Foundation.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package opennlp.tools.entitylinker;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import opennlp.tools.entitylinker.domain.BaseLink;
import opennlp.tools.entitylinker.domain.LinkedSpan;
import opennlp.tools.util.Span;

/**
 * Scores toponyms based on country context as well as fuzzy string matching
 */
public class CountryProximityScorer implements LinkedEntityScorer<CountryContext> {

  private Map<String, Set<String>> nameCodesMap;
  String dominantCode = "";

  @Override
  public void score(List<LinkedSpan> linkedSpans, String docText, Span[] sentenceSpans, CountryContext additionalContext) {

    score(linkedSpans, additionalContext.getCountryMentions(), additionalContext.getNameCodesMap(), docText, sentenceSpans, 1000);

  }

  /**
   * Assigns a score to each BaseLink in each linkedSpan's set of N best
   * matches. Currently the scoring indicates the probability that the toponym
   * is correct based on the country context in the document and fuzzy string
   * matching
   *
   * @param linkedData     the linked spans, holds the Namefinder results, and
   *                       the list of BaseLink for each
   * @param countryHits    all the country mentions in the document
   * @param nameCodesMap   maps a country indicator name to a country code. Used
   *                       to determine if the namefinder found the same exact
   *                       toponym the country context did. If so the score is
   *                       boosted due to the high probability that the
   *                       NameFinder actually "rediscovered" a country
   * @param docText        the full text of the document...not used in this
   *                       default implementation
   * @param sentences      the sentences that correspond to the doc text.
   * @param maxAllowedDist a constant that is used to determine which country
   *                       mentions, based on proximity within the text, should
   *                       be used to score the Named Entity.
   * @return
   */
  public List<LinkedSpan> score(List<LinkedSpan> linkedData, Map<String, Set<Integer>> countryHits, Map<String, Set<String>> nameCodesMap, String docText, Span[] sentences, Integer maxAllowedDist) {
    this.nameCodesMap = nameCodesMap;
    setDominantCode(countryHits);
    for (LinkedSpan<BaseLink> linkedspan : linkedData) {

      linkedspan = simpleProximityAnalysis(sentences, countryHits, linkedspan, maxAllowedDist);
    }
    return linkedData;
  }

  /**
   * sets class level variable to a code based on the number of mentions
   *
   * @param countryHits
   */
  private void setDominantCode(Map<String, Set<Integer>> countryHits) {
    int hits = -1;
    for (String code : countryHits.keySet()) {
      if (countryHits.get(code).size() > hits) {
        hits = countryHits.get(code).size();
        dominantCode = code;
      }
    }
  }

  /**
   * Generates distances from each country mention to the span's location in the
   * doc text. Ultimately an attempt to ensure that ambiguously named toponyms
   * are resolved to the correct country and coordinate.
   *
   * @param sentences
   * @param countryHits
   * @param span
   * @return
   */
  private LinkedSpan<BaseLink> simpleProximityAnalysis(Span[] sentences, Map<String, Set<Integer>> countryHits, LinkedSpan<BaseLink> span, Integer maxAllowedDistance) {
    Double score = 0.0;
    //get the index of the actual span, begining of sentence
    //should generate tokens from sentence and create a char offset...
    //could have large sentences due to poor sentence detection or wonky doc text
    int sentenceIdx = span.getSentenceid();
    int sentIndexInDoc = sentences[sentenceIdx].getStart();
    /**
     * create a map of all the span's proximal country mentions in the document
     * Map< countrycode, set of <distances from this NamedEntity>>
     */
    Map<String, Set<Integer>> distancesFromCodeMap = new HashMap<String, Set<Integer>>();
    //map = Map<countrycode, Set <of distances this span is from all the mentions of the code>>
    for (String cCode : countryHits.keySet()) {
//iterate over all the regex start values and calculate an offset
      for (Integer cHit : countryHits.get(cCode)) {
        Integer absDist = Math.abs(sentIndexInDoc - cHit);
        //only include near mentions based on a heuristic
        //TODO make this a property
        //  if (absDist < maxAllowedDistance) {
        if (distancesFromCodeMap.containsKey(cCode)) {
          distancesFromCodeMap.get(cCode).add(absDist);
        } else {
          HashSet<Integer> newset = new HashSet<Integer>();
          newset.add(absDist);
          distancesFromCodeMap.put(cCode, newset);
        }
      }

      //}
    }
    //we now know how far this named entity is from every country mention in the document

    /**
     * the gaz matches that have a country code that have mentions in the doc
     * that are closest to the Named Entity should return the best score Analyze
     * map generates a likelihood score that the toponym from the gaz is
     * referring to one of the countries Map<countrycode, prob that this span is
     * referring to the toponym form this code key>
     */
    Map<String, Double> scoreMap = analyzeMap(distancesFromCodeMap, sentences, span);
    for (BaseLink link : span.getLinkedEntries()) {
      //getItemParentId is the country code
      String spanCountryCode = link.getItemParentID();
      if (scoreMap.containsKey(spanCountryCode)) {

        score = scoreMap.get(spanCountryCode);
        ///does the name extracted match a country name?
        if (nameCodesMap.containsKey(link.getItemName().toLowerCase())) {
          //if so, is it the correct country code for that name
          if (nameCodesMap.get(link.getItemName().toLowerCase()).contains(link.getItemParentID())) {
            //boost the score becuase it is likely that this is the location in the text, so add 50% to the score or set to 1
            //TODO: make this multiplier configurable
            //TODO: improve this with a geographic/geometry based clustering (linear binning to be more precise) of points returned from the gaz
            score = (score + .75) > 1.0 ? 1d : (score + .75);
            //boost the score if the hit is from the dominant country context

            if (link.getItemParentID().equals(dominantCode)) {
              score = (score + .25) > 1.0 ? 1d : (score + .25);
            }


          }

        }
      }
      link.getScoreMap().put("countrycontext", score);
    }
    return span;
  }

  /**
   * takes a map of distances from the NE to each country mention and generates
   * a map of scores for each country code. The map is then correlated to teh
   * correlated to the code of the BaseLink parentid for retrieval. Then the
   * score is added to the overall.
   *
   * @param distanceMap
   * @param sentences
   * @param span
   * @return
   */
  private Map<String, Double> analyzeMap(Map<String, Set<Integer>> distanceMap, Span[] sentences, LinkedSpan<BaseLink> span) {

    Map<String, Double> scoreMap = new HashMap<String, Double>();
    TreeSet<Integer> all = new TreeSet<Integer>();
    for (String key : distanceMap.keySet()) {
      all.addAll(distanceMap.get(key));
    }
    //get min max for normalization, this could be more efficient
    Integer min = all.first();
    Integer max = all.last();
    for (String key : distanceMap.keySet()) {

      TreeSet<Double> normalizedDistances = new TreeSet<Double>();
      for (Integer i : distanceMap.get(key)) {
        Double norm = normalize(i, min, max);
        //reverse the normed distance so low numbers (closer) are better
        //this could be improved with a "decaying " function using an imcreaseing negative exponent
        Double reverse = Math.abs(norm - 1);
        normalizedDistances.add(reverse);
      }


      List<Double> doubles = new ArrayList<Double>(normalizedDistances);
      scoreMap.put(key, slidingDistanceAverage(doubles));
    }
    return scoreMap;
  }

  /**
   * this method is an attempt to make closer clusters of mentions group
   * together to smooth out the average, so one distant outlier does not kill
   * the score for an obviously good hit. More elegant solution is possible
   * using Math.pow, and making the score decay with distance by using an
   * increasing negative exponent
   *
   * @param normDis the normalized and sorted set of distances as a list
   * @return
   */
  private Double slidingDistanceAverage(List<Double> normDis) {
    List<Double> windowOfAverages = new ArrayList<Double>();

    if (normDis.size() < 3) {
      windowOfAverages.addAll(normDis);
    } else {

      for (int i = 0; i < normDis.size() - 1; i++) {
        double a = normDis.get(i);
        double b = normDis.get(i + 1);
        windowOfAverages.add((a + b) / 2);

      }
    }
    double sum = 0d;
    for (double d : windowOfAverages) {
      sum += d;
    }
    double result = sum / windowOfAverages.size();
    //TODO: ++ prob when large amounts of mentions for a code
    //System.out.println("avg of window:" + result);
    return result;
  }

  /**
   * transposes a value within one range to a relative value in a different
   * range. Used to normalize distances in this class.
   *
   * @param valueToNormalize the value to place within the new range
   * @param minimum          the min of the set to be transposed
   * @param maximum          the max of the set to be transposed
   * @return
   */
  private Double normalize(int valueToNormalize, int minimum, int maximum) {
    Double d = (double) ((1 - 0) * (valueToNormalize - minimum)) / (maximum - minimum) + 0;
    d = d == null ? 0d : d;
    return d;
  }
}
