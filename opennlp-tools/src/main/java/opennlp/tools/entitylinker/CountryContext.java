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

import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Finds instances of country mentions in a String, typically a document text.
 * Used to boost or degrade scoring of linked geo entities
 *
 */
public class CountryContext {

  private Connection con;
  private List<CountryContextEntry> countrydata;
  private Map<String, Set<String>> nameCodesMap = new HashMap<String, Set<String>>();
 private Map<String, Set<Integer>> countryMentions = new HashMap<String, Set<Integer>>();

  public Map<String, Set<String>> getNameCodesMap() {
    return nameCodesMap;
  }

  public void setNameCodesMap(Map<String, Set<String>> nameCodesMap) {
    this.nameCodesMap = nameCodesMap;
  }

  public CountryContext() {
  }

  /**
   * use regexFind
   */
  @Deprecated
  public List<CountryContextHit> find(String docText, EntityLinkerProperties properties) {
    List<CountryContextHit> hits = new ArrayList<CountryContextHit>();
    try {
      if (con == null) {
        con = getMySqlConnection(properties);
      }
      if (countrydata == null) {
        countrydata = getCountryData(properties);
      }
      for (CountryContextEntry entry : countrydata) {

        if (docText.contains(entry.getFull_name_nd_ro())) {
          System.out.println("\tFound Country indicator: " + entry.getFull_name_nd_ro());
          CountryContextHit hit = new CountryContextHit(entry.getCc1(), docText.indexOf(entry.getFull_name_nd_ro()), docText.indexOf(entry.getFull_name_nd_ro() + entry.getFull_name_nd_ro().length()));
          hits.add(hit);
        }
      }

    } catch (Exception ex) {
      Logger.getLogger(CountryContext.class.getName()).log(Level.SEVERE, null, ex);
    }
    return hits;

  }

  /**
   * Finds mentions of countries based on a list from MySQL stored procedure
   * called getCountryList. This method finds country mentions in documents,
   * which is an essential element of the scoring that is done for geo
   * linkedspans. Lazily loads the list from the database.
   *
   * @param docText    the full text of the document
   * @param properties EntityLinkerProperties for getting database connection
   * @return
   */
  public Map<String, Set<Integer>> regexfind(String docText, EntityLinkerProperties properties) {
    countryMentions = new HashMap<String, Set<Integer>>();
    nameCodesMap.clear();
    try {
      if (con == null) {
        con = getMySqlConnection(properties);
      }
      if (countrydata == null) {
        countrydata = getCountryData(properties);
      }
      for (CountryContextEntry entry : countrydata) {
        Pattern regex = Pattern.compile(entry.getFull_name_nd_ro(), Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
        Matcher rs = regex.matcher(docText);
        String code = entry.getCc1().toLowerCase();
        while (rs.find()) {
          Integer start = rs.start();
          String hit = rs.group().toLowerCase();
          if (countryMentions.containsKey(code)) {
            countryMentions.get(code).add(start);
          } else {
            Set<Integer> newset = new HashSet<Integer>();
            newset.add(start);
            countryMentions.put(code, newset);
          }
          if (!hit.equals("")) {
            if (this.nameCodesMap.containsKey(hit)) {
              nameCodesMap.get(hit).add(code);
            } else {
              HashSet<String> newset = new HashSet<String>();
              newset.add(code);
              nameCodesMap.put(hit, newset);
            }
          }
        }

      }

    } catch (Exception ex) {
      Logger.getLogger(CountryContext.class.getName()).log(Level.SEVERE, null, ex);
    }


    return countryMentions;
  }

  /**
   * returns a unique list of country codes
   *
   * @param countryMentions the countryMentions discovered
   * @return
   */
  public static Set<String> getCountryCodes(List<CountryContextHit> hits) {
    Set<String> ccs = new HashSet<String>();
    for (CountryContextHit hit : hits) {
      ccs.add(hit.getCountryCode().toLowerCase());
    }
    return ccs;
  }

  public static String getCountryCodeCSV(Set<String> hits) {
    String csv = "";
    if (hits.isEmpty()) {
      return csv;
    }

    for (String code : hits) {
      csv += "," + code;
    }
    return csv.substring(1);
  }

  private Connection getMySqlConnection(EntityLinkerProperties properties) throws Exception {

    String driver = properties.getProperty("mysql.driver", "org.gjt.mm.mysql.Driver");
    String url = properties.getProperty("mysql.url", "jdbc:mysql://localhost:3306/world");
    String username = properties.getProperty("mysql.username", "root");
    String password = properties.getProperty("mysql.password", "559447");

    Class.forName(driver);
    Connection conn = DriverManager.getConnection(url, username, password);
    return conn;
  }

  /**
   * reads the list from the database by calling a stored procedure
   * getCountryList
   *
   * @param properties
   * @return
   * @throws SQLException
   */
  private List<CountryContextEntry> getCountryData(EntityLinkerProperties properties) throws SQLException {
    List<CountryContextEntry> entries = new ArrayList<CountryContextEntry>();
    try {
      if (con == null) {
        con = getMySqlConnection(properties);
      }
      CallableStatement cs;
      cs = con.prepareCall("CALL `getCountryList`()");
      ResultSet rs;
      rs = cs.executeQuery();
      if (rs == null) {
        return entries;
      }
      while (rs.next()) {
        CountryContextEntry s = new CountryContextEntry();
        //rc,cc1, full_name_nd_ro,dsg
        s.setRc(rs.getString(1));
        s.setCc1(rs.getString(2));
//a.district, 
        s.setFull_name_nd_ro(rs.getString(3));
//b.name as countryname, 
        s.setDsg(rs.getString(4));
        entries.add(s);
      }

    } catch (SQLException ex) {
      throw ex;
    } catch (Exception e) {
      System.err.println(e);
    } finally {
      con.close();
    }
    return entries;
  }

  public Map<String, Set<Integer>> getCountryMentions() {
    return countryMentions;
  }

}
