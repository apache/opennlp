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

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import opennlp.tools.entitylinker.domain.BaseLink;
import opennlp.tools.entitylinker.domain.LinkedSpan;
import opennlp.tools.util.Span;

/**
 * Links location entities to gazatteers. Currently supports gazateers in a
 * MySql database (NGA and USGS)
 *
 *
 */
public class GeoEntityLinker implements EntityLinker<LinkedSpan> {

  // CountryProximityScorer scorer = new CountryProximityScorer();
  private MySQLGeoNamesGazLinkable geoNamesGaz;// = new MySQLGeoNamesGazLinkable();
  private MySQLUSGSGazLinkable usgsGaz;//= new MySQLUSGSGazLinkable();
  private CountryContext countryContext;
  private Map<String, Set<Integer>> countryMentions;
  private EntityLinkerProperties linkerProperties;
  /**
   * Flag for deciding whether to search gaz only for toponyms within countries
   * that are mentioned in the document
   */
  private Boolean filterCountryContext = true;

  public GeoEntityLinker() {
    if (geoNamesGaz == null || usgsGaz == null) {
      geoNamesGaz = new MySQLGeoNamesGazLinkable();
      usgsGaz = new MySQLUSGSGazLinkable();
      countryContext = new CountryContext();

    }
  }

  @Override
  public List<LinkedSpan> find(String doctext, Span[] sentences, String[][] tokensBySentence, Span[][] namesBySentence) {
    ArrayList<LinkedSpan> spans = new ArrayList<LinkedSpan>();
    try {
      if (linkerProperties == null) {
        linkerProperties = new EntityLinkerProperties(new File("C:\\temp\\opennlpmodels\\entitylinker.properties"));
      }
      countryMentions = countryContext.regexfind(doctext, linkerProperties);
      //prioritize query
      filterCountryContext = Boolean.valueOf(linkerProperties.getProperty("geoentitylinker.filter_by_country_context", "true"));

      for (int s = 0; s < sentences.length; s++) {
        Span[] names = namesBySentence[s];
        String[] tokens = tokensBySentence[s];
        String[] matches = Span.spansToStrings(names, tokens);

        for (int i = 0; i < matches.length; i++) {

//nga gazateer is for other than US placenames, don't use it unless US is a mention in the document
          ArrayList<BaseLink> geoNamesEntries = new ArrayList<BaseLink>();
          if (!(countryMentions.keySet().contains("us") && countryMentions.keySet().size() == 1) || countryMentions.keySet().size() > 1 || countryMentions.keySet().isEmpty()) {
            geoNamesEntries = geoNamesGaz.find(matches[i], names[i], countryMentions, linkerProperties);
          }
          ArrayList<BaseLink> usgsEntries = new ArrayList<BaseLink>();
          if (countryMentions.keySet().contains("us") || countryMentions.keySet().isEmpty()) {
            usgsEntries = usgsGaz.find(matches[i], names[i], linkerProperties);
          }
          LinkedSpan<BaseLink> geoSpan = new LinkedSpan<BaseLink>(geoNamesEntries, names[i].getStart(), names[i].getEnd());

          if (!usgsEntries.isEmpty()) {
            geoSpan.getLinkedEntries().addAll(usgsEntries);
            geoSpan.setSearchTerm(matches[i]);
          }

          if (!geoSpan.getLinkedEntries().isEmpty()) {
            geoSpan.setSearchTerm(matches[i]);
            geoSpan.setSentenceid(s);
            spans.add(geoSpan);
          }
        }
      }
    } catch (IOException ex) {
      Logger.getLogger(GeoEntityLinker.class.getName()).log(Level.SEVERE, null, ex);
    }
    List<LinkedEntityScorer<CountryContext>> scorers = new ArrayList<>();
    scorers.add(new GeoHashBinScorer());
    scorers.add(new CountryProximityScorer());
    scorers.add(new FuzzyStringMatchScorer());
    for (LinkedEntityScorer scorer : scorers) {
      scorer.score(spans, doctext, sentences, countryContext);
    }
    return spans;
  }

  @Override
  public void setEntityLinkerProperties(EntityLinkerProperties properties) {
    this.linkerProperties = properties;
  }

  @Override
  public List<LinkedSpan> find(String text, Span[] sentences, Span[] tokens, Span[] nameSpans) {
    throw new UnsupportedOperationException("The GeoEntityLinker requires the entire document for proper scoring. This method is unsupported"); //To change body of generated methods, choose Tools | Templates.
  }

  @Override
  public List<LinkedSpan> find(String text, Span[] sentences, Span[] tokens, Span[] nameSpans, int sentenceIndex) {
    throw new UnsupportedOperationException("The GeoEntityLinker requires the entire document for proper scoring. This method is unsupported"); //To change body of generated methods, choose Tools | Templates.
  }

  @Override
  public List<LinkedSpan> find(String text, Span[] sentences, String[] tokens, Span[] nameSpans) {
    throw new UnsupportedOperationException("The GeoEntityLinker requires the entire document for proper scoring."); //To change body of generated methods, choose Tools | Templates.
  }
}
