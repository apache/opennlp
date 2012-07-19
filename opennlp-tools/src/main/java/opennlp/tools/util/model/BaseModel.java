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


package opennlp.tools.util.model;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import opennlp.tools.util.BaseToolFactory;
import opennlp.tools.util.InvalidFormatException;
import opennlp.tools.util.Version;
import opennlp.tools.util.ext.ExtensionLoader;

/**
 * This model is a common based which can be used by the components
 * model classes.
 * 
 * TODO:
 * Provide sub classes access to serializers already in constructor
 */
public abstract class BaseModel implements ArtifactProvider {

  protected static final String MANIFEST_ENTRY = "manifest.properties";
  protected static final String FACTORY_NAME = "factory";
  
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

  protected final Map<String, Object> artifactMap = new HashMap<String, Object>();
  
  protected BaseToolFactory toolFactory;
  
  private final String componentName;

  private Map<String, byte[]> leftoverArtifacts;

  private boolean subclassSerializersInitiated = false;
  private boolean finishedLoadingArtifacts = false;
  
  private final boolean isLoadedFromSerialized;

  private BaseModel(String componentName, boolean isLoadedFromSerialized) {
    this.isLoadedFromSerialized = isLoadedFromSerialized;
    
    if (componentName == null)
      throw new IllegalArgumentException("componentName must not be null!");
    
    this.componentName = componentName;
  }
  
  /**
   * Initializes the current instance. The sub-class constructor should call the
   * method {@link #checkArtifactMap()} to check the artifact map is OK.
   * <p>
   * Sub-classes will have access to custom artifacts and serializers provided
   * by the factory.
   * 
   * @param componentName
   *          the component name
   * @param languageCode
   *          the language code
   * @param manifestInfoEntries
   *          additional information in the manifest
   * @param factory
   *          the factory
   */
  protected BaseModel(String componentName, String languageCode,
      Map<String, String> manifestInfoEntries, BaseToolFactory factory) {

    this(componentName, false);

    if (languageCode == null)
        throw new IllegalArgumentException("languageCode must not be null!");

    createBaseArtifactSerializers(artifactSerializers);
    
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
    finishedLoadingArtifacts = true;
    
    if (factory!=null) {
      setManifestProperty(FACTORY_NAME, factory.getClass().getCanonicalName());
      artifactMap.putAll(factory.createArtifactMap());

      // new manifest entries
      Map<String, String> entries = factory.createManifestEntries();
      for (String key : entries.keySet()) {
        setManifestProperty(key, entries.get(key));
      }
    }
    
    try {
      initializeFactory();
    } catch (InvalidFormatException e) {
      throw new IllegalArgumentException("Could not initialize tool factory. ", e);
    }
    loadArtifactSerializers();
  }

  /**
   * Initializes the current instance. The sub-class constructor should call the
   * method {@link #checkArtifactMap()} to check the artifact map is OK.
   * 
   * @param componentName
   *          the component name
   * @param languageCode
   *          the language code
   * @param manifestInfoEntries
   *          additional information in the manifest
   */
  protected BaseModel(String componentName, String languageCode, Map<String, String> manifestInfoEntries) {
    this(componentName, languageCode, manifestInfoEntries, null);
  }
  
  /**
   * Initializes the current instance.
   * 
   * @param componentName the component name
   * @param in the input stream containing the model
   *
   * @throws IOException
   * @throws InvalidFormatException
   */
  protected BaseModel(String componentName, InputStream in) throws IOException, InvalidFormatException {
    this(componentName, true);
    
    if (in == null)
        throw new IllegalArgumentException("in must not be null!");

    loadModel(in);
  }

  protected BaseModel(String componentName, File modelFile) throws IOException, InvalidFormatException  {
    this(componentName, true);
    
    InputStream in = new BufferedInputStream(new FileInputStream(modelFile));
    
    try {
      loadModel(in);
    }
    finally {
      in.close();
    }
  }

  protected BaseModel(String componentName, URL modelURL) throws IOException, InvalidFormatException  {
    this(componentName, true);
    
    InputStream in = modelURL.openStream();

    try {
      loadModel(in);
    }
    finally {
      in.close();
    }
  }

  private void loadModel(InputStream in) throws IOException, InvalidFormatException {
    createBaseArtifactSerializers(artifactSerializers);

    final ZipInputStream zip = new ZipInputStream(in);
    
    // will read it in two steps, first using the known factories, latter the
    // unknown.
    leftoverArtifacts = new HashMap<String, byte[]>();

    ZipEntry entry;
    while((entry = zip.getNextEntry()) != null ) {

      String extension = getEntryExtension(entry.getName());

      ArtifactSerializer factory = artifactSerializers.get(extension);

      if (factory == null) {
        /* TODO: find a better solution, that would consume less memory */
        byte[] bytes = toByteArray(zip);
        leftoverArtifacts.put(entry.getName(), bytes);
      } else {
        artifactMap.put(entry.getName(), factory.create(zip));
      }
      
      zip.closeEntry();
    }

    initializeFactory();
    
    loadArtifactSerializers();
    finishLoadingArtifacts();
    checkArtifactMap();
  }
  
  private void initializeFactory() throws InvalidFormatException {
    String factoryName = getManifestProperty(FACTORY_NAME);
    if (factoryName == null) {
      // load the default factory
      Class<? extends BaseToolFactory> factoryClass = getDefaultFactory();
      if(factoryClass != null) {
        this.toolFactory = BaseToolFactory.create(factoryClass, this);
      }
    } else {
      try {
        this.toolFactory = BaseToolFactory.create(factoryName, this);
      } catch (InvalidFormatException e) {
        throw new IllegalArgumentException(e);
      }
    }
  }
  
  /**
   * Sub-classes should override this method if their module has a default
   * BaseToolFactory sub-class.
   * 
   * @return the default {@link BaseToolFactory} for the module, or null if none.
   */
  protected Class<? extends BaseToolFactory> getDefaultFactory() {
    return null;
  }
  
  /**
   * Loads the artifact serializers.
   */
  private void loadArtifactSerializers() {
    if (!subclassSerializersInitiated)
      createArtifactSerializers(artifactSerializers);
    subclassSerializersInitiated = true;
  }

  /**
   * Finish loading the artifacts now that it knows all serializers.
   */
  private void finishLoadingArtifacts()
      throws InvalidFormatException, IOException {
    finishedLoadingArtifacts = true;
    if (leftoverArtifacts == null || leftoverArtifacts.size() == 0) {
      return;
    }

    Map<String, Object> artifactMap = new HashMap<String, Object>();
    
    for (String entryName : leftoverArtifacts.keySet()) {
      
      String extension = getEntryExtension(entryName);

      if (leftoverArtifacts.containsKey(entryName)) {
        ArtifactSerializer factory = artifactSerializers.get(extension);

        if (factory == null) {
          throw new InvalidFormatException("Unknown artifact format: "
              + extension);
        } else {
          artifactMap.put(entryName, factory.create(new ByteArrayInputStream(leftoverArtifacts.get(entryName))));
        }
      }
    }
    this.leftoverArtifacts = null;
    this.artifactMap.putAll(artifactMap);
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

  protected ArtifactSerializer getArtifactSerializer(String resourceName) {
    String extension = null;
    try {
      extension = getEntryExtension(resourceName);
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
    if(this.toolFactory != null)
      serializers.putAll(this.toolFactory.createArtifactSerializersMap());
  }

  private void createBaseArtifactSerializers(
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
        throw new InvalidFormatException("Unable to parse model version '" + versionString + "'!", e);
      }
      
      // Version check is only performed if current version is not the dev/debug version
      if (!Version.currentVersion().equals(Version.DEV_VERSION)) {
        // Major and minor version must match, revision might be 
        if (Version.currentVersion().getMajor() != version.getMajor() ||
            Version.currentVersion().getMinor() != version.getMinor()) {
          throw new InvalidFormatException("Model version " + version + " is not supported by this (" 
              + Version.currentVersion() +") version of OpenNLP!");
        }
        
        // Reject loading a snapshot model with a non-snapshot version
        if (!Version.currentVersion().isSnapshot() && version.isSnapshot()) {
          throw new InvalidFormatException("Model version " + version + " is a snapshot - snapshot models are not " +
          		"supported by this non-snapshot version (" + Version.currentVersion() + ") of OpenNLP!");
        }
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
    
    // Validate the factory. We try to load it using the ExtensionLoader. It
    // will return the factory, null or raise an exception 
    String factoryName = getManifestProperty(FACTORY_NAME);
    if (factoryName != null) {
      try {
        if (ExtensionLoader.instantiateExtension(BaseToolFactory.class,
            factoryName) == null) {
          throw new InvalidFormatException(
              "Could not load an user extension specified by the model: "
                  + factoryName);
        }
      } catch (Exception e) {
        throw new InvalidFormatException(
            "Could not load an user extension specified by the model: "
                + factoryName, e);
      }
    }
    
    // validate artifacts declared by the factory
    if(toolFactory != null) {
      toolFactory.validateArtifactMap();
    }
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
    if (!finishedLoadingArtifacts)
      throw new IllegalStateException(
          "The method BaseModel.finishLoadingArtifacts(..) was not called by BaseModel sub-class.");
    try {
      validateArtifactMap();
    } catch (InvalidFormatException e) {
      throw new IllegalArgumentException(e);
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
    if (!subclassSerializersInitiated) {
      throw new IllegalStateException(
          "The method BaseModel.loadArtifactSerializers() was not called by BaseModel subclass constructor.");
    }
    
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
  
  @SuppressWarnings("unchecked")
  public <T> T getArtifact(String key) {
    Object artifact = artifactMap.get(key);
    if(artifact == null)
      return null;
    return (T) artifact;
  }
  
  private static byte[] toByteArray(InputStream input) throws IOException {
    ByteArrayOutputStream output = new ByteArrayOutputStream();
    byte[] buffer = new byte[1024 * 4];
    int count = 0;
    int n = 0;
    while (-1 != (n = input.read(buffer))) {
      output.write(buffer, 0, n);
      count += n;
    }
    return output.toByteArray();
  }

  public boolean isLoadedFromSerialized() {
    return isLoadedFromSerialized;
  }
}
