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

import opennlp.tools.entitylinker.domain.BaseLink;

/**
 *

 */
public class MySQLUSGSGazEntry extends BaseLink
{

  private double rank;
  private String featureid;
  private String featurename;
  private String featureclass;
  private String statealpha;
  private double primarylatitudeDEC;
  private double primarylongitudeDEC;
  private String mapname;

  public double getRank()
  {
    return rank;
  }

  public void setRank(double rank)
  {
    this.rank = rank;
  }

  public String getFeatureid()
  {
    return featureid;
  }

  public void setFeatureid(String featureid)
  {
    this.featureid = featureid;
  }

  public String getFeaturename()
  {
    return featurename;
  }

  public void setFeaturename(String featurename)
  {
    this.featurename = featurename;
  }

  public String getFeatureclass()
  {
    return featureclass;
  }

  public void setFeatureclass(String featureclass)
  {
    this.featureclass = featureclass;
  }

  public String getStatealpha()
  {
    return statealpha;
  }

  public void setStatealpha(String statealpha)
  {
    this.statealpha = statealpha;
  }

  public double getPrimarylatitudeDEC()
  {
    return primarylatitudeDEC;
  }

  public void setPrimarylatitudeDEC(double primarylatitudeDEC)
  {
    this.primarylatitudeDEC = primarylatitudeDEC;
  }

  public double getPrimarylongitudeDEC()
  {
    return primarylongitudeDEC;
  }

  public void setPrimarylongitudeDEC(double primarylongitudeDEC)
  {
    this.primarylongitudeDEC = primarylongitudeDEC;
  }

  public String getMapname()
  {
    return mapname;
  }

  public void setMapname(String mapname)
  {
    this.mapname = mapname;
  }

  @Override
  public String toString() {
    return "MySQLUSGSGazEntry{" + "rank=" + rank + ", featureid=" + featureid + ", featurename=" + featurename + ", featureclass=" + featureclass + ", statealpha=" + statealpha + ", primarylatitudeDEC=" + primarylatitudeDEC + ", primarylongitudeDEC=" + primarylongitudeDEC + ", mapname=" + mapname + "}\n\n";
  }
  
}
