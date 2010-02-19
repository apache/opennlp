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


package opennlp.tools.util.model;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import opennlp.tools.util.InvalidFormatException;
import opennlp.tools.util.Version;

/**
 * This model is a common based which can be used by the components
 * model classes.
 * 
 * TODO:
 * Provide sub classes access to serializers already in constructor
 */
public abstract class BaseModel {

  protected static final String MANIFEST_ENTRY = "manifest.properties";
  private static final String VERSION_PROPERTY = "version";
  private static final String LANGUAGE_PROPERTY = "language";
  
  @SuppressWarnings("unchecked")
  private Map<String, ArtifactSerializer> artifactSerializers =
      new HashMap<String, ArtifactSerializer>();

  protected final Map<String, Object> artifactMap;

  /**
   * Initializes the current instance.
   *
   * @param languageCode
   */
  protected BaseModel(String languageCode) {

    if (languageCode == null)
        throw new IllegalArgumentException("languageCode must not be null!");

    artifactMap = new HashMap<String, Object>();
    
    createArtifactSerializers(artifactSerializers);
    
    Properties manifest = new Properties();
    manifest.setProperty(LANGUAGE_PROPERTY, languageCode);
    manifest.setProperty(VERSION_PROPERTY, Version.currentVersion().toString());

    artifactMap.put(MANIFEST_ENTRY, manifest);
  }

  /**
   * Initializes the current instance.
   *
   * @param in
   *
   * @throws IOException
   * @throws InvalidFormatException
   */
  @SuppressWarnings("unchecked")
  protected BaseModel(InputStream in) throws IOException, InvalidFormatException {

    if (in == null)
        throw new IllegalArgumentException("in must not be null!");

    Map<String, Object> artifactMap = new HashMap<String, Object>();

    createArtifactSerializers(artifactSerializers);

    final ZipInputStream zip = new ZipInputStream(in);

    ZipEntry entry;
    while((entry = zip.getNextEntry()) != null ) {

      String extension = getEntryExtension(entry.getName());

      ArtifactSerializer factory = artifactSerializers.get(extension);

      if (factory == null) {
        throw new InvalidFormatException("Unkown artifact format: " + extension);
      }

      artifactMap.put(entry.getName(), factory.create(zip));

      zip.closeEntry();
    }

    this.artifactMap = Collections.unmodifiableMap(artifactMap);

    validateArtifactMap();
  }

  /**
   * Extracts the "." extension from an entry name.
   *
   * @param entry the entry name which contains the extension
   *
   * @return the extension
   *
   * @throws InvalidFormatException if no extension can be extracted
   */
  private String getEntryExtension(String entry) throws InvalidFormatException {
    int extensionIndex = entry.lastIndexOf('.') + 1;

    if (extensionIndex == -1 || extensionIndex >= entry.length())
        throw new InvalidFormatException("Entry name must have type extension: " + entry);

    return entry.substring(extensionIndex);
  }

  @SuppressWarnings("unchecked")
  protected ArtifactSerializer getArtifactSerializer(String resoruceName) {
    String extension = null;
    try {
      extension = getEntryExtension(resoruceName);
    } catch (InvalidFormatException e) {
      // TODO: throw runtime exception, error in model code
      // sorry, will not fail, because the name was validated prior
      e.printStackTrace();
    }

    return artifactSerializers.get(extension);  
  }
  
  /**
   * Registers all {@link ArtifactSerializer} for their artifact file name extensions.
   * The registered {@link ArtifactSerializer} are used to create and serialize
   * resources in the model package.
   * 
   * Override this method to register custom {@link ArtifactSerializer}s.
   *
   * Note:
   * Subclasses should generally invoke super.createArtifactSerializers at the beginning
   * of this method.
   * 
   * This method is called during construction.
   *
   * @param serializers the key of the map is the file extension used to lookup
   *     the {@link ArtifactSerializer}.
   */
  @SuppressWarnings("unchecked")
  protected void createArtifactSerializers(
      Map<String, ArtifactSerializer> serializers) {
    GenericModelSerializer.register(serializers);
    PropertiesSerializer.register(serializers);
    DictionarySerializer.register(serializers);
  }

  /**
   * Validates the parsed artifacts. If something is not
   * valid subclasses should throw an {@link InvalidFormatException}.
   *
   * Note:
   * Subclasses should generally invoke super.validateArtifactMap at the beginning
   * of this method.
   *
   * @throws InvalidFormatException
   */
  protected void validateArtifactMap() throws InvalidFormatException {
    if (!(artifactMap.get(MANIFEST_ENTRY) instanceof Properties))
      throw new InvalidFormatException("Missing the " + MANIFEST_ENTRY + "!");

    if (getManifestProperty(LANGUAGE_PROPERTY) == null)
      throw new InvalidFormatException("Missing " + LANGUAGE_PROPERTY + " property in " +
      		MANIFEST_ENTRY + "!");
  }

  /**
   * Retrieves the value to the given key from the manifest.properties
   * entry.
   *
   * @param key
   *
   * @return
   */
  protected String getManifestProperty(String key) {
    Properties manifest = (Properties) artifactMap.get(MANIFEST_ENTRY);

    return manifest.getProperty(key);
  }

  /**
   * Sets a given value for a given key to the manifest.properties entry.
   *
   * @param key
   * @param value
   */
  protected void setManifestProperty(String key, String value) {
    Properties manifest = (Properties) artifactMap.get(MANIFEST_ENTRY);

    manifest.setProperty(key, value);
  }

  /**
   * Retrieves the language code of the material which
   * was used to train the model or x-unspecified if
   * non was set.
   *
   * @return the language code of this model
   */
  public String getLanguage() {
    return getManifestProperty(LANGUAGE_PROPERTY);
  }

  /**
   * Retrieves the OpenNLP version which was used
   * to create the model.
   *
   * @return
   */
  public Version getVersion() {
    String version = getManifestProperty(VERSION_PROPERTY);

    return Version.parse(version);
  }

  /**
   * Serializes the model to the given {@link OutputStream}.
   *
   * @param out
   * @throws IOException
   */
  @SuppressWarnings("unchecked")
  public void serialize(OutputStream out) throws IOException {
    ZipOutputStream zip = new ZipOutputStream(out);

    for (String name : artifactMap.keySet()) {
      zip.putNextEntry(new ZipEntry(name));

      ArtifactSerializer serializer = getArtifactSerializer(name);

      if (serializer == null)
        // TODO: This should never happen
        // add a method to add entries to the artifactMap
        throw new RuntimeException("Missing serializer for " + name);
        
      serializer.serialize(artifactMap.get(name), zip);

      zip.closeEntry();
    }
  }
}
