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

import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Describes a request for a component: where its model or resource lives, if anywhere, and
 * the options a {@link ComponentProvider} may need. A provider judges from the spec alone
 * whether it {@link ComponentProvider#supports(ComponentSpec) supports} the request, without
 * opening anything. Instances are immutable.
 *
 * @since 3.0.0
 */
public final class ComponentSpec {

  private static final ComponentSpec EMPTY = new ComponentSpec(null, Map.of());

  private final Path location;
  private final Map<String, String> options;

  private ComponentSpec(Path location, Map<String, String> options) {
    this.location = location;
    this.options = options;
  }

  /**
   * @return A spec without a location and without options, for components that need
   *         neither. Never {@code null}.
   */
  public static ComponentSpec empty() {
    return EMPTY;
  }

  /**
   * Creates a spec for a model file or directory.
   *
   * @param location The model file or directory. Must not be {@code null}.
   * @return The spec. Never {@code null}.
   * @throws IllegalArgumentException Thrown if {@code location} is {@code null}.
   */
  public static ComponentSpec of(Path location) {
    if (location == null) {
      throw new IllegalArgumentException("location must not be null");
    }
    return new ComponentSpec(location, Map.of());
  }

  /**
   * Creates a spec from options only.
   *
   * @param options The options. Must not be {@code null} or hold a null key or value.
   * @return The spec. Never {@code null}.
   * @throws IllegalArgumentException Thrown if {@code options} is {@code null} or holds a
   *         null key or value.
   */
  public static ComponentSpec of(Map<String, String> options) {
    return new ComponentSpec(null, copy(options));
  }

  /**
   * Creates a spec for a model file or directory with options.
   *
   * @param location The model file or directory, or {@code null} when the component has none.
   * @param options The options. Must not be {@code null} or hold a null key or value.
   * @return The spec. Never {@code null}.
   * @throws IllegalArgumentException Thrown if {@code options} is {@code null} or holds a
   *         null key or value.
   */
  public static ComponentSpec of(Path location, Map<String, String> options) {
    return new ComponentSpec(location, copy(options));
  }

  private static Map<String, String> copy(Map<String, String> options) {
    if (options == null) {
      throw new IllegalArgumentException("options must not be null");
    }
    Map<String, String> copy = new LinkedHashMap<>();
    for (Map.Entry<String, String> option : options.entrySet()) {
      if (option.getKey() == null || option.getValue() == null) {
        throw new IllegalArgumentException("options must not hold a null key or value");
      }
      copy.put(option.getKey(), option.getValue());
    }
    return Collections.unmodifiableMap(copy);
  }

  /**
   * @return The model file or directory, or {@code null} when the request has none.
   */
  public Path location() {
    return location;
  }

  /**
   * @return The options, in insertion order; unmodifiable and never {@code null}.
   */
  public Map<String, String> options() {
    return options;
  }

  /**
   * Reads one option.
   *
   * @param key The option name. Must not be {@code null}.
   * @param defaultValue The value when the option is absent; may be {@code null}.
   * @return The option value, or {@code defaultValue}.
   * @throws IllegalArgumentException Thrown if {@code key} is {@code null}.
   */
  public String option(String key, String defaultValue) {
    if (key == null) {
      throw new IllegalArgumentException("key must not be null");
    }
    return options.getOrDefault(key, defaultValue);
  }

  /**
   * Tells whether every option name is one of the given names, which lets a provider claim
   * only requests written for it.
   *
   * @param names The option names the provider understands. Must not be {@code null}.
   * @return {@code true} if no option has another name.
   * @throws IllegalArgumentException Thrown if {@code names} is {@code null}.
   */
  public boolean hasOnlyOptions(String... names) {
    if (names == null) {
      throw new IllegalArgumentException("names must not be null");
    }
    for (String key : options.keySet()) {
      boolean known = false;
      for (String name : names) {
        if (key.equals(name)) {
          known = true;
          break;
        }
      }
      if (!known) {
        return false;
      }
    }
    return true;
  }

  /**
   * Tells whether the location is a file whose name ends with the given suffix, compared
   * without regard to letter case.
   *
   * @param suffix The file name suffix, such as {@code .onnx}. Must not be {@code null}.
   * @return {@code true} if there is a location, it has a file name, and the name ends with
   *         {@code suffix} ignoring case.
   * @throws IllegalArgumentException Thrown if {@code suffix} is {@code null}.
   */
  public boolean locationEndsWith(String suffix) {
    if (suffix == null) {
      throw new IllegalArgumentException("suffix must not be null");
    }
    if (location == null || location.getFileName() == null) {
      return false;
    }
    String name = location.getFileName().toString();
    return name.regionMatches(true, name.length() - suffix.length(), suffix, 0, suffix.length());
  }

  /**
   * Returns a spec with one more option, or a replaced one.
   *
   * @param key The option name. Must not be {@code null}.
   * @param value The option value. Must not be {@code null}.
   * @return A new spec; this one is unchanged.
   * @throws IllegalArgumentException Thrown if {@code key} or {@code value} is {@code null}.
   */
  public ComponentSpec withOption(String key, String value) {
    if (key == null || value == null) {
      throw new IllegalArgumentException("key and value must not be null");
    }
    Map<String, String> copy = new LinkedHashMap<>(options);
    copy.put(key, value);
    return new ComponentSpec(location, Collections.unmodifiableMap(copy));
  }

  @Override
  public boolean equals(Object other) {
    if (this == other) {
      return true;
    }
    if (!(other instanceof ComponentSpec)) {
      return false;
    }
    ComponentSpec spec = (ComponentSpec) other;
    return Objects.equals(location, spec.location) && options.equals(spec.options);
  }

  @Override
  public int hashCode() {
    return Objects.hash(location, options);
  }

  @Override
  public String toString() {
    return "ComponentSpec[location=" + location + ", options=" + options + "]";
  }
}
