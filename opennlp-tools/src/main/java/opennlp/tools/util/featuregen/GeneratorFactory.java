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

package opennlp.tools.util.featuregen;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;

import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.w3c.dom.Text;
import org.xml.sax.SAXException;

import opennlp.tools.util.InvalidFormatException;
import opennlp.tools.util.XmlUtil;
import opennlp.tools.util.ext.ExtensionLoader;
import opennlp.tools.util.model.ArtifactSerializer;
import opennlp.tools.util.model.DictionarySerializer;
import opennlp.tools.util.model.POSModelSerializer;

/**
 * Creates a set of feature generators based on a provided XML descriptor.
 *
 * Example of an XML descriptor:
 * <p>
 * &lt;featureGenerators name="namefind"&gt;
 *     &lt;generator class="opennlp.tools.util.featuregen.CachedFeatureGeneratorFactory"&gt;
 *         &lt;generator class="opennlp.tools.util.featuregen.WindowFeatureGeneratorFactory"&gt;
 *           &lt;int name="prevLength"&gt;2&lt;/int&gt;
 *           &lt;int name="nextLength"&gt;2&lt;/int&gt;
 *           &lt;generator class="opennlp.tools.util.featuregen.TokenClassFeatureGeneratorFactory"/&gt;
 *         &lt;/generator&gt;
 *         &lt;generator class="opennlp.tools.util.featuregen.WindowFeatureGeneratorFactory"&gt;
 *           &lt;int name="prevLength"&gt;2&lt;/int&gt;
 *           &lt;int name="nextLength"&gt;2&lt;/int&gt;
 *           &lt;generator class="opennlp.tools.util.featuregen.TokenFeatureGeneratorFactory"/&gt;
 *         &lt;/generator&gt;
 *         &lt;generator class="opennlp.tools.util.featuregen.DefinitionFeatureGeneratorFactory"/&gt;
 *         &lt;generator class="opennlp.tools.util.featuregen.PreviousMapFeatureGeneratorFactory"/&gt;
 *         &lt;generator class="opennlp.tools.util.featuregen.BigramNameFeatureGeneratorFactory"/&gt;
 *         &lt;generator class="opennlp.tools.util.featuregen.SentenceFeatureGeneratorFactory"&gt;
 *           &lt;bool name="begin"&gt;true&lt;/bool&gt;
 *           &lt;bool name="end"&gt;false&lt;/bool&gt;
 *         &lt;/generator&gt;
 *     &lt;/generator&gt;
 * &lt;/featureGenerators&gt;
 * </p>
 *
 * Each XML element is mapped to a {@link GeneratorFactory.XmlFeatureGeneratorFactory} which
 * is responsible to process the element and create the specified
 * {@link AdaptiveFeatureGenerator}. Elements can contain other
 * elements in this case it is the responsibility of the mapped factory to process
 * the child elements correctly. In some factories this leads to recursive
 * calls the
 * {@link GeneratorFactory.XmlFeatureGeneratorFactory#create(Element, FeatureGeneratorResourceProvider)}
 * method.
 *
 * In the example above the generators element is mapped to the
 * {@link AggregatedFeatureGeneratorFactory} which then
 * creates all the aggregated {@link AdaptiveFeatureGenerator}s to
 * accomplish this it evaluates the mapping with the same mechanism
 * and gives the child element to the corresponding factories. All
 * created generators are added to a new instance of the
 * {@link AggregatedFeatureGenerator} which is then returned.
 */
public class GeneratorFactory {

  /**
   * The {@link XmlFeatureGeneratorFactory} is responsible to construct
   * an {@link AdaptiveFeatureGenerator} from an given XML {@link Element}
   * which contains all necessary configuration if any.
   */
  @Deprecated // TODO: (OPENNLP-1174) just remove when back-compat is no longer needed
  interface XmlFeatureGeneratorFactory {

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
        FeatureGeneratorResourceProvider resourceManager) throws InvalidFormatException;
  }

  public static abstract class AbstractXmlFeatureGeneratorFactory {

    protected Element generatorElement;
    protected FeatureGeneratorResourceProvider resourceManager;

    // to respect the order <generator/> in AggregatedFeatureGenerator, let's use LinkedHashMap
    protected LinkedHashMap<String, Object> args;

    public AbstractXmlFeatureGeneratorFactory() {
      args = new LinkedHashMap<>();
    }

    public Map<String, ArtifactSerializer<?>>
        getArtifactSerializerMapping() throws InvalidFormatException {
      return null;
    }

    final void init(Element element, FeatureGeneratorResourceProvider resourceManager)
        throws InvalidFormatException {
      this.generatorElement = element;
      this.resourceManager = resourceManager;
      List<AdaptiveFeatureGenerator> generators = new ArrayList<>();
      NodeList childNodes = generatorElement.getChildNodes();
      for (int i = 0; i < childNodes.getLength(); i++) {
        Node childNode = childNodes.item(i);
        if (childNode instanceof Element) {
          Element elem = (Element)childNode;
          String type = elem.getTagName();
          if (type.equals("generator")) {
            String key = "generator#" + Integer.toString(generators.size());
            AdaptiveFeatureGenerator afg = buildGenerator(elem, resourceManager);
            generators.add(afg);
            if (afg != null)
              args.put(key, afg);
          }
          else {
            String name = elem.getAttribute("name");
            Node cn = elem.getFirstChild();
            Text text = (Text)cn;

            switch (type) {
              case "int" :
                args.put(name, Integer.parseInt(text.getWholeText()));
                break;
              case "long" :
                args.put(name, Long.parseLong(text.getWholeText()));
                break;
              case "float" :
                args.put(name, Float.parseFloat(text.getWholeText()));
                break;
              case "double" :
                args.put(name, Double.parseDouble(text.getWholeText()));
                break;
              case "str" :
                args.put(name, text.getWholeText());
                break;
              case "bool" :
                args.put(name, Boolean.parseBoolean(text.getWholeText()));
                break;
              default:
                throw new InvalidFormatException(
                    "child element must be one of generator, int, long, float, double," +
                        " str or bool");
            }
          }
        }
      }

      if (generators.size() > 1) {
        AdaptiveFeatureGenerator aggregatedFeatureGenerator =
            new AggregatedFeatureGenerator(generators.toArray(
                new AdaptiveFeatureGenerator[generators.size()]));
        args.put("generator#0", aggregatedFeatureGenerator);
      }
    }

    public int getInt(String name) throws InvalidFormatException {
      Object value = args.get(name);
      if (value == null) {
        throw new InvalidFormatException("parameter " + name + " must be set!");
      }
      else if (value instanceof Integer) {
        return (Integer)value;
      }
      else {
        throw new InvalidFormatException("parameter " + name + " must be integer!");
      }
    }

    public int getInt(String name, int defValue) throws InvalidFormatException {
      Object value = args.get(name);
      if (value == null) {
        return defValue;
      }
      else if (value instanceof Integer) {
        return (Integer)value;
      }
      else {
        throw new InvalidFormatException("parameter " + name + " must be integer!");
      }
    }

    public long getLong(String name) throws InvalidFormatException {
      Object value = args.get(name);
      if (value == null) {
        throw new InvalidFormatException("parameter " + name + " must be set!");
      }
      else if (value instanceof Long) {
        return (Long)value;
      }
      else {
        throw new InvalidFormatException("parameter " + name + " must be long!");
      }
    }

    public long getLong(String name, long defValue) throws InvalidFormatException {
      Object value = args.get(name);
      if (value == null) {
        return defValue;
      }
      else if (value instanceof Long) {
        return (Long)value;
      }
      else {
        throw new InvalidFormatException("parameter " + name + " must be long!");
      }
    }

    public float getFloat(String name) throws InvalidFormatException {
      Object value = args.get(name);
      if (value == null) {
        throw new InvalidFormatException("parameter " + name + " must be set!");
      }
      else if (value instanceof Float) {
        return (Float)value;
      }
      else {
        throw new InvalidFormatException("parameter " + name + " must be float!");
      }
    }

    public float getFloat(String name, float defValue) throws InvalidFormatException {
      Object value = args.get(name);
      if (value == null) {
        return defValue;
      }
      else if (value instanceof Float) {
        return (Float)value;
      }
      else {
        throw new InvalidFormatException("parameter " + name + " must be float!");
      }
    }

    public double getDouble(String name) throws InvalidFormatException {
      Object value = args.get(name);
      if (value == null) {
        throw new InvalidFormatException("parameter " + name + " must be set!");
      }
      else if (value instanceof Double) {
        return (Double)value;
      }
      else {
        throw new InvalidFormatException("parameter " + name + " must be double!");
      }
    }

    public double getDouble(String name, double defValue) throws InvalidFormatException {
      Object value = args.get(name);
      if (value == null) {
        return defValue;
      }
      else if (value instanceof Double) {
        return (Double)value;
      }
      else {
        throw new InvalidFormatException("parameter " + name + " must be double!");
      }
    }

    public String getStr(String name) throws InvalidFormatException {
      Object value = args.get(name);
      if (value == null) {
        throw new InvalidFormatException("parameter " + name + " must be set!");
      }
      else if (value instanceof String) {
        return (String)value;
      }
      else {
        throw new InvalidFormatException("parameter " + name + " must be double!");
      }
    }

    public String getStr(String name, String defValue) throws InvalidFormatException {
      Object value = args.get(name);
      if (value == null) {
        return defValue;
      }
      else if (value instanceof String) {
        return (String)value;
      }
      else {
        throw new InvalidFormatException("parameter " + name + " must be String!");
      }
    }

    public boolean getBool(String name) throws InvalidFormatException {
      Object value = args.get(name);
      if (value == null) {
        throw new InvalidFormatException("parameter " + name + " must be set!");
      }
      else if (value instanceof Boolean) {
        return (Boolean)value;
      }
      else {
        throw new InvalidFormatException("parameter " + name + " must be boolean!");
      }
    }

    public boolean getBool(String name, boolean defValue) throws InvalidFormatException {
      Object value = args.get(name);
      if (value == null) {
        return defValue;
      }
      else if (value instanceof Boolean) {
        return (Boolean)value;
      }
      else {
        throw new InvalidFormatException("parameter " + name + " must be boolean!");
      }
    }

    /**
     *
     * @return null if the subclass uses {@link #resourceManager} to instantiate
     * @throws InvalidFormatException
     */
    public abstract AdaptiveFeatureGenerator create() throws InvalidFormatException;
  }

  // TODO: We have to support custom resources here. How does it work ?!
  // Attributes get into a Map<String, String> properties

  // How can serialization be supported ?!
  // The model is loaded, and the manifest should contain all serializer classes registered for the
  // resources by name.
  // When training, the descriptor could be consulted first to register the serializers, and afterwards
  // they are stored in the model.

  // TODO: (OPENNLP-1174) just remove this class when back-compat is no longer needed
  static class CustomFeatureGeneratorFactory implements XmlFeatureGeneratorFactory {

    public AdaptiveFeatureGenerator create(Element generatorElement,
        FeatureGeneratorResourceProvider resourceManager) throws InvalidFormatException {

      String featureGeneratorClassName = generatorElement.getAttribute("class");

      AdaptiveFeatureGenerator generator =
          ExtensionLoader.instantiateExtension(AdaptiveFeatureGenerator.class, featureGeneratorClassName);

      if (generator instanceof CustomFeatureGenerator) {

        CustomFeatureGenerator customGenerator = (CustomFeatureGenerator) generator;

        Map<String, String> properties = new HashMap<>();

        NamedNodeMap attributes = generatorElement.getAttributes();

        for (int i = 0; i < attributes.getLength(); i++) {
          Node attribute = attributes.item(i);
          if (!"class".equals(attribute.getNodeName())) {
            properties.put(attribute.getNodeName(), attribute.getNodeValue());
          }
        }

        if (resourceManager != null) {
          customGenerator.init(properties, resourceManager);
        }
      }

      return generator;
    }

    static void register(Map<String, XmlFeatureGeneratorFactory> factoryMap) {
      factoryMap.put("custom", new CustomFeatureGeneratorFactory());
    }
  }

  // TODO: (OPENNLP-1174) just remove when back-compat is no longer needed
  private static Map<String, XmlFeatureGeneratorFactory> factories = new HashMap<>();

  // TODO: (OPENNLP-1174) just remove when back-compat is no longer needed
  static {
    AggregatedFeatureGeneratorFactory.register(factories);
    CachedFeatureGeneratorFactory.register(factories);
    CharacterNgramFeatureGeneratorFactory.register(factories);
    DefinitionFeatureGeneratorFactory.register(factories);
    DictionaryFeatureGeneratorFactory.register(factories);
    DocumentBeginFeatureGeneratorFactory.register(factories);
    PreviousMapFeatureGeneratorFactory.register(factories);
    SentenceFeatureGeneratorFactory.register(factories);
    TokenClassFeatureGeneratorFactory.register(factories);
    TokenFeatureGeneratorFactory.register(factories);
    BigramNameFeatureGeneratorFactory.register(factories);
    TokenPatternFeatureGeneratorFactory.register(factories);
    PosTaggerFeatureGeneratorFactory.register(factories);
    PrefixFeatureGeneratorFactory.register(factories);
    SuffixFeatureGeneratorFactory.register(factories);
    WindowFeatureGeneratorFactory.register(factories);
    WordClusterFeatureGeneratorFactory.register(factories);
    BrownClusterTokenFeatureGeneratorFactory.register(factories);
    BrownClusterTokenClassFeatureGeneratorFactory.register(factories);
    BrownClusterBigramFeatureGeneratorFactory.register(factories);
    CustomFeatureGeneratorFactory.register(factories);
    POSTaggerNameFeatureGeneratorFactory.register(factories);
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
  @Deprecated   // TODO: (OPENNLP-1174) remove back-compat support when it is unnecessary
  static AdaptiveFeatureGenerator createGenerator(Element generatorElement,
      FeatureGeneratorResourceProvider resourceManager) throws InvalidFormatException {

    String elementName = generatorElement.getTagName();

    // check it is new format?
    if (elementName.equals("featureGenerators")) {

      List<AdaptiveFeatureGenerator> generators = new ArrayList<>();
      NodeList childNodes = generatorElement.getChildNodes();
      for (int i = 0; i < childNodes.getLength(); i++) {
        Node childNode = childNodes.item(i);
        if (childNode instanceof Element) {
          Element elem = (Element)childNode;
          String type = elem.getTagName();
          if (type.equals("generator")) {
            generators.add(buildGenerator(elem, resourceManager));
          }
          else
            throw new InvalidFormatException("Unexpected element: " + elementName);
        }
      }

      AdaptiveFeatureGenerator featureGenerator = null;
      if (generators.size() == 1)
        featureGenerator = generators.get(0);
      else if (generators.size() > 1)
        featureGenerator = new AggregatedFeatureGenerator(generators.toArray(
            new AdaptiveFeatureGenerator[generators.size()]));
      else
        throw new InvalidFormatException("featureGenerators must have one or more generators");

      // disallow manually specifying CachedFeatureGenerator
      if (featureGenerator instanceof CachedFeatureGenerator)
        throw new InvalidFormatException("CachedFeatureGeneratorFactory cannot be specified manually." +
          "Use cache=\"true\" attribute in featureGenerators element instead.");

      // check cache usage
      if (Boolean.parseBoolean(generatorElement.getAttribute("cache")))
        return new CachedFeatureGenerator(featureGenerator);
      else
        return featureGenerator;
    }
    else {
      // support classic format
      XmlFeatureGeneratorFactory generatorFactory = factories.get(elementName);
      if (generatorFactory != null) {
        return generatorFactory.create(generatorElement, resourceManager);
      }
      else
        throw new InvalidFormatException("Unexpected element: " + elementName);
    }
  }

  static Element getFirstChild(Element elem) {
    NodeList nodes = elem.getChildNodes();
    for (int i = 0; i < nodes.getLength(); i++) {
      if (nodes.item(i) instanceof Element) {
        return (Element)nodes.item(i);
      }
    }
    return null;
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
  static AdaptiveFeatureGenerator buildGenerator(Element generatorElement,
             FeatureGeneratorResourceProvider resourceManager) throws InvalidFormatException {
    String className = generatorElement.getAttribute("class");
    if (className == null) {
      throw new InvalidFormatException("generator must have class attribute");
    }
    else {
      try {
        Class factoryClass = Class.forName(className);
        try {
          Constructor constructor = factoryClass.getConstructor();
          AbstractXmlFeatureGeneratorFactory factory =
              (AbstractXmlFeatureGeneratorFactory)constructor.newInstance();
          factory.init(generatorElement, resourceManager);
          return factory.create();
        } catch (NoSuchMethodException e) {
          throw new RuntimeException(e);
        } catch (InvocationTargetException e) {
          throw new RuntimeException(e);
        } catch (InstantiationException e) {
          throw new RuntimeException(e);
        } catch (IllegalAccessException e) {
          throw new RuntimeException(e);
        }
      } catch (ClassNotFoundException e) {
        throw new RuntimeException(e);
      }
    }
  }

  private static org.w3c.dom.Document createDOM(InputStream xmlDescriptorIn)
      throws IOException {

    DocumentBuilder documentBuilder = XmlUtil.createDocumentBuilder();

    org.w3c.dom.Document xmlDescriptorDOM;

    try {
      xmlDescriptorDOM = documentBuilder.parse(xmlDescriptorIn);
    } catch (SAXException e) {
      throw new InvalidFormatException("Descriptor is not valid XML!", e);
    }
    return xmlDescriptorDOM;
  }

  /**
   * Creates an {@link AdaptiveFeatureGenerator} from an provided XML descriptor.
   *
   * Usually this XML descriptor contains a set of nested feature generators
   * which are then used to generate the features by one of the opennlp
   * components.
   *
   * @param xmlDescriptorIn the {@link InputStream} from which the descriptor
   *     is read, the stream remains open and must be closed by the caller.
   *
   * @param resourceManager the resource manager which is used to resolve resources
   *     referenced by a key in the descriptor
   *
   * @return created feature generators
   *
   * @throws IOException if an error occurs during reading from the descriptor
   *     {@link InputStream}
   */
  public static AdaptiveFeatureGenerator create(InputStream xmlDescriptorIn,
      FeatureGeneratorResourceProvider resourceManager) throws IOException {

    org.w3c.dom.Document xmlDescriptorDOM = createDOM(xmlDescriptorIn);

    Element generatorElement = xmlDescriptorDOM.getDocumentElement();

    // TODO: (OPENNLP-1174) use #buildGenerator() after back-compat support is gone
    return createGenerator(generatorElement, resourceManager);
  }

  public static Map<String, ArtifactSerializer<?>> extractArtifactSerializerMappings(
      InputStream xmlDescriptorIn) throws IOException {

    org.w3c.dom.Document xmlDescriptorDOM = createDOM(xmlDescriptorIn);
    Element element = xmlDescriptorDOM.getDocumentElement();

    String elementName = element.getTagName();

    // check it is new format?
    if (elementName.equals("featureGenerators")) {
      Map<String, ArtifactSerializer<?>> mapping = new HashMap<>();
      NodeList nodes = element.getChildNodes();
      for (int i = 0; i < nodes.getLength(); i++) {
        if (nodes.item(i) instanceof Element) {
          Element childElem = (Element)nodes.item(i);
          if (childElem.getTagName().equals("generator")) {
            extractArtifactSerializerMappings(mapping, childElem);
          }
        }
      }
      return mapping;
    }
    else {
      return extractArtifactSerializerMappingsClassicFormat(element);
    }
  }

  static void extractArtifactSerializerMappings(Map<String, ArtifactSerializer<?>> mapping, Element element) {
    String className = element.getAttribute("class");
    if (className != null) {
      try {
        Class factoryClass = Class.forName(className);
        try {
          Constructor constructor = factoryClass.getConstructor();
          AbstractXmlFeatureGeneratorFactory factory =
              (AbstractXmlFeatureGeneratorFactory)constructor.newInstance();
          factory.init(element, null);
          Map<String, ArtifactSerializer<?>> map = factory.getArtifactSerializerMapping();
          if (map != null)
            mapping.putAll(map);
        } catch (NoSuchMethodException e) {
          throw new RuntimeException(e);
        } catch (InvocationTargetException e) {
          throw new RuntimeException(e);
        } catch (InstantiationException e) {
          throw new RuntimeException(e);
        } catch (IllegalAccessException e) {
          throw new RuntimeException(e);
        } catch (InvalidFormatException ignored) {
        }
      } catch (ClassNotFoundException e) {
        throw new RuntimeException(e);
      }
    }

    NodeList nodes = element.getChildNodes();
    for (int i = 0; i < nodes.getLength(); i++) {
      if (nodes.item(i) instanceof Element) {
        Element childElem = (Element)nodes.item(i);
        if (childElem.getTagName().equals("generator")) {
          extractArtifactSerializerMappings(mapping, childElem);
        }
      }
    }
  }

  @Deprecated   // TODO: (OPENNLP-1174) remove back-compat support when it is unnecessary
  static Map<String, ArtifactSerializer<?>> extractArtifactSerializerMappingsClassicFormat(
      Element elem) throws IOException {
    Map<String, ArtifactSerializer<?>> mapping = new HashMap<>();

    XPath xPath = XPathFactory.newInstance().newXPath();

    NodeList customElements;
    try {
      XPathExpression exp = xPath.compile("//custom");
      customElements = (NodeList) exp.evaluate(elem, XPathConstants.NODESET);
    } catch (XPathExpressionException e) {
      throw new IllegalStateException("The hard coded XPath expression should always be valid!");
    }

    for (int i = 0; i < customElements.getLength(); i++) {
      if (customElements.item(i) instanceof Element) {
        Element customElement = (Element) customElements.item(i);

        // Note: The resource provider is not available at that point, to provide
        // resources they need to be loaded first!
        AdaptiveFeatureGenerator generator = createGenerator(customElement, null);

        if (generator instanceof ArtifactToSerializerMapper) {
          ArtifactToSerializerMapper mapper = (ArtifactToSerializerMapper) generator;
          mapping.putAll(mapper.getArtifactSerializerMapping());
        }
      }
    }

    NodeList allElements;
    try {
      XPathExpression exp = xPath.compile("//*");
      allElements = (NodeList) exp.evaluate(elem, XPathConstants.NODESET);
    } catch (XPathExpressionException e) {
      throw new IllegalStateException("The hard coded XPath expression should always be valid!");
    }

    for (int i = 0; i < allElements.getLength(); i++) {
      if (allElements.item(i) instanceof Element) {
        Element xmlElement = (Element) allElements.item(i);

        String dictName = xmlElement.getAttribute("dict");
        if (dictName != null) {

          switch (xmlElement.getTagName()) {
            case "wordcluster":
              mapping.put(dictName, new WordClusterDictionary.WordClusterDictionarySerializer());
              break;

            case "brownclustertoken":
              mapping.put(dictName, new BrownCluster.BrownClusterSerializer());
              break;

            case "brownclustertokenclass"://, ;
              mapping.put(dictName, new BrownCluster.BrownClusterSerializer());
              break;

            case "brownclusterbigram": //, ;
              mapping.put(dictName, new BrownCluster.BrownClusterSerializer());
              break;

            case "dictionary":
              mapping.put(dictName, new DictionarySerializer());
              break;
          }
        }

        String modelName = xmlElement.getAttribute("model");
        if (modelName != null) {

          switch (xmlElement.getTagName()) {
            case "tokenpos":
              mapping.put(modelName, new POSModelSerializer());
              break;
          }
        }
      }
    }

    return mapping;
  }

  /**
   * Provides a list with all the elements in the xml feature descriptor.
   * @param xmlDescriptorIn the xml feature descriptor
   * @return a list containing all elements
   * @throws IOException if inputstream cannot be open
   * @throws InvalidFormatException if xml is not well-formed
   */
  public static List<Element> getDescriptorElements(InputStream xmlDescriptorIn)
      throws IOException {

    List<Element> elements = new ArrayList<>();
    org.w3c.dom.Document xmlDescriptorDOM = createDOM(xmlDescriptorIn);
    XPath xPath = XPathFactory.newInstance().newXPath();
    NodeList allElements;
    try {
      XPathExpression exp = xPath.compile("//*");
      allElements = (NodeList) exp.evaluate(xmlDescriptorDOM.getDocumentElement(), XPathConstants.NODESET);
    } catch (XPathExpressionException e) {
      throw new IllegalStateException("The hard coded XPath expression should always be valid!");
    }

    for (int i = 0; i < allElements.getLength(); i++) {
      if (allElements.item(i) instanceof Element) {
        Element customElement = (Element) allElements.item(i);
        elements.add(customElement);
      }
    }
    return elements;
  }
}
