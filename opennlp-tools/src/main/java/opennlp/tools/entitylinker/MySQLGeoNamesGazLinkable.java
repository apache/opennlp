package opennlp.tools.entitylinker;

/**
 *
 * @author Owner
 */
import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import opennlp.tools.entitylinker.domain.BaseLink;
import opennlp.tools.util.Span;

/**
 *
 *
 */
public final class MySQLGeoNamesGazLinkable {

  private Connection con;
  private Boolean filterCountryContext;

  public MySQLGeoNamesGazLinkable() {
  }

  public ArrayList<BaseLink> find(String locationText, Span span, List<CountryContextHit> countryHits, EntityLinkerProperties properties) {
    ArrayList<BaseLink> returnlocs = new ArrayList<BaseLink>();

    try {
      if (con == null) {
        con = getMySqlConnection(properties);
      }
      //   pull from config to utilize country context filtering
      filterCountryContext = Boolean.valueOf(properties.getProperty("geoentitylinker.filter_by_country_context", "false"));

      Set<String> countrycodes = getCountryCodes(countryHits);
      String thresh = properties.getProperty("mysqlusgsgazscorethresh", "25");
      int threshhold = -1;
      if (!thresh.matches("[azAZ]")) {
        threshhold = Integer.valueOf(thresh);
      }
      returnlocs.addAll(this.searchGaz(locationText, threshhold, countrycodes, properties));


    } catch (Exception ex) {
      Logger.getLogger(MySQLUSGSGazLinkable.class.getName()).log(Level.SEVERE, null, ex);
    }
    return returnlocs;
  }

  protected Connection getMySqlConnection(EntityLinkerProperties property) throws Exception {
   // EntityLinkerProperties property = new EntityLinkerProperties(new File("c:\\temp\\opennlpmodels\\entitylinker.properties"));
    String driver = property.getProperty("mysql.driver", "org.gjt.mm.mysql.Driver");
    String url = property.getProperty("mysql.url", "jdbc:mysql://localhost:3306/world");
    String username = property.getProperty("mysql.username", "root");
    String password = property.getProperty("mysql.password", "?");

    Class.forName(driver);
    Connection conn = DriverManager.getConnection(url, username, password);
    return conn;
  }

  public ArrayList<MySQLGeoNamesGazEntry> searchGaz(String searchString, int matchthresh, Set<String> countryCodes, EntityLinkerProperties properties) throws SQLException, Exception {

    if (con.isClosed()) {
      con = getMySqlConnection(properties);
    }
    CallableStatement cs;
    cs = con.prepareCall("CALL `search_geonames`(?, ?)");
    cs.setString(1, this.format(searchString));
    cs.setInt(2, matchthresh);
    ArrayList<MySQLGeoNamesGazEntry> retLocs = new ArrayList<MySQLGeoNamesGazEntry>();
    ResultSet rs;
    try {
      rs = cs.executeQuery();

      if (rs == null) {
        return retLocs;
      }

      while (rs.next()) {
        MySQLGeoNamesGazEntry s = new MySQLGeoNamesGazEntry();
        rs.getDouble(2);
        //ufi      
        s.setUFI(rs.getString(1));
//latitude, 
        s.setLATITUDE(rs.getDouble(2));
//longitude, 
        s.setLONGITUDE(rs.getDouble(3));
//cc1,
        s.setCC1(rs.getString(4));
//adm1, 
        s.setADM1(rs.getString(5));
//dsg,
        s.setDSG(rs.getString(6));
//SHORT_FORM ,
        s.setSHORT_FORM(rs.getString(7));
//	SORT_NAME_RO ,
        s.setSORT_NAME_RO(rs.getString(8));
//	FULL_NAME_RO ,
        s.setFULL_NAME_RO(rs.getString(9));
//	FULL_NAME_ND_RO ,
        s.setFULL_NAME_ND_RO(rs.getString(10));
//	SORT_NAME_RG ,
        s.setSORT_NAME_RG(rs.getString(11));
//	FULL_NAME_RG ,
        s.setFULL_NAME_RG(rs.getString(12));
//	FULL_NAME_ND_RG ,
        s.setFULL_NAME_ND_RG(rs.getString(13));

        s.setRank(rs.getDouble(14));

        if (filterCountryContext) {
          if (countryCodes.contains(s.getCC1().toLowerCase())) {
          //  System.out.println(searchString +" GeoNames qualified on: " + s.getCC1());
            s.setRank(s.getRank() + 1.0);
          } else {
         //    System.out.println(s.getFULL_NAME_ND_RO() + ", with CC1 of "+ s.getCC1()+ ", is not within countries discovered in the document. The Country list used to discover countries can be modified in mysql procedure getCountryList()");
            continue;
          }
        }

        retLocs.add(s);
      }

    } catch (SQLException ex) {
      throw ex;
    } catch (Exception e) {
      System.err.println(e);
    } finally {
      con.close();
    }

    return retLocs;
  }

  private Set<String> getCountryCodes(List<CountryContextHit> hits) {
    Set<String> ccs = new HashSet<String>();
    for (CountryContextHit hit : hits) {
      ccs.add(hit.getCountryCode().toLowerCase());
    }
    return ccs;
  }

  public String format(String entity) {
    return "\"" + entity + "\"";
  }
}
