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
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import opennlp.tools.entitylinker.domain.BaseLink;
import opennlp.tools.entitylinker.domain.LinkedSpan;

/**
 * Generates a score based on clustering of points in the document. Process:
 * takes each lat long and converts to interwoven geohash transposed to an all
 * positive linear space. Points are then sorted and binned. Scoring is based on
 * which bin the point is in, based on which bins are the most prominent.
 *
 */
public class GeoHashBinScorer {

  public List<LinkedSpan> score(List<LinkedSpan> geospans) {
    Map<Double, Double> latLongs = new HashMap<Double, Double>();
    /**
     * collect all the lat longs
     */
    for (LinkedSpan<BaseLink> ls : geospans) {
      for (BaseLink bl : ls.getLinkedEntries()) {
        if (bl instanceof MySQLGeoNamesGazEntry) {
          MySQLGeoNamesGazEntry entry = (MySQLGeoNamesGazEntry) bl;
          latLongs.put(entry.getLATITUDE(), entry.getLONGITUDE());
        }
        if (bl instanceof MySQLUSGSGazEntry) {
          MySQLUSGSGazEntry entry = (MySQLUSGSGazEntry) bl;
          latLongs.put(entry.getPrimarylatitudeDEC(), entry.getPrimarylongitudeDEC());
        }
      }
    }

    /**
     * convert to geohash and add to sortedset
     */
    TreeSet<Long> geoHashes = new TreeSet<Long>();
    for (Map.Entry<Double, Double> entry : latLongs.entrySet()) {
      geoHashes.add(geoHash(entry.getKey(), entry.getValue()));
    }
    /**
     * bin the points and generate a scoremap
     */
    Map<Long, Set<Long>> bins = bin(geoHashes);
    Map<Long, Double> scores = getScore((TreeMap<Long, Set<Long>>) bins);
    /**
     * iterate over the data again and assign the score based on the bins
     */
    for (LinkedSpan<BaseLink> ls : geospans) {
      for (BaseLink bl : ls.getLinkedEntries()) {
        Long geohash = -1L;
        Double score = 0d;
        if (bl instanceof MySQLGeoNamesGazEntry) {
          MySQLGeoNamesGazEntry entry = (MySQLGeoNamesGazEntry) bl;
          geohash = geoHash(entry.getLATITUDE(), entry.getLONGITUDE());
        }
        if (bl instanceof MySQLUSGSGazEntry) {
          MySQLUSGSGazEntry entry = (MySQLUSGSGazEntry) bl;
          geohash = geoHash(entry.getPrimarylatitudeDEC(), entry.getPrimarylongitudeDEC());
        }
        if (scores.containsKey(geohash)) {
          score = scores.get(geohash);

        } else {
          for (Long bin : bins.keySet()) {
            if (bin == geohash || bins.get(bin).contains(geohash)) {
              score = scores.get(bin);
              break;
            }
          }
        }
        bl.getScoreMap().put("geohashbin", score);
      }
    }

    return geospans;
  }

  private Long normalize(Double coordpart, Boolean isLat) {
    Integer add = isLat ? 90 : 180;
    coordpart = Math.abs(coordpart + add);
    coordpart = coordpart * 1000000;

    Long l = Math.round(coordpart);
    String coord = String.valueOf(l);
    if (coord.length() < 8) {
      while (coord.length() < 8) {
        coord += "0";
      }
    }
    coord = coord.substring(0, 8);
    l = Long.valueOf(coord);
    return l;
  }

  /**
   * interleaves a lat and a long to place the coordinate in linear sortable
   * space for binning simplicity
   *
   * @param lat
   * @param lon
   * @return
   */
  private Long geoHash(double lat, double lon) {
    Long normLat = normalize(lat, Boolean.TRUE);
    Long normLon = normalize(lon, Boolean.FALSE);
    String sLat = String.valueOf(normLat);
    String sLon = String.valueOf(normLon);
    char[] latInts = sLat.toCharArray();
    char[] lonInts = sLon.toCharArray();
    String geoHash = "";
    int len = latInts.length > lonInts.length ? lonInts.length : latInts.length;
    for (int i = 0; i < len - 1; i++) {
      String a = String.valueOf(latInts[i]);
      String b = String.valueOf(lonInts[i]);
      geoHash += a + b;
    }
//223100120000

    return Long.valueOf(geoHash);
  }

  private Map<Long, Set<Long>> bin(TreeSet<Long> sets) {
    ArrayList<Long> list = new ArrayList<Long>(sets);
    ArrayList<Long> diffs = new ArrayList<Long>();
    for (int i = 0; i < list.size() - 1; i++) {
      Long n = list.get(i + 1);
      Long v = list.get(i);

    //  Long percent = 100 - (v / n * 100);
//n * 100 / v;
      diffs.add(n-v);

    }
    Long sum = 0L;
    for (Long l : diffs) {
      sum += l;
    }
    Double avg = (double) sum / diffs.size();

    TreeSet<Long> breaks = new TreeSet<Long>();
    for (int i = 0; i < list.size() - 1; i++) {
      Long n = list.get(i + 1);
      Long v = list.get(i);
      //Long percent = 100 - (v / n * 100);
      long diff = n-v;
      if (diff > avg) {
        breaks.add(v);
      }

    }
    TreeMap<Long, Set<Long>> binToAmount = new TreeMap<Long, Set<Long>>();

    Long lastBreak = -1L;
    for (Long br : breaks) {
      if (lastBreak == -1L) {
        binToAmount.put(br, sets.subSet(0L, true, br, false));
      } else {
        binToAmount.put(br, sets.subSet(lastBreak, true, br, false));
      }
      lastBreak = br;

    }
    lastBreak = sets.higher(lastBreak);
    if (lastBreak != null) {
      binToAmount.put(lastBreak, sets.subSet(lastBreak, true, sets.last(), true));
      if (binToAmount.get(lastBreak).isEmpty()) {
        binToAmount.get(lastBreak).add(lastBreak);
      }
    }
    return binToAmount;
  }

  /**
   * returns a map of geohashes and their score
   *
   * @param binToAmount
   * @return Map< Geohash, score>
   */
  private Map<Long, Double> getScore(TreeMap<Long, Set<Long>> binToAmount) {
    TreeMap<Long, Double> ranks = new TreeMap<Long, Double>();
    TreeMap<Long, Double> normRanks = new TreeMap<Long, Double>();
    if (binToAmount.keySet().size() == 1 || binToAmount.keySet().isEmpty()) {
      for (Long bin : binToAmount.keySet()) {
        for (Long hash : binToAmount.get(bin)) {
          ranks.put(bin, 1d);
        }
      }
      return ranks;
    }
    int total = 0;

    for (Set<Long> geohashes : binToAmount.values()) {
      total += geohashes.size();
    }
    /**
     * get a total, divide total by bin size, largest bin size gets best score,
     * everything in that bin gets that score,
     */
    TreeSet<Double> rankSet = new TreeSet<Double>();
    for (Long key : binToAmount.keySet()) {
      int size = binToAmount.get(key).size();
      Double rank = (double) total / size;
      rankSet.add(rank);
      ranks.put(key, rank);
    }

    for (Map.Entry<Long, Double> rank : ranks.entrySet()) {
      double norm = normalize(rank.getValue(), rankSet.first() + .1, rankSet.last() + .1);
      double reverse = Math.abs(norm - 1);
      double score = reverse > 1d ? 1.0 : reverse;
      normRanks.put(rank.getKey(), score);
    }

    return normRanks;
  }

  private Double normalize(Double valueToNormalize, Double minimum, Double maximum) {
    Double d = (double) ((1 - 0) * (valueToNormalize - minimum)) / (maximum - minimum) + 0;
    d = d == null ? 0d : d;
    return d;
  }
}
