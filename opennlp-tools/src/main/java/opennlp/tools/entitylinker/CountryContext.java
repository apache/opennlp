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
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *Finds instances of country mentions in a String, typically a document text.
 * Used to boost or degrade scoring of linked geo entities

 */
public class CountryContext {

  private Connection con;
  private List<CountryContextEntry> countrydata;

  public CountryContext() {
  }

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
          CountryContextHit hit = new CountryContextHit(entry.getCc1(), docText.indexOf(entry.getFull_name_nd_ro()), docText.indexOf(entry.getFull_name_nd_ro()+ entry.getFull_name_nd_ro().length()));
          hits.add(hit);
        }
      }

    } catch (Exception ex) {
      Logger.getLogger(CountryContext.class.getName()).log(Level.SEVERE, null, ex);
    }
    return hits;
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
}
