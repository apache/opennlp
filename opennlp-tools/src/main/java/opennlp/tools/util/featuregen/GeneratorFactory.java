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
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.w3c.dom.Text;
import org.xml.sax.SAXException;

import opennlp.tools.util.InvalidFormatException;
import opennlp.tools.util.XmlUtil;
import opennlp.tools.util.model.ArtifactSerializer;

/**
 * Creates a set of feature generators based on a provided XML descriptor.
 * <p>
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
 * Each XML element is mapped to a {@link GeneratorFactory.AbstractXmlFeatureGeneratorFactory} which
 * is responsible to process the element and create the specified
 * {@link AdaptiveFeatureGenerator}. Elements can contain other
 * elements in this case it is the responsibility of the mapped factory to process
 * the child elements correctly.
 * <p>
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
   * Creates an {@link AdaptiveFeatureGenerator} from an provided XML descriptor.
   * <p>
   * Usually this XML descriptor contains a set of nested feature generators
   * which are then used to generate the features by one of the opennlp
   * components.
   *
   * @param xmlDescriptorIn the {@link InputStream} from which the descriptor
   *                        is read, the stream remains open and must be closed by the caller.
   * @param resourceManager the resource manager which is used to resolve resources
   *                        referenced by a key in the descriptor
   * @return created feature generators
   * @throws IOException if an error occurs during reading from the descriptor
   *                     {@link InputStream}
   */
  public static AdaptiveFeatureGenerator create(InputStream xmlDescriptorIn,
                                                FeatureGeneratorResourceProvider resourceManager)
      throws IOException {

    final org.w3c.dom.Document xmlDescriptorDOM = createDOM(xmlDescriptorIn);
    final Element generatorElement = xmlDescriptorDOM.getDocumentElement();

    return createGenerator(generatorElement, resourceManager);
  }

  /**
   * Creates an {@link AdaptiveFeatureGenerator} for the provided element.
   * To accomplish this it looks up the corresponding factory by the
   * element tag name. The factory is then responsible for the creation
   * of the generator from the element.
   *
   * @param generatorElement must not be {@code null}
   * @param resourceManager  may be {@code null}
   * @return an {@link AdaptiveFeatureGenerator}
   * @throws IllegalArgumentException if the given {@link Element generatorElement} is {@code null}.
   * @throws IllegalStateException if the given {@link Element generatorElement} has
   *                               no {@code class} attribute.
   */
  private static AdaptiveFeatureGenerator buildGenerator(Element generatorElement,
                                                         FeatureGeneratorResourceProvider resourceManager)
      throws InvalidFormatException {

    if (generatorElement == null) {
      throw new IllegalArgumentException("generatorElement must not be NULL");
    }

    final String className = generatorElement.getAttribute("class");
    if (className.isBlank()) {
      throw new InvalidFormatException("generator must have class attribute");
    } else {
      try {
        final Class<?> factoryClass = Class.forName(className);
        try {
          final Constructor<?> constructor = factoryClass.getConstructor();
          final AbstractXmlFeatureGeneratorFactory factory =
              (AbstractXmlFeatureGeneratorFactory) constructor.newInstance();
          factory.init(generatorElement, resourceManager);
          return factory.create();
        } catch (NoSuchMethodException | InvocationTargetException | InstantiationException
                 | IllegalAccessException e) {
          throw new RuntimeException(e);
        }
      } catch (ClassNotFoundException e) {
        throw new RuntimeException(e);
      }
    }
  }

  /**
   * Creates an {@link AdaptiveFeatureGenerator} for the provided element.
   * To accomplish this it looks up the corresponding factory by the
   * element tag name. The factory is then responsible for the creation
   * of the generator from the element.
   *
   * @param generatorElement must not be {@code null}
   * @param resourceManager  may be {@code null}
   * @return an {@link AdaptiveFeatureGenerator}
   * @throws IllegalArgumentException if the given {@link Element generatorElement} is {@code null}
   */
  private static AdaptiveFeatureGenerator createGenerator(Element generatorElement,
                                                          FeatureGeneratorResourceProvider resourceManager)
      throws InvalidFormatException {

    if (generatorElement == null) {
      throw new IllegalArgumentException("generatorElement must not be NULL");
    }

    final String elementName = generatorElement.getTagName();

    // check it is new format?
    if ("featureGenerators".equals(elementName)) {

      final List<AdaptiveFeatureGenerator> generators = new ArrayList<>();
      final NodeList childNodes = generatorElement.getChildNodes();
      for (int i = 0; i < childNodes.getLength(); i++) {
        final Node childNode = childNodes.item(i);
        if (childNode instanceof Element elem) {
          final String type = elem.getTagName();
          if ("generator".equals(type)) {
            generators.add(buildGenerator(elem, resourceManager));
          } else {
            throw new InvalidFormatException("Unexpected element: " + elementName);
          }
        }
      }

      AdaptiveFeatureGenerator featureGenerator;
      if (generators.size() == 1) {
        featureGenerator = generators.get(0);
      } else if (generators.size() > 1) {
        featureGenerator = new AggregatedFeatureGenerator(generators.toArray(
            new AdaptiveFeatureGenerator[0]));
      } else {
        throw new InvalidFormatException("featureGenerators must have one or more generators");
      }

      // disallow manually specifying CachedFeatureGenerator
      if (featureGenerator instanceof CachedFeatureGenerator) {
        throw new InvalidFormatException("CachedFeatureGeneratorFactory cannot be specified manually." +
            "Use cache=\"true\" attribute in featureGenerators element instead.");
      }

      // check cache usage
      if (Boolean.parseBoolean(generatorElement.getAttribute("cache"))) {
        return new CachedFeatureGenerator(featureGenerator);
      } else {
        return featureGenerator;
      }
    } else {
      throw new IllegalArgumentException(
          "[OPENNLP-1174] - Classic configuration format is no longer supported!");
    }
  }

  private static org.w3c.dom.Document createDOM(InputStream xmlDescriptorIn)
      throws IOException {

    final DocumentBuilder documentBuilder = XmlUtil.createDocumentBuilder();

    org.w3c.dom.Document xmlDescriptorDOM;

    try {
      xmlDescriptorDOM = documentBuilder.parse(xmlDescriptorIn);
    } catch (SAXException e) {
      throw new InvalidFormatException("Descriptor is not valid XML!", e);
    }
    return xmlDescriptorDOM;
  }

  public static Map<String, ArtifactSerializer<?>> extractArtifactSerializerMappings(
      InputStream xmlDescriptorIn) throws IOException {

    final org.w3c.dom.Document xmlDescriptorDOM = createDOM(xmlDescriptorIn);
    final Element element = xmlDescriptorDOM.getDocumentElement();

    final String elementName = element.getTagName();

    // check it is new format?
    if ("featureGenerators".equals(elementName)) {
      return addMappingsFromXmlChildren(element.getChildNodes(), new HashMap<>());
    } else {
      throw new IllegalArgumentException(
          "[OPENNLP-1174] - Classic configuration format is no longer supported!");
    }
  }

  private static void extractArtifactSerializerMappings(
      Map<String, ArtifactSerializer<?>> mapping, Element element) {
    final String className = element.getAttribute("class");
    if (!className.isBlank()) {
      try {
        final Class<?> factoryClass = Class.forName(className);
        try {
          final Constructor<?> constructor = factoryClass.getConstructor();
          final AbstractXmlFeatureGeneratorFactory factory =
              (AbstractXmlFeatureGeneratorFactory) constructor.newInstance();
          factory.init(element, null);
          final Map<String, ArtifactSerializer<?>> map = factory.getArtifactSerializerMapping();
          if (map != null) {
            mapping.putAll(map);
          }
        } catch (NoSuchMethodException | InvocationTargetException | InstantiationException
                 | IllegalAccessException e) {
          throw new RuntimeException(e);
        } catch (InvalidFormatException ignored) {
        }
      } catch (ClassNotFoundException e) {
        throw new RuntimeException(e);
      }
    }

    addMappingsFromXmlChildren(element.getChildNodes(), mapping);
  }

  private static Map<String, ArtifactSerializer<?>> addMappingsFromXmlChildren(
      final NodeList nodes, final Map<String, ArtifactSerializer<?>> mapping) {
    for (int i = 0; i < nodes.getLength(); i++) {
      if (nodes.item(i) instanceof Element childElem) {
        if ("generator".equals(childElem.getTagName())) {
          extractArtifactSerializerMappings(mapping, childElem);
        }
      }
    }
    return mapping;
  }

  /**
   * Provides a list with all the elements in the xml feature descriptor.
   *
   * @param xmlDescriptorIn the xml feature descriptor
   * @return a list containing all elements
   * @throws IOException            if the given {@link InputStream} cannot be open
   * @throws InvalidFormatException if xml is not well-formed
   */
  public static List<Element> getDescriptorElements(InputStream xmlDescriptorIn)
      throws IOException {

    final List<Element> elements = new ArrayList<>();
    final org.w3c.dom.Document xmlDescriptorDOM = createDOM(xmlDescriptorIn);
    final XPath xPath = XPathFactory.newInstance().newXPath();
    NodeList allElements;
    try {
      final XPathExpression exp = xPath.compile("//*");
      allElements = (NodeList) exp.evaluate(xmlDescriptorDOM.getDocumentElement(), XPathConstants.NODESET);
    } catch (XPathExpressionException e) {
      throw new InvalidFormatException("The hard coded XPath expression should always be valid!");
    }

    for (int i = 0; i < allElements.getLength(); i++) {
      if (allElements.item(i) instanceof Element customElement) {
        elements.add(customElement);
      }
    }
    return elements;
  }

  public static abstract class AbstractXmlFeatureGeneratorFactory {

    protected Element generatorElement;
    protected FeatureGeneratorResourceProvider resourceManager;

    // to respect the order <generator/> in AggregatedFeatureGenerator, let's use LinkedHashMap
    protected LinkedHashMap<String, Object> args;

    public AbstractXmlFeatureGeneratorFactory() {
      args = new LinkedHashMap<>();
    }

    public Map<String, ArtifactSerializer<?>> getArtifactSerializerMapping() throws InvalidFormatException {
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
        if (childNode instanceof Element elem) {
          String type = elem.getTagName();
          if (type.equals("generator")) {
            String key = "generator#" + generators.size();
            AdaptiveFeatureGenerator afg = buildGenerator(elem, resourceManager);
            generators.add(afg);
            if (afg != null) {
              args.put(key, afg);
            }
          } else {
            String name = elem.getAttribute("name");
            Node cn = elem.getFirstChild();
            Text text = (Text) cn;

            switch (type) {
              case "int" -> args.put(name, Integer.parseInt(text.getWholeText()));
              case "long" -> args.put(name, Long.parseLong(text.getWholeText()));
              case "float" -> args.put(name, Float.parseFloat(text.getWholeText()));
              case "double" -> args.put(name, Double.parseDouble(text.getWholeText()));
              case "str" -> args.put(name, text.getWholeText());
              case "bool" -> args.put(name, Boolean.parseBoolean(text.getWholeText()));
              default -> throw new InvalidFormatException(
                  "child element must be one of generator, int, long, float, double," +
                      " str or bool");
            }
          }
        }
      }

      if (generators.size() > 1) {
        AdaptiveFeatureGenerator aggregatedFeatureGenerator =
            new AggregatedFeatureGenerator(generators.toArray(
                new AdaptiveFeatureGenerator[0]));
        args.put("generator#0", aggregatedFeatureGenerator);
      }
    }

    public int getInt(String name) throws InvalidFormatException {
      final Object value = args.get(name);
      if (value == null) {
        throw new InvalidFormatException("parameter " + name + " must be set!");
      } else if (value instanceof Integer) {
        return (Integer) value;
      } else {
        throw new InvalidFormatException("parameter " + name + " must be integer!");
      }
    }

    public int getInt(String name, int defValue) throws InvalidFormatException {
      final Object value = args.get(name);
      if (value == null) {
        return defValue;
      } else if (value instanceof Integer) {
        return (Integer) value;
      } else {
        throw new InvalidFormatException("parameter " + name + " must be integer!");
      }
    }

    public long getLong(String name) throws InvalidFormatException {
      final Object value = args.get(name);
      if (value == null) {
        throw new InvalidFormatException("parameter " + name + " must be set!");
      } else if (value instanceof Long) {
        return (Long) value;
      } else {
        throw new InvalidFormatException("parameter " + name + " must be long!");
      }
    }

    public long getLong(String name, long defValue) throws InvalidFormatException {
      final Object value = args.get(name);
      if (value == null) {
        return defValue;
      } else if (value instanceof Long) {
        return (Long) value;
      } else {
        throw new InvalidFormatException("parameter " + name + " must be long!");
      }
    }

    public float getFloat(String name) throws InvalidFormatException {
      final Object value = args.get(name);
      if (value == null) {
        throw new InvalidFormatException("parameter " + name + " must be set!");
      } else if (value instanceof Float) {
        return (Float) value;
      } else {
        throw new InvalidFormatException("parameter " + name + " must be float!");
      }
    }

    public float getFloat(String name, float defValue) throws InvalidFormatException {
      final Object value = args.get(name);
      if (value == null) {
        return defValue;
      } else if (value instanceof Float) {
        return (Float) value;
      } else {
        throw new InvalidFormatException("parameter " + name + " must be float!");
      }
    }

    public double getDouble(String name) throws InvalidFormatException {
      final Object value = args.get(name);
      if (value == null) {
        throw new InvalidFormatException("parameter " + name + " must be set!");
      } else if (value instanceof Double) {
        return (Double) value;
      } else {
        throw new InvalidFormatException("parameter " + name + " must be double!");
      }
    }

    public double getDouble(String name, double defValue) throws InvalidFormatException {
      final Object value = args.get(name);
      if (value == null) {
        return defValue;
      } else if (value instanceof Double) {
        return (Double) value;
      } else {
        throw new InvalidFormatException("parameter " + name + " must be double!");
      }
    }

    public String getStr(String name) throws InvalidFormatException {
      final Object value = args.get(name);
      if (value == null) {
        throw new InvalidFormatException("parameter " + name + " must be set!");
      } else if (value instanceof String) {
        return (String) value;
      } else {
        throw new InvalidFormatException("parameter " + name + " must be double!");
      }
    }

    public String getStr(String name, String defValue) throws InvalidFormatException {
      final Object value = args.get(name);
      if (value == null) {
        return defValue;
      } else if (value instanceof String) {
        return (String) value;
      } else {
        throw new InvalidFormatException("parameter " + name + " must be String!");
      }
    }

    public boolean getBool(String name) throws InvalidFormatException {
      Object value = args.get(name);
      if (value == null) {
        throw new InvalidFormatException("parameter " + name + " must be set!");
      } else if (value instanceof Boolean) {
        return (Boolean) value;
      } else {
        throw new InvalidFormatException("parameter " + name + " must be boolean!");
      }
    }

    public boolean getBool(String name, boolean defValue) throws InvalidFormatException {
      Object value = args.get(name);
      if (value == null) {
        return defValue;
      } else if (value instanceof Boolean) {
        return (Boolean) value;
      } else {
        throw new InvalidFormatException("parameter " + name + " must be boolean!");
      }
    }

    /**
     * @return null if the subclass uses {@link #resourceManager} to instantiate
     * @throws InvalidFormatException
     */
    public abstract AdaptiveFeatureGenerator create() throws InvalidFormatException;
  }
}
