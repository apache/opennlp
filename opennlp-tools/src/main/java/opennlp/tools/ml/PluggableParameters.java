package opennlp.tools.ml;

import java.util.Map;

public final class PluggableParameters {

  private Map<String, String> parameterMap;
  private Map<String, String> reportMap;

  public PluggableParameters(Map<String,String> parameterMap,Map<String,String> reportMap) {
    this.parameterMap = parameterMap;
    this.reportMap = reportMap;
  }

  public String getStringParam(String key, String defaultValue) {

    String valueString = parameterMap.get(key);

    if (valueString == null)
      valueString = defaultValue;

    if (reportMap != null)
      reportMap.put(key, valueString);

    return valueString;
  }

  public int getIntParam(String key, int defaultValue) {

    String valueString = parameterMap.get(key);

    if (valueString != null)
      return Integer.parseInt(valueString);
    else
      return defaultValue;
  }

  public double getDoubleParam(String key, double defaultValue) {

    String valueString = parameterMap.get(key);

    if (valueString != null)
      return Double.parseDouble(valueString);
    else
      return defaultValue;
  }

  public boolean getBooleanParam(String key, boolean defaultValue) {

    String valueString = parameterMap.get(key);

    if (valueString != null)
      return Boolean.parseBoolean(valueString);
    else
      return defaultValue;
  }

  public void addToReport(String key, String value) {
    if (reportMap != null) {
      reportMap.put(key, value);
    }
  }

  public Map<String, String> getReportMap() {
    return reportMap;
  }
}
