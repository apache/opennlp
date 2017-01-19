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

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import opennlp.tools.util.ObjectStream;

public class BratDocument {

  private final AnnotationConfiguration config;
  private final String id;
  private final String text;
  private final Map<String, BratAnnotation> annotationMap;

  public BratDocument(AnnotationConfiguration config, String id, String text,
      Collection<BratAnnotation> annotations) {
    this.config = config;
    this.id = id;
    this.text = text;

    Map<String, BratAnnotation> annMap = new HashMap<>();
    for (BratAnnotation annotation : annotations) {
      annMap.put(annotation.getId(), annotation);
    }

    annotationMap = Collections.unmodifiableMap(annMap);
  }

  public AnnotationConfiguration getConfig() {
    return config;
  }

  public String getId() {
    return id;
  }

  public String getText() {
    return text;
  }

  public BratAnnotation getAnnotation(String id) {
    return annotationMap.get(id);
  }

  public Collection<BratAnnotation> getAnnotations() {
    return annotationMap.values();
  }

  public static BratDocument parseDocument(AnnotationConfiguration config, String id,
      InputStream txtIn, InputStream annIn) throws IOException {

    Reader txtReader = new InputStreamReader(txtIn, StandardCharsets.UTF_8);

    StringBuilder text = new StringBuilder();

    char cbuf[] = new char[1024];

    int len;
    while ((len = txtReader.read(cbuf)) > 0) {
      text.append(cbuf, 0, len);
    }

    Collection<BratAnnotation> annotations = new ArrayList<>();
    ObjectStream<BratAnnotation> annStream = new BratAnnotationStream(config, id, annIn);
    BratAnnotation ann;
    while ((ann = annStream.read()) != null) {
      annotations.add(ann);
    }

    return new BratDocument(config, id, text.toString(), annotations);
  }
}
