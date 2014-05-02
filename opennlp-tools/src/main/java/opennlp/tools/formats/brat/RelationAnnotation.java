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

public class RelationAnnotation extends BratAnnotation {

  private final String arg1;
  private final String arg2;

  protected RelationAnnotation(String id, String type, String arg1, String arg2) {
    super(id, type);
    this.arg1 = arg1;
    this.arg2 = arg2;
  }

  public String getArg1() {
    return arg1;
  }

  public String getArg2() {
    return arg2;
  }

  @Override
  public String toString() {
    return super.toString() + " arg1:" + getArg1() + " arg2:" + getArg2();
  }
}
