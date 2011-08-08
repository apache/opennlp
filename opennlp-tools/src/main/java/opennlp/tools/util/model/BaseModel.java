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
  
  private static final String MANIFEST_VERSION_PROPERTY = "Manifest-Version";
  private static final String COMPONENT_NAME_PROPERTY = "Component-Name";
  private static final String VERSION_PROPERTY = "OpenNLP-Version";
  private static final String TIMESTAMP_PROPERTY = "Timestamp";
  private static final String LANGUAGE_PROPERTY = "Language";
  
  public static final String TRAINING_CUTOFF_PROPERTY = "Training-Cutoff";
  public static final String TRAINING_ITERATIONS_PROPERTY = "Training-Iterations";
  public static final String TRAINING_EVENTHASH_PROPERTY = "Training-Eventhash";
  
  private Map<String, ArtifactSerializer> artifactSerializers =
      new HashMap<String, ArtifactSerializer>();

  protected final Map<String, Object> artifactMap;

  private final String componentName;
  
  /**
   * Initializes the current instance.
   *
   * @param languageCode
   * @param manifestInfoEntries additional information in the manifest
   */
  protected BaseModel(String componentName, String languageCode, Map<String, String> manifestInfoEntries) {

    if (componentName == null)
        throw new IllegalArgumentException("componentName must not be null!");
    
    if (languageCode == null)
        throw new IllegalArgumentException("languageCode must not be null!");

    this.componentName = componentName;
    
    artifactMap = new HashMap<String, Object>();
    
    createArtifactSerializers(artifactSerializers);
    
    Properties manifest = new Properties();
    manifest.setProperty(MANIFEST_VERSION_PROPERTY, "1.0");
    manifest.setProperty(LANGUAGE_PROPERTY, languageCode);
    manifest.setProperty(VERSION_PROPERTY, Version.currentVersion().toString());
    manifest.setProperty(TIMESTAMP_PROPERTY, 
        Long.toString(System.currentTimeMillis()));
    manifest.setProperty(COMPONENT_NAME_PROPERTY, componentName);
    
    if (manifestInfoEntries != null) {
      for (Map.Entry<String, String> entry : manifestInfoEntries.entrySet()) {
        manifest.setProperty(entry.getKey(), entry.getValue());
      }
    }
      
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
  protected BaseModel(String componentName, InputStream in) throws IOException, InvalidFormatException {

    if (componentName == null)
      throw new IllegalArgumentException("componentName must not be null!");
    
    if (in == null)
        throw new IllegalArgumentException("in must not be null!");

    this.componentName = componentName;
    
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

  protected ArtifactSerializer getArtifactSerializer(String resoruceName) {
    String extension = null;
    try {
      extension = getEntryExtension(resoruceName);
    } catch (InvalidFormatException e) {
      throw new IllegalStateException(e);
    }

    return artifactSerializers.get(extension);  
  }
  
  protected static Map<String, ArtifactSerializer> createArtifactSerializers() {
    Map<String, ArtifactSerializer> serializers = new HashMap<String, ArtifactSerializer>();
    
    GenericModelSerializer.register(serializers);
    PropertiesSerializer.register(serializers);
    DictionarySerializer.register(serializers);
    
    return serializers;
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
  protected void createArtifactSerializers(
      Map<String, ArtifactSerializer> serializers) {
    serializers.putAll(createArtifactSerializers());
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

    // First check version, everything else might change in the future
    String versionString = getManifestProperty(VERSION_PROPERTY);
    
    if (versionString != null) {
      Version version;
      
      try {
        version = Version.parse(versionString);
      }
      catch (NumberFormatException e) {
        throw new InvalidFormatException("Unable to parse model version!, e");
      }
      
      // Major and minor version must match, revision might be 
      if (Version.currentVersion().getMajor() != version.getMajor() ||
          Version.currentVersion().getMinor() != version.getMinor()) {
        throw new InvalidFormatException("Model version " + version + " is not supported by this (" 
            + Version.currentVersion() +") version of OpenNLP!");
      }
      
      // Reject loading a snapshot model with a non-snapshot version
      if (!Version.currentVersion().isSnapshot() && version.isSnapshot()) {
        throw new InvalidFormatException("Model is a snapshot models are not" +
        		"supported by release versions!");
      }
    }
    else {
      throw new InvalidFormatException("Missing " + VERSION_PROPERTY + " property in " +
            MANIFEST_ENTRY + "!");
    }
    
    if (getManifestProperty(COMPONENT_NAME_PROPERTY) == null)
      throw new InvalidFormatException("Missing " + COMPONENT_NAME_PROPERTY + " property in " +
            MANIFEST_ENTRY + "!");
   
    if (!getManifestProperty(COMPONENT_NAME_PROPERTY).equals(componentName)) 
        throw new InvalidFormatException("The " + componentName + " cannot load a model for the " + 
            getManifestProperty(COMPONENT_NAME_PROPERTY) + "!");
    
    if (getManifestProperty(LANGUAGE_PROPERTY) == null)
      throw new InvalidFormatException("Missing " + LANGUAGE_PROPERTY + " property in " +
      		MANIFEST_ENTRY + "!");
  }

  /**
   * Checks the artifact map. 
   * <p>
   * A subclass should call this method from a constructor which accepts the individual
   * artifact map items, to validate that these items form a valid model.
   * <p>
   * If the artifacts are not valid an IllegalArgumentException will be thrown.
   */
  protected void checkArtifactMap() {
    try {
      validateArtifactMap();
    } catch (InvalidFormatException e) {
      throw new IllegalArgumentException(e.getMessage());
    }
  }
  
  /**
   * Retrieves the value to the given key from the manifest.properties
   * entry.
   *
   * @param key
   *
   * @return the value
   */
  public final String getManifestProperty(String key) {
    Properties manifest = (Properties) artifactMap.get(MANIFEST_ENTRY);

    return manifest.getProperty(key);
  }

  /**
   * Sets a given value for a given key to the manifest.properties entry.
   *
   * @param key
   * @param value
   */
  protected final void setManifestProperty(String key, String value) {
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
  public final String getLanguage() {
    return getManifestProperty(LANGUAGE_PROPERTY);
  }

  /**
   * Retrieves the OpenNLP version which was used
   * to create the model.
   *
   * @return the version
   */
  public final Version getVersion() {
    String version = getManifestProperty(VERSION_PROPERTY);

    return Version.parse(version);
  }

  /**
   * Serializes the model to the given {@link OutputStream}.
   *
   * @param out stream to write the model to
   * @throws IOException
   */
  @SuppressWarnings("unchecked")
  public final void serialize(OutputStream out) throws IOException {
    ZipOutputStream zip = new ZipOutputStream(out);

    for (String name : artifactMap.keySet()) {
      zip.putNextEntry(new ZipEntry(name));

      ArtifactSerializer serializer = getArtifactSerializer(name);

      if (serializer == null) {
        throw new IllegalStateException("Missing serializer for " + name);
      }
      
      serializer.serialize(artifactMap.get(name), zip);

      zip.closeEntry();
    }
    
    zip.finish();
    zip.flush();
  }
}
