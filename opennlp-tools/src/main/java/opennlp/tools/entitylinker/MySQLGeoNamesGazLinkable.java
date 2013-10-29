package opennlp.tools.entitylinker;

import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import opennlp.tools.entitylinker.domain.BaseLink;
import opennlp.tools.util.Span;

/**
 *
 * Links names to the NGA gazateer
 */
public final class MySQLGeoNamesGazLinkable {

  private Connection con;
  private Boolean filterCountryContext;

  public MySQLGeoNamesGazLinkable() {
  }

  public ArrayList<BaseLink> find(String locationText, Span span, Map<String, Set<Integer>> countryHits, EntityLinkerProperties properties) {
    ArrayList<BaseLink> returnlocs = new ArrayList<BaseLink>();

    try {
      if (con == null) {
        con = getMySqlConnection(properties);
      }
      //   pull from config to utilize country context filtering
      filterCountryContext = Boolean.valueOf(properties.getProperty("geoentitylinker.filter_by_country_context", "false"));


      String thresh = properties.getProperty("geonames.gaz.rowsreturned", "200");
      int threshhold = -1;
      if (!thresh.matches("[azAZ]")) {
        threshhold = Integer.valueOf(thresh);
      }
      /**
       * Because we need equal amount of candidate toponyms from each country
       * code, we iterate over the set of codes and do a search with the name
       * for each code returning n rows based on threah
       */
      for (String code : countryHits.keySet()) {
        returnlocs.addAll(this.searchGaz(locationText, threshhold, code, properties));
      }
    } catch (Exception ex) {
      Logger.getLogger(MySQLUSGSGazLinkable.class.getName()).log(Level.SEVERE, null, ex);
    }
    return returnlocs;
  }

  private Connection getMySqlConnection(EntityLinkerProperties property) throws Exception {
    // EntityLinkerProperties property = new EntityLinkerProperties(new File("c:\\temp\\opennlpmodels\\entitylinker.properties"));
    String driver = property.getProperty("db.driver", "org.gjt.mm.mysql.Driver");
    String url = property.getProperty("db.url", "jdbc:mysql://localhost:3306/world");
    String username = property.getProperty("db.username", "root");
    String password = property.getProperty("db.password", "?");

    Class.forName(driver);
    Connection conn = DriverManager.getConnection(url, username, password);
    return conn;
  }

  /**
   *
   * @param searchString the name to look up in the gazateer
   * @param rowsReturned number of rows to return
   * @param code         the two digit country code
   * @param properties   EntityLinkerProperties that identifies the database
   *                     connection properties
   * @return
   * @throws SQLException
   * @throws Exception
   */
  public ArrayList<MySQLGeoNamesGazEntry> searchGaz(String searchString, int rowsReturned, String code, EntityLinkerProperties properties) throws SQLException, Exception {

    if (con.isClosed()) {
      con = getMySqlConnection(properties);
    }
    CallableStatement cs;
    cs = con.prepareCall("CALL `search_geonames`(?, ?, ?)");
    cs.setString(1, this.format(searchString));
    cs.setInt(2, rowsReturned);
    if (filterCountryContext) {
      cs.setString(3, code);
    } else {
      cs.setString(3, "");
    }

    ArrayList<MySQLGeoNamesGazEntry> toponyms = new ArrayList<MySQLGeoNamesGazEntry>();
    ResultSet rs;
    try {
      rs = cs.executeQuery();

      if (rs == null) {
        return toponyms;
      }

      while (rs.next()) {
        MySQLGeoNamesGazEntry s = new MySQLGeoNamesGazEntry();
        rs.getDouble(2);
        s.setUFI(rs.getString(1));
        s.setLATITUDE(rs.getDouble(2));
        s.setLONGITUDE(rs.getDouble(3));
        s.setCC1(rs.getString(4));
        s.setADM1(rs.getString(5));
        s.setDSG(rs.getString(6));
        s.setSHORT_FORM(rs.getString(7));
        s.setSORT_NAME_RO(rs.getString(8));
        s.setFULL_NAME_RO(rs.getString(9));
        s.setFULL_NAME_ND_RO(rs.getString(10));
        s.setSORT_NAME_RG(rs.getString(11));
        s.setFULL_NAME_RG(rs.getString(12));
        s.setFULL_NAME_ND_RG(rs.getString(13));
        s.setRank(rs.getDouble(14));

        //set the base link data
        s.setItemName(s.getFULL_NAME_ND_RO().toLowerCase().trim());
        s.setItemID(s.getUFI());
        s.setItemType(s.getDSG());
        s.setItemParentID(s.getCC1().toLowerCase());
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
