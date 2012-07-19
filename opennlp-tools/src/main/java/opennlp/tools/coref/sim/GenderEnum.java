/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package opennlp.tools.coref.sim;

/**
 * Enumeration of gender types.
 */
public class GenderEnum {
  private String gender;

  /** Male gender. */
  public static final GenderEnum MALE = new GenderEnum("male");
  /** Female gender. */
  public static final GenderEnum FEMALE = new GenderEnum("female");
  /** Nueter gender. */
  public static final GenderEnum NEUTER = new GenderEnum("neuter");
  /** Unknown gender. */
  public static final GenderEnum UNKNOWN = new GenderEnum("unknown");

  private GenderEnum(String g) {
    gender = g;
  }

  @Override
  public String toString() {
    return gender;
  }
}
