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
import java.util.logging.Level;
import java.util.logging.Logger;
import opennlp.tools.entitylinker.domain.BaseLink;
import opennlp.tools.util.Span;

/**
 * Links names to the USGS gazateer that resides in a database
 */
public class MySQLUSGSGazLinkable {

  private Connection con;

  public MySQLUSGSGazLinkable() {
  }

  public ArrayList<BaseLink> find(String locationText, Span span, EntityLinkerProperties properties) {
    ArrayList<BaseLink> returnlocs = new ArrayList<BaseLink>();
    try {

      if (con == null) {
        con = getMySqlConnection(properties);
      }
      String thresh = properties.getProperty("usgs.gaz.rowsreturned", "5");
      int threshhold = -1;
      if (!thresh.matches("[azAZ]")) {
        threshhold = Integer.valueOf(thresh);
      }
      returnlocs.addAll(this.searchGaz(locationText, threshhold, properties));

    } catch (Exception ex) {
      Logger.getLogger(MySQLUSGSGazLinkable.class.getName()).log(Level.SEVERE, null, ex);
    }

    return returnlocs;
  }

  private Connection getMySqlConnection(EntityLinkerProperties properties) throws Exception {
    String driver = properties.getProperty("db.driver", "org.gjt.mm.mysql.Driver");
    String url = properties.getProperty("db.url", "jdbc:mysql://127.0.0.1:3306/world");
    String username = properties.getProperty("db.username", "root");
    String password = properties.getProperty("db.password", "?");

    Class.forName(driver);
    Connection conn = DriverManager.getConnection(url, username, password);
    return conn;
  }

  /**
   *
   * @param searchString the name to look up in the gazateer
   * @param rowsReturned number of rows to return
   * @param properties   EntityLinkerProperties that identifies the database
   *                     connection properties
   *
   * @return
   * @throws SQLException
   * @throws Exception
   */
  public ArrayList<MySQLUSGSGazEntry> searchGaz(String searchString, int rowsReturned, EntityLinkerProperties properties) throws SQLException, Exception {
    if (con.isClosed()) {
      con = getMySqlConnection(properties);
    }
    CallableStatement cs;
    cs = con.prepareCall("CALL `search_gaz`(?, ?)");
    cs.setString(1, this.format(searchString));
    cs.setInt(2, rowsReturned);
    ArrayList<MySQLUSGSGazEntry> toponyms = new ArrayList<MySQLUSGSGazEntry>();
    ResultSet rs;
    try {
      rs = cs.executeQuery();

      if (rs == null) {
        return toponyms;
      }

      while (rs.next()) {
        MySQLUSGSGazEntry s = new MySQLUSGSGazEntry();
        s.setRank(rs.getDouble(1));
        s.setFeatureid(String.valueOf(rs.getLong(2)));
        s.setFeaturename(rs.getString(3));
        s.setFeatureclass(rs.getString(4));
        s.setStatealpha(rs.getString(5));
        s.setPrimarylatitudeDEC(rs.getDouble(6));
        s.setPrimarylongitudeDEC(rs.getDouble(7));
        s.setMapname(rs.getString(8));

        //set the baselink data
        s.setItemName(s.getFeaturename().toLowerCase().trim());
        s.setItemID(s.getFeatureid());
        s.setItemType(s.getFeatureclass());
        s.setItemParentID("us");
        s.getScoreMap().put("dbfulltext", s.getRank());
        toponyms.add(s);
      }

    } catch (SQLException ex) {
      throw ex;
    } catch (Exception e) {
      System.err.println(e);
    } finally {
      con.close();
    }

    return toponyms;
  }

  public String format(String entity) {
    return "\"" + entity + "\"";
  }
}
