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
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.io.Serializable;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Properties;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import opennlp.tools.util.BaseToolFactory;
import opennlp.tools.util.InvalidFormatException;
import opennlp.tools.util.Version;
import opennlp.tools.util.ext.ExtensionLoader;

/**
 * This is a common base model which can be used by the components' specific
 * model classes.
 */
// TODO: Provide subclasses access to serializers already in constructor
public abstract class BaseModel implements ArtifactProvider, Serializable {

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

  private static final String SERIALIZER_CLASS_NAME_PREFIX = "serializer-class-";

  private Map<String, ArtifactSerializer> artifactSerializers = new HashMap<>();

  protected Map<String, Object> artifactMap = new HashMap<>();

  protected BaseToolFactory toolFactory;

  private String componentName;

  private boolean subclassSerializersInitiated = false;
  private boolean finishedLoadingArtifacts = false;

  private boolean isLoadedFromSerialized;

  private BaseModel(String componentName, boolean isLoadedFromSerialized) {
    this.isLoadedFromSerialized = isLoadedFromSerialized;

    this.componentName = Objects.requireNonNull(componentName, "componentName must not be null!");
  }

  /**
   * Initializes a {@link BaseModel} instance. The subclass constructor should call the
   * method {@link #checkArtifactMap()} to check the artifact map is in a valid state.
   * <p>
   * Subclasses will have access to custom artifacts and serializers provided
   * by the specified {@code factory}.
   *
   * @param componentName The component name to create the model for.
   * @param languageCode The ISO language code to configure. Must not be {@code null}.
   * @param manifestInfoEntries Mapping for additional information in the manifest.
   * @param factory The {@link BaseToolFactory factory} to use.
   */
  protected BaseModel(String componentName, String languageCode,
      Map<String, String> manifestInfoEntries, BaseToolFactory factory) {

    this(componentName, false);

    Objects.requireNonNull(languageCode, "languageCode must not be null");

    createBaseArtifactSerializers(artifactSerializers);

    Properties manifest = new Properties();
    manifest.setProperty(MANIFEST_VERSION_PROPERTY, "1.0");
    manifest.setProperty(LANGUAGE_PROPERTY, languageCode);
    manifest.setProperty(VERSION_PROPERTY, Version.currentVersion().toString());
    manifest.setProperty(TIMESTAMP_PROPERTY, Long.toString(System.currentTimeMillis()));
    manifest.setProperty(COMPONENT_NAME_PROPERTY, componentName);

    if (manifestInfoEntries != null) {
      for (Map.Entry<String, String> entry : manifestInfoEntries.entrySet()) {
        manifest.setProperty(entry.getKey(), entry.getValue());
      }
    }

    artifactMap.put(MANIFEST_ENTRY, manifest);
    finishedLoadingArtifacts = true;

    if (factory != null) {
      setManifestProperty(FACTORY_NAME, factory.getClass().getCanonicalName());
      artifactMap.putAll(factory.createArtifactMap());

      // new manifest entries
      Map<String, String> entries = factory.createManifestEntries();
      for (Entry<String, String> entry : entries.entrySet()) {
        setManifestProperty(entry.getKey(), entry.getValue());
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
   * Initializes a {@link BaseModel} instance. The subclass constructor should call the
   * method {@link #checkArtifactMap()} to check the artifact map is in a valid state.
   *
   * @param componentName The component name to create the model for.
   * @param languageCode The ISO language code to configure. Must not be {@code null}.
   * @param manifestInfoEntries Mapping for additional information in the manifest.
   */
  protected BaseModel(String componentName, String languageCode, Map<String, String> manifestInfoEntries) {
    this(componentName, languageCode, manifestInfoEntries, null);
  }

  /**
   * Initializes a {@link BaseModel} instance. The subclass constructor should call the
   * method {@link #checkArtifactMap()} to check the artifact map is in a valid state.
   *
   * @param componentName The component name to create the model for.
   * @param in A valid, open {@link InputStream} to read the model from.
   *
   * @throws IOException Thrown if IO errors occurred.
   */
  protected BaseModel(String componentName, InputStream in) throws IOException {
    this(componentName, true);

    loadModel(in);
  }

  /**
   * Initializes a {@link BaseModel} instance. The subclass constructor should call the
   * method {@link #checkArtifactMap()} to check the artifact map is in a valid state.
   *
   * @param componentName The component name to create the model for.
   * @param modelFile A valid, accessible {@link File} to read the model from.
   *
   * @throws IOException Thrown if IO errors occurred.
   */
  protected BaseModel(String componentName, File modelFile) throws IOException  {
    this(componentName, true);

    try (InputStream in = new BufferedInputStream(new FileInputStream(modelFile))) {
      loadModel(in);
    }
  }

  /**
   * Initializes a {@link BaseModel} instance. The subclass constructor should call the
   * method {@link #checkArtifactMap()} to check the artifact map is in a valid state.
   *
   * @param componentName The component name to create the model for.
   * @param modelPath A valid, accessible {@link Path} to read the model from.
   *
   * @throws IOException Thrown if IO errors occurred.
   */
  protected BaseModel(String componentName, Path modelPath) throws IOException  {
    this(componentName, true);

    try (InputStream in = Files.newInputStream(modelPath)) {
      loadModel(in);
    }
  }

  /**
   * Initializes a {@link BaseModel} instance. The subclass constructor should call the
   * method {@link #checkArtifactMap()} to check the artifact map is in a valid state.
   *
   * @param componentName The component name to create the model for.
   * @param modelURL A valid, accessible {@link URL} to read the model from.
   *
   * @throws IOException Thrown if IO errors occurred.
   */
  protected BaseModel(String componentName, URL modelURL) throws IOException  {
    this(componentName, true);

    try (InputStream in = new BufferedInputStream(modelURL.openStream())) {
      loadModel(in);
    }
  }

  private void loadModel(InputStream in) throws IOException {

    Objects.requireNonNull(in, "in must not be null");

    createBaseArtifactSerializers(artifactSerializers);

    if (!in.markSupported()) {
      in = new BufferedInputStream(in);
    }

    // TODO: Discuss this solution, the buffering should
    int MODEL_BUFFER_SIZE_LIMIT = Integer.MAX_VALUE;
    in.mark(MODEL_BUFFER_SIZE_LIMIT);

    final ZipInputStream zip = new ZipInputStream(in);

    // The model package can contain artifacts which are serialized with 3rd party
    // serializers which are configured in the manifest file. To be able to load
    // the model the manifest must be read first, and afterwards all the artifacts
    // can be de-serialized.

    // The ordering of artifacts in a zip package is not guaranteed. The stream is first
    // read until the manifest appears, reseted, and read again to load all artifacts.

    boolean isSearchingForManifest = true;

    ZipEntry entry;
    while ((entry = zip.getNextEntry()) != null && isSearchingForManifest) {

      if ("manifest.properties".equals(entry.getName())) {
        // TODO: Probably better to use the serializer here directly!
        ArtifactSerializer<?> factory = artifactSerializers.get("properties");
        artifactMap.put(entry.getName(), factory.create(zip));
        isSearchingForManifest = false;
      }

      zip.closeEntry();
    }

    initializeFactory();

    loadArtifactSerializers();

    // The Input Stream should always be reset-able because if markSupport returns
    // false it is wrapped before hand into an Buffered InputStream
    in.reset();

    finishLoadingArtifacts(in);

    checkArtifactMap();
  }

  private void initializeFactory() throws InvalidFormatException {
    String factoryName = getManifestProperty(FACTORY_NAME);
    if (factoryName == null) {
      // load the default factory
      Class<? extends BaseToolFactory> factoryClass = getDefaultFactory();
      if (factoryClass != null) {
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
   * Subclasses should override this method if their module has a default
   * {@link BaseToolFactory} subclass.
   *
   * @return The default {@link BaseToolFactory} for the component, or {@code null} if none.
   */
  protected Class<? extends BaseToolFactory> getDefaultFactory() {
    return null;
  }

  /**
   * Loads the {@link ArtifactSerializer artifact serializers}.
   */
  private void loadArtifactSerializers() {
    if (!subclassSerializersInitiated)
      createArtifactSerializers(artifactSerializers);
    subclassSerializersInitiated = true;
  }

  /**
   * Finishes loading the artifacts now that it knows all serializers.
   */
  private void finishLoadingArtifacts(InputStream in)
      throws IOException {

    final ZipInputStream zip = new ZipInputStream(in);

    Map<String, Object> artifactMap = new HashMap<>();

    ZipEntry entry;
    while ((entry = zip.getNextEntry()) != null ) {

      // Note: The manifest.properties file will be read here again,
      // there should be no need to prevent that.

      String entryName = entry.getName();
      String extension = getEntryExtension(entryName);

      ArtifactSerializer<?> factory = artifactSerializers.get(extension);

      String artifactSerializerClazzName =
          getManifestProperty(SERIALIZER_CLASS_NAME_PREFIX + entryName);

      if (artifactSerializerClazzName != null) {
        factory = ExtensionLoader.instantiateExtension(ArtifactSerializer.class, artifactSerializerClazzName);
      }

      if (factory != null) {
        artifactMap.put(entryName, factory.create(zip));
      } else {
        throw new InvalidFormatException("Unknown artifact format: " + extension);
      }

      zip.closeEntry();
    }

    this.artifactMap.putAll(artifactMap);

    finishedLoadingArtifacts = true;
  }

  /**
   * Extracts the "." extension from an entry name.
   *
   * @param entry The entry name which contains the extension
   *
   * @return The extension.
   *
   * @throws InvalidFormatException Thrown if no extension can be extracted
   */
  private String getEntryExtension(String entry) throws InvalidFormatException {
    int extensionIndex = entry.lastIndexOf('.') + 1;

    if (extensionIndex >= entry.length())
        throw new InvalidFormatException("Entry name must have type extension: " + entry);

    return entry.substring(extensionIndex);
  }

  /**
   * @param resourceName The identifying name of a resource to retrieve an
   *                     {link ArtifactSerializer} for.
   * @return Retrieves an {@link ArtifactSerializer artifact serialize}.
   */
  protected ArtifactSerializer<?> getArtifactSerializer(String resourceName) {
    try {
      return artifactSerializers.get(getEntryExtension(resourceName));
    } catch (InvalidFormatException e) {
      throw new IllegalStateException(e);
    }
  }

  /**
   * Creates and registers default {@link ArtifactSerializer artifact serializes}.
   * 
   * @return A {@link Map} with all registered {@link ArtifactSerializer artifact serializes}.
   */
  protected static Map<String, ArtifactSerializer<?>> createArtifactSerializers() {
    Map<String, ArtifactSerializer<?>> serializers = new HashMap<>();

    GenericModelSerializer.register(serializers);
    PropertiesSerializer.register(serializers);
    DictionarySerializer.register(serializers);
    serializers.put("txt", new ByteArraySerializer());
    serializers.put("html", new ByteArraySerializer());

    return serializers;
  }

  /**
   * Registers all {@link ArtifactSerializer} for their artifact file name extensions.
   * The registered {@link ArtifactSerializer serializers} are used to
   * create and serialize resources in the model package.
   * <p>
   * Override this method to register custom {@link ArtifactSerializer serializers}.
   * <p>
   * <b>Note:</b>
   * Subclasses should generally invoke {@code super.createArtifactSerializers}
   * at the beginning of this method.
   * <p>
   * This method is called during construction.
   *
   * @param serializers The key of the map is the file extension used to look up
   *                    an {@link ArtifactSerializer}.
   */
  protected void createArtifactSerializers(Map<String, ArtifactSerializer> serializers) {
    if (this.toolFactory != null)
      serializers.putAll(this.toolFactory.createArtifactSerializersMap());
  }

  private void createBaseArtifactSerializers(Map<String, ArtifactSerializer> serializers) {
    serializers.putAll(createArtifactSerializers());
  }

  /**
   * Validates the parsed artifacts. If something is not
   * valid, subclasses should throw an {@link InvalidFormatException}.
   *
   * <p>
   * <b>Note:</b>
   * Subclasses should generally invoke {@code super.validateArtifactMap}
   * at the beginning of this method.
   *
   * @throws InvalidFormatException Thrown if artifacts were found to be inconsistent.
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
        // Support OpenNLP 1.x models.
        if (version.getMajor() != 1 && version.getMajor() != 2) {
          throw new InvalidFormatException("Model version " + version + " is not supported by this ("
              + Version.currentVersion() + ") version of OpenNLP!");
        }

        // Reject loading a snapshot model with a non-snapshot version
        if (!Version.currentVersion().isSnapshot() && version.isSnapshot()) {
          throw new InvalidFormatException("Model version " + version
              + " is a snapshot - snapshot models are not supported by this non-snapshot version ("
              + Version.currentVersion() + ") of OpenNLP!");
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
    if (toolFactory != null) {
      toolFactory.validateArtifactMap();
    }
  }

  /**
   * Checks the artifact map.
   * <p>
   * A subclass should call this method from a constructor which accepts the individual
   * artifact map items, to validate that these items form a valid model.
   * <p>
   *
   * @throws IllegalArgumentException Thrown if the artifacts are not valid.
   * @throws IllegalStateException Thrown if {@link  BaseModel#finishLoadingArtifacts(InputStream)} was
   *                               not called by a subclass.
   */
  protected void checkArtifactMap() {
    if (!finishedLoadingArtifacts)
      throw new IllegalStateException(
          "The method BaseModel.finishLoadingArtifacts(..) was not called by BaseModel subclass.");
    try {
      validateArtifactMap();
    } catch (InvalidFormatException e) {
      throw new IllegalArgumentException(e);
    }
  }

  @Override
  public final String getManifestProperty(String key) {
    Properties manifest = (Properties) artifactMap.get(MANIFEST_ENTRY);
    return manifest.getProperty(key);
  }

  /**
   * Sets a given value for a given key to the {@code manifest.properties} mapping.
   *
   * @param key The identifying key.
   * @param value The value to set at {@code key}.
   */
  protected final void setManifestProperty(String key, String value) {
    Properties manifest = (Properties) artifactMap.get(MANIFEST_ENTRY);
    manifest.setProperty(key, value);
  }

  @Override
  public final String getLanguage() {
    return getManifestProperty(LANGUAGE_PROPERTY);
  }

  /**
   * @return Retrieves the OpenNLP {@link Version} which was used to create the model.
   */
  public final Version getVersion() {
    String version = getManifestProperty(VERSION_PROPERTY);
    return Version.parse(version);
  }

  /**
   * Serializes the model to the given {@link OutputStream}.
   *
   * @param out The {@link OutputStream} to write the model to.
   *            
   * @throws IOException Thrown if IO errors occurred.
   * @throws IllegalStateException Thrown if {@link  BaseModel#loadArtifactSerializers()} was
   *                               not called in a subclass constructor.
   */
  @SuppressWarnings("unchecked")
  public final void serialize(OutputStream out) throws IOException {
    if (!subclassSerializersInitiated) {
      throw new IllegalStateException(
          "The method BaseModel.loadArtifactSerializers() was not called by BaseModel subclass constructor.");
    }

    for (Entry<String, Object> entry : artifactMap.entrySet()) {
      final String name = entry.getKey();
      final Object artifact = entry.getValue();
      if (artifact instanceof SerializableArtifact serializableArtifact) {

        String artifactSerializerName = serializableArtifact
            .getArtifactSerializerClass().getName();

        setManifestProperty(SERIALIZER_CLASS_NAME_PREFIX + name,
            artifactSerializerName);
      }
    }

    ZipOutputStream zip = new ZipOutputStream(out);

    for (Entry<String, Object> entry : artifactMap.entrySet()) {
      String name = entry.getKey();
      zip.putNextEntry(new ZipEntry(name));

      Object artifact = entry.getValue();

      if (skipEntryForSerialization(entry)) {
        continue;
      }

      ArtifactSerializer serializer = getArtifactSerializer(name);

      // If model is serialize-able always use the provided serializer
      if (artifact instanceof SerializableArtifact serializableArtifact) {

        String artifactSerializerName =
            serializableArtifact.getArtifactSerializerClass().getName();

        serializer = ExtensionLoader.instantiateExtension(ArtifactSerializer.class, artifactSerializerName);
      }

      if (serializer == null) {
        throw new IllegalStateException("Missing serializer for " + name);
      }

      serializer.serialize(artifactMap.get(name), zip);

      zip.closeEntry();
    }

    zip.finish();
    zip.flush();
  }

  /**
   * Serializes the model to the specified {@link File}.
   *
   * @param f The write-accessible {@link File} to write the model to.
   *
   * @throws IOException Thrown if IO errors occurred.
   * @throws IllegalStateException Thrown if {@link  BaseModel#loadArtifactSerializers()} was
   *                               not called in a subclass constructor.
   */
  public final void serialize(File f) throws IOException {
    try (OutputStream out = new BufferedOutputStream(new FileOutputStream(f))) {
      serialize(out);
    }
  }

  /**
   * Serializes the model to the specified {@link Path}.
   *
   * @param p The write-accessible {@link Path} to write the model to.
   *
   * @throws IOException Thrown if IO errors occurred.
   * @throws IllegalStateException Thrown if {@link  BaseModel#loadArtifactSerializers()} was
   *                               not called in a subclass constructor.
   */
  public final void serialize(Path p) throws IOException {
    serialize(p.toFile());
  }

  @Override
  @SuppressWarnings("unchecked")
  public <T> T getArtifact(String key) {
    Object artifact = artifactMap.get(key);
    if (artifact == null)
      return null;
    return (T) artifact;
  }

  @Override
  public boolean isLoadedFromSerialized() {
    return isLoadedFromSerialized;
  }

  // These methods are required to serialize/deserialize the model because
  // many of the included objects in this model are not Serializable.
  // An alternative to this solution is to make all included objects
  // Serializable and remove the writeObject and readObject methods.
  // This will allow the usage of final for fields that should not change.

  private void writeObject(ObjectOutputStream out) throws IOException {
    out.writeUTF(componentName);
    this.serialize(out);
  }

  private void readObject(final ObjectInputStream in) throws IOException {

    isLoadedFromSerialized = true;
    artifactSerializers = new HashMap<>();
    artifactMap = new HashMap<>();

    componentName = in.readUTF();

    this.loadModel(in);
  }

  /**
   * @param entry the entry to check
   * @return {@code true}, if the given entry should be skipped for serialization.
   */
  protected boolean skipEntryForSerialization(Entry<String, Object> entry) {
    return false;
  }
}
