/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package opennlp.tools.stemmer;

/**
 * A factory that captures a stemmer configuration and mints configured {@link Stemmer}
 * instances on demand. Unlike {@code BaseToolFactory}, this type is not loaded from a model
 * manifest.
 */
public interface StemmerFactory {

  /**
   * {@return a new {@link Stemmer}}
   */
  Stemmer newStemmer();
}
