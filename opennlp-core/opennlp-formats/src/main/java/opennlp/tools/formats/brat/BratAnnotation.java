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

package opennlp.tools.formats.brat;

import java.util.Objects;

public abstract class BratAnnotation {

  private final String id;
  private final String type;
  private String note;
  
  protected BratAnnotation(String id, String type) {
    this.id = Objects.requireNonNull(id);
    this.type = Objects.requireNonNull(type);
    this.note = "";
  }

  public String getId() {
    return id;
  }

  public String getType() {
    return type;
  }

  public void setNote(String note) {
    this.note = note;
  }
  
  public String getNote() {
    return note;
  }
  
  @Override
  public String toString() {
    return (id + " " + type + " " + note).trim();
  }
}
