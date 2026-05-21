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

/**
 * Bounded edit-distance metrics used by the SymSpell engine to verify candidates.
 *
 * <p>{@link opennlp.spellcheck.distance.EditDistance} is the common, threshold-aware
 * interface. {@link opennlp.spellcheck.distance.DamerauOSADistance} is the default
 * (code-point-aware optimal string alignment, transposition counts as one);
 * {@link opennlp.spellcheck.distance.LevenshteinDistance} is a selectable alternative
 * backed by Apache Commons Text.</p>
 */
package opennlp.spellcheck.distance;
