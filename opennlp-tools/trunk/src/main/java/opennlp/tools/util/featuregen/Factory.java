/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreemnets.  See the NOTICE file distributed with
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

package opennlp.tools.util.featuregen;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

/**
 * Creates a set of feature generators based on a provided XML descriptor.
 *
 * Example of an XML descriptor:
 *
 * <generators>
 *   <charngram min = "2" max = "5"/>
 *   <definition/>
 *   <cache>
 *     <window prevLength = "3" nextLength = "3">
 *       <generators>
 *         <prevmap/>
 *         <sentence/>Ê
 *         <tokenclass/>
 *         <tokenpattern/>
 *       </generators>
 *     </window>
 *   </cache>
 * </generators>
 *
 * Each XML element is mapped to a {@link FeatureGeneratorFactory} which
 * is responsible to process the element and create the specified
 * {@link AdaptiveFeatureGenerator}. Elements can contain other
 * elements in this case it is the responsibility of the mapped factory to process
 * the child elements correctly. In some factories this leads to recursive
 * calls the {@link #createGenerator(Element)} method.
 *
 * In the example above the generators element is mapped to the
 * {@link AggregatedFeatureGeneratorFactory} which then
 * creates all the aggregated {@link AdaptiveFeatureGenerator}s to
 * accomplish this it evaluates the mapping with the same mechanism
 * and gives the child element to the corresponding factories. All
 * created generators are added to a new instance of the
 * {@link AggregatedFeatureGenerator} which is then returned.
 *
 * TODO:
 * Extension FeatureGenerators can be specified with a special xml element
 * which contains the class name of the factory.
 */
public class Factory {

  /**
   * The {@link FeatureGeneratorFactory} is responsible to construct
   * an {@link AdaptiveFeatureGenerator} from an given XML {@link Element}
   * which contains all necessary configuration if any.
   */
  static interface FeatureGeneratorFactory {

    /**
     * Creates an {@link AdaptiveFeatureGenerator} from a the describing
     * XML element.
     *
     * @param generatorElement the element which contains the configuration
     * @param resourceManager the resource manager which could be used
     *     to access referenced resources
     *
     * @return the configured {@link AdaptiveFeatureGenerator}
     */
    AdaptiveFeatureGenerator create(Element generatorElement,
        FactoryResourceManager resourceManager);
  }

  /**
   * @see AggregatedFeatureGenerator
   */
  static class AggregatedFeatureGeneratorFactory implements FeatureGeneratorFactory {

    public AdaptiveFeatureGenerator create(Element generatorElement,
        FactoryResourceManager resourceManager) {

      Collection<AdaptiveFeatureGenerator> aggregatedGenerators =
          new LinkedList<AdaptiveFeatureGenerator>();

      NodeList childNodes = generatorElement.getChildNodes();

      for (int i = 0; i < childNodes.getLength(); i++) {
        Node childNode = childNodes.item(i);

        if (childNode instanceof Element) {
          Element aggregatedGeneratorElement = (Element) childNode;

          aggregatedGenerators.add(
              Factory.createGenerator(aggregatedGeneratorElement, resourceManager));
        }
      }

      return new AggregatedFeatureGenerator(aggregatedGenerators.toArray(
              new AdaptiveFeatureGenerator[aggregatedGenerators.size()]));
    }

    static void register(Map<String, FeatureGeneratorFactory> factoryMap) {
      factoryMap.put("generators", new AggregatedFeatureGeneratorFactory());
    }
  }

  /**
   * @see CachedFeatureGenerator
   */
  static class CachedFeatureGeneratorFactory implements FeatureGeneratorFactory {

    private CachedFeatureGeneratorFactory() {
    }

    public AdaptiveFeatureGenerator create(Element generatorElement,
        FactoryResourceManager resourceManager) {

      Element cachedGeneratorElement = null;

      NodeList kids = generatorElement.getChildNodes();

      for (int i = 0; i < kids.getLength(); i++) {
        Node childNode = kids.item(i);

        if (childNode instanceof Element) {
          cachedGeneratorElement = (Element) childNode;
          break;
        }
      }

      // TODO: Check if the generator could be created to avoid NPE

      AdaptiveFeatureGenerator chachedGenerator =
          Factory.createGenerator(cachedGeneratorElement, resourceManager);

      return new CachedFeatureGenerator(chachedGenerator);
    }

    static void register(Map<String, FeatureGeneratorFactory> factoryMap) {
      factoryMap.put("cache", new CachedFeatureGeneratorFactory());
    }
  }

  /**
   * @see CharacterNgramFeatureGenerator
   */
  static class CharacterNgramFeatureGeneratorFactory implements FeatureGeneratorFactory {

    public AdaptiveFeatureGenerator create(Element generatorElement,
        FactoryResourceManager resourceManager) {

      String minString = generatorElement.getAttribute("min");

      int min;

      try {
        min = Integer.parseInt(minString);
      } catch (NumberFormatException e) {
        // TODO: throw parsing error
        min = 2;
      }

      String maxString = generatorElement.getAttribute("max");

      int max;

      try {
        max = Integer.parseInt(maxString);
      } catch (NumberFormatException e) {
        // TODO: throw parsing error
        max = 5;
      }

      return new CharacterNgramFeatureGenerator(min, max);
    }

    static void register(Map<String, FeatureGeneratorFactory> factoryMap) {
      factoryMap.put("charngram", new CharacterNgramFeatureGeneratorFactory());
    }
  }

  /**
   * @see DefinitionFeatureGenerator
   */
  static class DefinitionFeatureGeneratorFactory implements FeatureGeneratorFactory {

    private static final String ELEMENT_NAME = "definition";

    private DefinitionFeatureGeneratorFactory() {
    }

    public AdaptiveFeatureGenerator create(Element generatorElement,
        FactoryResourceManager resourceManager) {

      if (!ELEMENT_NAME.equals(generatorElement.getTagName())) {
        // TODO throw an exception
      }

      return new DefinitionFeatureGenerator();
    }

    static void register(Map<String, FeatureGeneratorFactory> factoryMap) {
      factoryMap.put(ELEMENT_NAME, new DefinitionFeatureGeneratorFactory());
    }
  }

  /**
   * @see DictionaryFeatureGenerator
   */
  static class DictionaryFeatureGeneratorFactory implements FeatureGeneratorFactory {

    public AdaptiveFeatureGenerator create(Element generatorElement,
        FactoryResourceManager resourceManager) {
      // TODO: Discuss resource loading with Tom
      return null;
    }

    static void register(Map<String, FeatureGeneratorFactory> factoryMap) {
      factoryMap.put("dictionary", new DictionaryFeatureGeneratorFactory());
    }
  }

  /**
   * @see PreviousMapFeatureGenerator
   */
  static class PreviousMapFeatureGeneratorFactory implements FeatureGeneratorFactory {

    public AdaptiveFeatureGenerator create(Element generatorElement,
        FactoryResourceManager resourceManager) {

      // TODO Check that element is the right one

      return new PreviousMapFeatureGenerator();
    }

    static void register(Map<String, FeatureGeneratorFactory> factoryMap) {
      factoryMap.put("prevmap", new PreviousMapFeatureGeneratorFactory());
    }
  }

  /**
   * @see SentenceFeatureGenerator
   */
  static class SentenceFeatureGeneratorFactory implements FeatureGeneratorFactory {

    public AdaptiveFeatureGenerator create(Element generatorElement,
        FactoryResourceManager resourceManager) {
      return new SentenceFeatureGenerator();
    }

    static void register(Map<String, FeatureGeneratorFactory> factoryMap) {
      factoryMap.put("sentence", new SentenceFeatureGeneratorFactory());
    }
  }

  /**
   * @see TokenClassFeatureGenerator
   */
  static class TokenClassFeatureGeneratorFactory implements FeatureGeneratorFactory {

    public AdaptiveFeatureGenerator create(Element generatorElement,
        FactoryResourceManager resourceManager) {
      return new TokenClassFeatureGenerator();
    }

    static void register(Map<String, FeatureGeneratorFactory> factoryMap) {
      factoryMap.put("tokenclass", new TokenClassFeatureGeneratorFactory());
    }
  }

  /**
   * @see TokenPatternFeatureGenerator
   */
  static class TokenPatternFeatureGeneratorFactory implements FeatureGeneratorFactory {

    public AdaptiveFeatureGenerator create(Element generatorElement,
        FactoryResourceManager resourceManager) {
      return new TokenPatternFeatureGenerator();
    }

    static void register(Map<String, FeatureGeneratorFactory> factoryMap) {
      factoryMap.put("tokenpattern", new TokenPatternFeatureGeneratorFactory());
    }
  }

  /**
   * @see WindowFeatureGenerator
   */
  static class WindowFeatureGeneratorFactory implements FeatureGeneratorFactory {

    public AdaptiveFeatureGenerator create(Element generatorElement,
        FactoryResourceManager resourceManager) {

      // prev and next length

      // get child element

      return null;
    }

    static void register(Map<String, FeatureGeneratorFactory> factoryMap) {
      factoryMap.put("window", new WindowFeatureGeneratorFactory());
    }
  }

  private static Map<String, FeatureGeneratorFactory> factories =
      new HashMap<String, FeatureGeneratorFactory>();

  static {
    AggregatedFeatureGeneratorFactory.register(factories);
    CachedFeatureGeneratorFactory.register(factories);
    CharacterNgramFeatureGeneratorFactory.register(factories);
    DefinitionFeatureGeneratorFactory.register(factories);
    DictionaryFeatureGeneratorFactory.register(factories);
    PreviousMapFeatureGeneratorFactory.register(factories);
    SentenceFeatureGeneratorFactory.register(factories);
    TokenClassFeatureGeneratorFactory.register(factories);
    TokenPatternFeatureGeneratorFactory.register(factories);
    WindowFeatureGeneratorFactory.register(factories);
  }

  /**
   * Creates a {@link AdaptiveFeatureGenerator} for the provided element.
   * To accomplish this it looks up the corresponding factory by the
   * element tag name. The factory is then responsible for the creation
   * of the generator from the element.
   *
   * @param generatorElement
   * @param resourceManager
   *
   * @return
   */
  static AdaptiveFeatureGenerator createGenerator(Element generatorElement,
      FactoryResourceManager resourceManager) {

    FeatureGeneratorFactory generatorFactory = factories.get(generatorElement.getTagName());

    return generatorFactory.create(generatorElement, resourceManager);
  }

  /**
   * Creates an {@link AdaptiveFeatureGenerator} from an provided XML descriptor.
   *
   * Usually this XML descriptor contains a set of nested feature generators
   * which are then used to generate the features by one of the opennlp
   * components.
   *
   * @param xmlDescriptorIn the {@link InputStream} from which the descriptor
   * is read, the stream remains open and must be closed by the caller.
   *
   * @param resourceManager the resource manager which is used to resolve resources
   * referenced by a key in the descriptor
   *
   * @return
   *
   * @throws IOException if an error occurs during reading from the descriptor
   *     {@link InputStream}
   */
  public static AdaptiveFeatureGenerator create(InputStream xmlDescriptorIn,
      FactoryResourceManager resourceManager) throws IOException {

    DocumentBuilderFactory documentBuilderFacoty = DocumentBuilderFactory.newInstance();

    DocumentBuilder documentBuilder;

    try {
      documentBuilder = documentBuilderFacoty.newDocumentBuilder();
    } catch (ParserConfigurationException e) {
      e.printStackTrace();
      documentBuilder = null;
    }

    org.w3c.dom.Document xmlDescriptorDOM;

    try {
      xmlDescriptorDOM = documentBuilder.parse(xmlDescriptorIn);
    } catch (SAXException e) {
      e.printStackTrace();
      xmlDescriptorDOM = null;
    }

    Element generatorElement = xmlDescriptorDOM.getDocumentElement();

    return createGenerator(generatorElement, resourceManager);
  }
}
