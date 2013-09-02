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
import java.util.logging.Level;
import java.util.logging.Logger;
import opennlp.tools.entitylinker.domain.BaseLink;
import opennlp.tools.entitylinker.domain.LinkedSpan;
import opennlp.tools.util.Span;

/**
 * Links location entities to gazatteers.
 *
 *
 */
public class GeoEntityLinker implements EntityLinker<LinkedSpan> {

  private MySQLGeoNamesGazLinkable geoNamesGaz;// = new MySQLGeoNamesGazLinkable();
  private MySQLUSGSGazLinkable usgsGaz;//= new MySQLUSGSGazLinkable();
  private CountryContext countryContext;
  private List<CountryContextHit> hits;
  private EntityLinkerProperties props;

  public GeoEntityLinker() {
    if (geoNamesGaz == null || usgsGaz == null) {
      geoNamesGaz = new MySQLGeoNamesGazLinkable();
      usgsGaz = new MySQLUSGSGazLinkable();
      countryContext = new CountryContext();

    }
  }

  public List<LinkedSpan> find(String text, Span[] sentences, String[] tokens, Span[] names) {
    ArrayList<LinkedSpan> spans = new ArrayList<LinkedSpan>();
    try {
      if (props == null) {
        props = new EntityLinkerProperties(new File("C:\\temp\\opennlpmodels\\entitylinker.properties"));
      }
      if (hits == null) {
        System.out.println("getting country context");
        hits = countryContext.find(text, props);
      }

      String[] matches = Span.spansToStrings(names, tokens);
      for (int i = 0; i < matches.length; i++) {
        System.out.println("processing match " + i + " of " + matches.length);
        ArrayList<BaseLink> geoNamesEntries = geoNamesGaz.find(matches[i], names[i], hits, props);
        ArrayList<BaseLink> usgsEntries = usgsGaz.find(matches[i], names[i], hits, props);
        LinkedSpan<BaseLink> geoSpans = new LinkedSpan<BaseLink>(geoNamesEntries, names[i].getStart(), names[i].getEnd());
        geoSpans.getLinkedEntries().addAll(usgsEntries);
        geoSpans.setSearchTerm(matches[i]);
        spans.add(geoSpans);
      }
      return spans;
    } catch (IOException ex) {
      Logger.getLogger(GeoEntityLinker.class.getName()).log(Level.SEVERE, null, ex);
    }
    return spans;
  }

  public List<LinkedSpan> find(String text, Span[] sentences, Span[] tokens, Span[] names) {
    ArrayList<LinkedSpan> spans = new ArrayList<LinkedSpan>();
    try {


      if (props == null) {
        props = new EntityLinkerProperties(new File("C:\\temp\\opennlpmodels\\entitylinker.properties"));
      }
      List<CountryContextHit> hits = countryContext.find(text, props);
      //get the sentence text....must assume some index
      Span s = sentences[0];
      String sentence = text.substring(s.getStart(), s.getEnd());

      String[] stringtokens = Span.spansToStrings(tokens, sentence);
      //get the names based on the tokens
      String[] matches = Span.spansToStrings(names, stringtokens);
      for (int i = 0; i < matches.length; i++) {
        ArrayList<BaseLink> geoNamesEntries = geoNamesGaz.find(matches[i], names[i], hits, props);
        ArrayList<BaseLink> usgsEntries = usgsGaz.find(matches[i], names[i], hits, props);
        LinkedSpan<BaseLink> geoSpans = new LinkedSpan<BaseLink>(geoNamesEntries, names[i], 0);
        geoSpans.getLinkedEntries().addAll(usgsEntries);
        geoSpans.setSearchTerm(matches[i]);
        spans.add(geoSpans);
      }
      return spans;
    } catch (IOException ex) {
      Logger.getLogger(GeoEntityLinker.class.getName()).log(Level.SEVERE, null, ex);
    }
    return spans;
  }

  public List<LinkedSpan> find(String text, Span[] sentences, Span[] tokens, Span[] names, int sentenceIndex) {
    ArrayList<LinkedSpan> spans = new ArrayList<LinkedSpan>();
    try {

      if (props == null) {
        props = new EntityLinkerProperties(new File("C:\\temp\\opennlpmodels\\entitylinker.properties"));
      }
      List<CountryContextHit> hits = countryContext.find(text, props);

      Span s = sentences[sentenceIndex];
      String sentence = text.substring(s.getStart(), s.getEnd());

      String[] stringtokens = Span.spansToStrings(tokens, sentence);
      //get the names based on the tokens
      String[] matches = Span.spansToStrings(names, stringtokens);

      for (int i = 0; i < matches.length; i++) {
        ArrayList<BaseLink> geoNamesEntries = geoNamesGaz.find(matches[i], names[i], hits, props);
        ArrayList<BaseLink> usgsEntries = usgsGaz.find(matches[i], names[i], hits, props);
        LinkedSpan<BaseLink> geoSpans = new LinkedSpan<BaseLink>(geoNamesEntries, names[i], 0);
        geoSpans.getLinkedEntries().addAll(usgsEntries);
        geoSpans.setSearchTerm(matches[i]);
        geoSpans.setSentenceid(sentenceIndex);
        spans.add(geoSpans);
      }

    } catch (IOException ex) {
      Logger.getLogger(GeoEntityLinker.class.getName()).log(Level.SEVERE, null, ex);
    }
    return spans;
  }

  public void setEntityLinkerProperties(EntityLinkerProperties properties) {
    this.props = properties;
  }
}
