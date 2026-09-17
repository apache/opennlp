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

package opennlp.tools.util.ext;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import opennlp.tools.doccat.FeatureGenerator;
import opennlp.tools.util.BaseToolFactory;
import opennlp.tools.util.SequenceCodec;
import opennlp.tools.util.featuregen.GeneratorFactory.AbstractXmlFeatureGeneratorFactory;
import opennlp.tools.util.jvm.StringInterner;
import opennlp.tools.util.model.ArtifactSerializer;
import opennlp.tools.util.model.BaseModel;
import opennlp.tools.util.model.ModelLoader;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Keeps {@link RuntimeExtensionRegistrar} complete: every concrete class in this
 * module that OpenNLP refers to by name must be registered, or a model naming it
 * falls back to reflection and fails in a native image.
 * <p>
 * The compiled classes are scanned reflectively here; that is test-only code.
 */
public class RuntimeExtensionRegistrarTest {

  private static final Path CLASSES = Path.of("target", "classes");

  private static List<Class<?>> classes;

  @BeforeAll
  static void loadClasses() throws IOException {
    assertTrue(Files.isDirectory(CLASSES), "compiled classes expected at " + CLASSES.toAbsolutePath());
    classes = new ArrayList<>();
    try (Stream<Path> files = Files.walk(CLASSES)) {
      files.filter(p -> p.toString().endsWith(".class"))
          .map(CLASSES::relativize)
          .map(p -> p.toString().replace(p.getFileSystem().getSeparator(), ".")
              .substring(0, p.toString().length() - ".class".length()))
          .filter(n -> !n.equals("module-info") && !n.endsWith("package-info"))
          .forEach(n -> {
            try {
              classes.add(Class.forName(n, false, RuntimeExtensionRegistrarTest.class.getClassLoader()));
            } catch (ClassNotFoundException | LinkageError e) {
              throw new IllegalStateException("Cannot load " + n, e);
            }
          });
    }
  }

  private static boolean isConcretePublic(Class<?> c) {
    int mod = c.getModifiers();
    return Modifier.isPublic(mod) && !Modifier.isAbstract(mod) && !c.isInterface()
        && !c.isAnonymousClass() && !c.isLocalClass();
  }

  private static Stream<Class<?>> concreteImplementationsOf(Class<?> type) {
    return classes.stream()
        .filter(type::isAssignableFrom)
        .filter(c -> c != type)
        .filter(RuntimeExtensionRegistrarTest::isConcretePublic);
  }

  static Stream<Class<?>> byNameExtensions() {
    return Stream.of(BaseToolFactory.class, ArtifactSerializer.class,
            AbstractXmlFeatureGeneratorFactory.class, SequenceCodec.class,
            FeatureGenerator.class, StringInterner.class)
        .flatMap(RuntimeExtensionRegistrarTest::concreteImplementationsOf)
        .filter(c -> c != BaseModel.class);
  }

  static Stream<Class<?>> modelTypes() {
    return concreteImplementationsOf(BaseModel.class)
        .filter(c -> Stream.of(c.getConstructors())
            .anyMatch(k -> k.getParameterCount() == 1 && k.getParameterTypes()[0] == InputStream.class));
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("byNameExtensions")
  void testByNameExtensionIsRegistered(Class<?> extension) {
    ExtensionRegistry registry = ExtensionRegistry.getDefault();
    assertNotNull(registry.supplier(extension.getName()),
        extension.getName() + " must be registered in RuntimeExtensionRegistrar");
    assertTrue(extension.isInstance(registry.supplier(extension.getName()).get()),
        extension.getName() + " supplier must return an instance of the registered class");
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("modelTypes")
  void testModelLoaderIsRegistered(Class<?> modelType) {
    assertNotNull(ExtensionRegistry.getDefault().factory(modelType.getName(), ModelLoader.class),
        modelType.getName() + " must have a ModelLoader registered in RuntimeExtensionRegistrar");
  }
}
