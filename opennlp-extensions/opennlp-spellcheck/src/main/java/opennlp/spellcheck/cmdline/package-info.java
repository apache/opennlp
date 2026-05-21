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
 * Command-line tools for the OpenNLP SpellChecker (SymSpell) extension.
 *
 * <p>The {@link opennlp.spellcheck.cmdline.CLI} entry point dispatches to the available
 * tools, mirroring the {@code opennlp} and {@code opennlp-morfologik-addon} launchers:</p>
 * <ul>
 *   <li>{@link opennlp.spellcheck.cmdline.SpellCheckModelBuilderTool SpellCheckModelBuilder}
 *       &ndash; builds a binary {@link opennlp.spellcheck.dictionary.SymSpellModel} from
 *       plain-text frequency dictionaries;</li>
 *   <li>{@link opennlp.spellcheck.cmdline.CorrectTextTool CorrectText} &ndash; loads a
 *       model and corrects text read from a file or standard input.</li>
 * </ul>
 */
package opennlp.spellcheck.cmdline;
