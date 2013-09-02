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
public class MySQLGeoNamesGazEntry extends BaseLink
{
  ////actual fields returned
//ufi, 
//latitude, 
//longitude, 
//cc1,
//adm1, 
//dsg,
//SHORT_FORM ,
//	SORT_NAME_RO ,
//	FULL_NAME_RO ,
//	FULL_NAME_ND_RO ,
//	SORT_NAME_RG ,
//	FULL_NAME_RG ,
//	FULL_NAME_ND_RG ,
//match(`SHORT_FORM` ,`SORT_NAME_RO`,`FULL_NAME_RO`,`FULL_NAME_ND_RO` ,`SORT_NAME_RG` ,`FULL_NAME_RG` ,`FULL_NAME_ND_RG`) 
//against(pSearch in natural language mode) as rank
  
  ///////
  
 // private String RC;// VARCHAR(150) NULL DEFAULT NULL,
  private String UFI;
  //private String UNI;
  private Double LATITUDE; //DOUBLE NULL DEFAULT NULL,
  private Double LONGITUDE;// DOUBLE NULL DEFAULT NULL,
 // private String DMS_LAT;// VARCHAR(150) NULL DEFAULT NULL,
 // private String DMS_LONG;// VARCHAR(150) NULL DEFAULT NULL,
 // private String MGRS;// VARCHAR(150) NULL DEFAULT NULL,
//  private String JOG;// VARCHAR(150) NULL DEFAULT NULL,
 // private String FC;// VARCHAR(150) NULL DEFAULT NULL,
  private String DSG;// VARCHAR(150) NULL DEFAULT NULL,
 // private String PC;// VARCHAR(150) NULL DEFAULT NULL,
  private String CC1;//` VARCHAR(150) NULL DEFAULT NULL,
  private String ADM1;// VARCHAR(150) NULL DEFAULT NULL,
 // private String POP;// VARCHAR(150) NULL DEFAULT NULL,
  //private String ELEV;//VARCHAR(150) NULL DEFAULT NULL,
//  private String CC2;// VARCHAR(150) NULL DEFAULT NULL,
 // private String NT;//VARCHAR(150) NULL DEFAULT NULL,
 // private String LC;// VARCHAR(150) NULL DEFAULT NULL,
  private String SHORT_FORM;// VARCHAR(500) NULL DEFAULT NULL,
 // private String GENERIC;// VARCHAR(150) NULL DEFAULT NULL,
  private String SORT_NAME_RO;//VARCHAR(500) NULL DEFAULT NULL,
  private String FULL_NAME_RO;// VARCHAR(500) NULL DEFAULT NULL,
  private String FULL_NAME_ND_RO;// VARCHAR(500) NULL DEFAULT NULL,
  private String SORT_NAME_RG;// VARCHAR(500) NULL DEFAULT NULL,
  private String FULL_NAME_RG;// VARCHAR(500) NULL DEFAULT NULL,
  private String FULL_NAME_ND_RG;// VARCHAR(500) NULL DEFAULT NULL,
//  private String NOTE;//VARCHAR(500) NULL DEFAULT NULL,
 // private String MODIFY_DATE;// VARCHAR(150) NULL DEFAULT NULL,
private Double rank;

  public String getUFI()
  {
    return UFI;
  }

  public void setUFI(String UFI)
  {
    this.UFI = UFI;
  }

  public Double getLATITUDE()
  {
    return LATITUDE;
  }

  public void setLATITUDE(Double LATITUDE)
  {
    this.LATITUDE = LATITUDE;
  }

  public Double getLONGITUDE()
  {
    return LONGITUDE;
  }

  public void setLONGITUDE(Double LONGITUDE)
  {
    this.LONGITUDE = LONGITUDE;
  }

  public String getDSG()
  {
    return DSG;
  }

  public void setDSG(String DSG)
  {
    this.DSG = DSG;
  }

  public String getCC1()
  {
    return CC1;
  }

  public void setCC1(String CC1)
  {
    this.CC1 = CC1;
  }

  public String getADM1()
  {
    return ADM1;
  }

  public void setADM1(String ADM1)
  {
    this.ADM1 = ADM1;
  }

  public String getSHORT_FORM()
  {
    return SHORT_FORM;
  }

  public void setSHORT_FORM(String SHORT_FORM)
  {
    this.SHORT_FORM = SHORT_FORM;
  }

  public String getSORT_NAME_RO()
  {
    return SORT_NAME_RO;
  }

  public void setSORT_NAME_RO(String SORT_NAME_RO)
  {
    this.SORT_NAME_RO = SORT_NAME_RO;
  }

  public String getFULL_NAME_RO()
  {
    return FULL_NAME_RO;
  }

  public void setFULL_NAME_RO(String FULL_NAME_RO)
  {
    this.FULL_NAME_RO = FULL_NAME_RO;
  }

  public String getFULL_NAME_ND_RO()
  {
    return FULL_NAME_ND_RO;
  }

  public void setFULL_NAME_ND_RO(String FULL_NAME_ND_RO)
  {
    this.FULL_NAME_ND_RO = FULL_NAME_ND_RO;
  }

  public String getSORT_NAME_RG()
  {
    return SORT_NAME_RG;
  }

  public void setSORT_NAME_RG(String SORT_NAME_RG)
  {
    this.SORT_NAME_RG = SORT_NAME_RG;
  }

  public String getFULL_NAME_RG()
  {
    return FULL_NAME_RG;
  }

  public void setFULL_NAME_RG(String FULL_NAME_RG)
  {
    this.FULL_NAME_RG = FULL_NAME_RG;
  }

  public String getFULL_NAME_ND_RG()
  {
    return FULL_NAME_ND_RG;
  }

  public void setFULL_NAME_ND_RG(String FULL_NAME_ND_RG)
  {
    this.FULL_NAME_ND_RG = FULL_NAME_ND_RG;
  }

  public Double getRank()
  {
    return rank;
  }

  public void setRank(Double rank)
  {
    this.rank = rank;
  }

  @Override
  public String toString() {
    return "MySQLGeoNamesGazEntry{" + "UFI=" + UFI + ", LATITUDE=" + LATITUDE + ", LONGITUDE=" + LONGITUDE + ", DSG=" + DSG + ", CC1=" + CC1 + ", ADM1=" + ADM1 + ", SHORT_FORM=" + SHORT_FORM + ", SORT_NAME_RO=" + SORT_NAME_RO + ", FULL_NAME_RO=" + FULL_NAME_RO + ", FULL_NAME_ND_RO=" + FULL_NAME_ND_RO + ", SORT_NAME_RG=" + SORT_NAME_RG + ", FULL_NAME_RG=" + FULL_NAME_RG + ", FULL_NAME_ND_RG=" + FULL_NAME_ND_RG + ", rank=" + rank + "}\n\n";
  }

  
}
