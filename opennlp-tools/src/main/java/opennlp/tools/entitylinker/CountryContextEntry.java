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

/**
 *
 *
 */
public class CountryContextEntry {
  /*
   * rc,cc1, full_name_nd_ro,dsg
   */

  private String rc;
  private String cc1;
  private String full_name_nd_ro;
  private String dsg;

  public CountryContextEntry() {
  }

  public CountryContextEntry(String rc, String cc1, String full_name_nd_ro, String dsg) {
    this.rc = rc;
    this.cc1 = cc1;
    this.full_name_nd_ro = full_name_nd_ro;
    this.dsg = dsg;
  }

  public String getRc() {
    return rc;
  }

  public void setRc(String rc) {
    this.rc = rc;
  }

  public String getCc1() {
    return cc1;
  }

  public void setCc1(String cc1) {
    this.cc1 = cc1;
  }

  public String getFull_name_nd_ro() {
    return full_name_nd_ro;
  }

  public void setFull_name_nd_ro(String full_name_nd_ro) {
    this.full_name_nd_ro = full_name_nd_ro;
  }

  public String getDsg() {
    return dsg;
  }

  public void setDsg(String dsg) {
    this.dsg = dsg;
  }
}
