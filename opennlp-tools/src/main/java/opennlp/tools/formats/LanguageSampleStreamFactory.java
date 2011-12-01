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

package opennlp.tools.formats;

/**
 * Stream factory for those streams which carry language.
 */
public abstract class LanguageSampleStreamFactory<T> extends AbstractSampleStreamFactory<T> {

  // language seems to belong to the stream, however, ObjectStream is used in 400+ places
  // in the project and introducing new things to it is not a light decision.
  protected String language;

  protected <P> LanguageSampleStreamFactory(Class<P> params) {
    super(params);
  }

  @Override
  public String getLang() {
    return language;
  }
}
