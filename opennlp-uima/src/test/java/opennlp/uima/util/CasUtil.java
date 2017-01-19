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

package opennlp.uima.util;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.apache.uima.ResourceSpecifierFactory;
import org.apache.uima.UIMAFramework;
import org.apache.uima.cas.CAS;
import org.apache.uima.cas.impl.XmiCasDeserializer;
import org.apache.uima.resource.ResourceInitializationException;
import org.apache.uima.resource.metadata.FsIndexDescription;
import org.apache.uima.resource.metadata.TypePriorities;
import org.apache.uima.resource.metadata.TypeSystemDescription;
import org.apache.uima.resource.metadata.impl.FsIndexDescription_impl;
import org.apache.uima.util.CasCreationUtils;
import org.apache.uima.util.InvalidXMLException;
import org.apache.uima.util.XMLInputSource;
import org.apache.uima.util.XMLParser;

import org.xml.sax.SAXException;

public class CasUtil {

  public static TypeSystemDescription createTypeSystemDescription(InputStream in) {

    // Note:
    // Type System location is not set correctly,
    // resolving a referenced type system will fail

    XMLInputSource xmlTypeSystemSource = new XMLInputSource(in, new File(""));

    XMLParser xmlParser = UIMAFramework.getXMLParser();

    TypeSystemDescription typeSystemDesciptor;

    try {
      typeSystemDesciptor = (TypeSystemDescription) xmlParser
          .parse(xmlTypeSystemSource);

      typeSystemDesciptor.resolveImports();
    } catch (InvalidXMLException e) {
      e.printStackTrace();
      typeSystemDesciptor = null;
    }

    return typeSystemDesciptor;
  }

  public static CAS createEmptyCAS(TypeSystemDescription typeSystem) {
    ResourceSpecifierFactory resourceSpecifierFactory = UIMAFramework
        .getResourceSpecifierFactory();
    TypePriorities typePriorities = resourceSpecifierFactory
        .createTypePriorities();

    FsIndexDescription indexDesciptor = new FsIndexDescription_impl();
    indexDesciptor.setLabel("TOPIndex");
    indexDesciptor.setTypeName("uima.cas.TOP");
    indexDesciptor.setKind(FsIndexDescription.KIND_SORTED);

    CAS cas;
    try {
      cas = CasCreationUtils.createCas(typeSystem, typePriorities,
          new FsIndexDescription[] { indexDesciptor });
    } catch (ResourceInitializationException e) {
      e.printStackTrace();
      cas = null;
    }

    return cas;
  }

  public static void deserializeXmiCAS(CAS cas, InputStream xmiIn) throws IOException {

    SAXParserFactory saxParserFactory = SAXParserFactory.newInstance();
    saxParserFactory.setValidating(false);

    SAXParser saxParser;

    try {
      saxParser = saxParserFactory.newSAXParser();
    } catch (ParserConfigurationException e) {
      throw new IllegalStateException(
          "SAXParser should be configured correctly!", e);
    } catch (SAXException e) {
      throw new IllegalStateException("SAX error while creating parser!", e);
    }

    XmiCasDeserializer dezerializer = new XmiCasDeserializer(
        cas.getTypeSystem());

    try {
      saxParser.parse(xmiIn, dezerializer.getXmiCasHandler(cas));
    } catch (SAXException e) {
      throw new IOException("Invalid XMI input!", e);
    }
  }
}
