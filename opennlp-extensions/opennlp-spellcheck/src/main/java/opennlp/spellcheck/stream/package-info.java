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
 * Pipeline integration of the SpellChecker as OpenNLP
 * {@link opennlp.tools.util.FilterObjectStream} filters.
 *
 * <p>{@link opennlp.spellcheck.stream.SpellCorrectingObjectStream} corrects whole text
 * lines read from any {@link opennlp.tools.util.ObjectStream} (e.g. a
 * {@link opennlp.tools.util.PlainTextByLineStream}), while
 * {@link opennlp.spellcheck.stream.SpellCorrectingTokenStream} corrects tokenized data
 * token-by-token without changing the token count.</p>
 */
package opennlp.spellcheck.stream;
