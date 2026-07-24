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
package opennlp.subword.sentencepiece;

/**
 * One encoded piece as a half-open byte range of the normalized text plus its vocabulary id.
 *
 * @param from The inclusive start offset in the normalized bytes.
 * @param to   The exclusive end offset in the normalized bytes.
 * @param id   The vocabulary id; the unknown id when no piece covers the range.
 */
record Segment(int from, int to, int id) {
}
