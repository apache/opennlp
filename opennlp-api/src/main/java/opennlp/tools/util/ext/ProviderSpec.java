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

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Describes what a {@link Provider} should create: an optional location, such as a model file,
 * and string options. Instances are immutable.
 *
 * @see Provider#supports(ProviderSpec)
 * @see Provider#create(ProviderSpec)
 * @see Providers#select(ProviderSpec)
 * @since 3.0.0
 */
public final class ProviderSpec {

  private static final String FILE_SCHEME = "file";
  private static final char PATH_SEPARATOR = '/';
  private static final char QUERY_SEPARATOR = '?';
  private static final char USER_INFO_SEPARATOR = '@';
  private static final char ESCAPE = '%';
  private static final String AUTHORITY_PREFIX = "//";
  private static final ProviderSpec EMPTY = new ProviderSpec(null, Map.of());

  private final URI location;
  private final Map<String, String> options;

  /**
   * @param location The location, or {@code null} if the spec has none.
   * @param options The options. Must not be {@code null}, an option name must not be
   *                {@code null} or blank and an option value must not be {@code null}.
   * @throws IllegalArgumentException Thrown if {@code options} is invalid.
   */
  private ProviderSpec(final URI location, final Map<String, String> options) {
    if (options == null) {
      throw new IllegalArgumentException("options must not be null");
    }
    final Map<String, String> copy = new LinkedHashMap<>();
    for (final Map.Entry<String, String> option : options.entrySet()) {
      if (option.getKey() == null || option.getValue() == null) {
        throw new IllegalArgumentException("options must not contain a null key or value");
      }
      if (option.getKey().isBlank()) {
        throw new IllegalArgumentException("option names must not be blank");
      }
      copy.put(option.getKey(), option.getValue());
    }
    this.location = location;
    this.options = Collections.unmodifiableMap(copy);
  }

  /**
   * @return A spec without location and options. Never {@code null}.
   */
  public static ProviderSpec empty() {
    return EMPTY;
  }

  /**
   * Creates a spec with options and without location.
   *
   * @param options The options. Must not be {@code null}, an option name must not be
   *                {@code null} or blank and an option value must not be {@code null}.
   * @return The spec. Never {@code null}.
   * @throws IllegalArgumentException Thrown if {@code options} is invalid.
   */
  public static ProviderSpec of(final Map<String, String> options) {
    return new ProviderSpec(null, options);
  }

  /**
   * Creates a spec with a location and without options.
   *
   * @param location The absolute location. Must not be {@code null}.
   * @return The spec. Never {@code null}.
   * @throws IllegalArgumentException Thrown if {@code location} is {@code null} or not absolute.
   */
  public static ProviderSpec of(final URI location) {
    return of(location, Map.of());
  }

  /**
   * Creates a spec with a location and options.
   *
   * @param location The absolute location, which is taken as it is, unlike the location of
   *                 {@link #of(Path, Map)}. Must not be {@code null}.
   * @param options The options. Must not be {@code null}, an option name must not be
   *                {@code null} or blank and an option value must not be {@code null}.
   * @return The spec. Never {@code null}.
   * @throws IllegalArgumentException Thrown if {@code location} is {@code null} or not absolute,
   *                                  or if {@code options} is invalid.
   */
  public static ProviderSpec of(final URI location, final Map<String, String> options) {
    if (location == null) {
      throw new IllegalArgumentException("location must not be null");
    }
    if (!location.isAbsolute()) {
      throw new IllegalArgumentException("location must be an absolute URI: " + location);
    }
    return new ProviderSpec(location, options);
  }

  /**
   * Creates a spec for a file or directory without options, see {@link #of(Path, Map)}.
   *
   * @param location The file or directory. Must not be {@code null}.
   * @return The spec. Never {@code null}.
   * @throws IllegalArgumentException Thrown if {@code location} is {@code null}.
   */
  public static ProviderSpec of(final Path location) {
    return of(location, Map.of());
  }

  /**
   * Creates a spec for a file or directory with options. A relative path is resolved against the
   * working directory of the running process when the spec is created, so a spec that is kept in
   * a {@code static} field must not be created from a relative path. The file system is not
   * read, so the location of a directory carries no trailing separator and a path holding
   * {@code ..} is kept as it is.
   *
   * @param location The file or directory. Must not be {@code null}.
   * @param options The options. Must not be {@code null}, an option name must not be
   *                {@code null} or blank and an option value must not be {@code null}.
   * @return The spec. Never {@code null}.
   * @throws IllegalArgumentException Thrown if {@code location} is {@code null}, or if
   *                                  {@code options} is invalid.
   */
  public static ProviderSpec of(final Path location, final Map<String, String> options) {
    if (location == null) {
      throw new IllegalArgumentException("location must not be null");
    }
    return new ProviderSpec(uriOf(location), options);
  }

  /**
   * Converts a path of the default file system without reading it, unlike {@link Path#toUri()},
   * which appends a separator to an existing directory and would make the location depend on
   * what is on disk when the spec is created. The path is made absolute but neither normalized
   * nor resolved against symbolic links.
   *
   * @param location The file or directory. Must not be {@code null}.
   * @return The location of {@code location}. Never {@code null}.
   * @throws IllegalArgumentException Thrown if {@code location} has no URI.
   */
  private static URI uriOf(final Path location) {
    final Path absolute = location.toAbsolutePath();
    if (!absolute.getFileSystem().equals(FileSystems.getDefault())) {
      return absolute.toUri();
    }
    final String separated = absolute.toString().replace(File.separatorChar, PATH_SEPARATOR);
    final String path = separated.charAt(0) == PATH_SEPARATOR ? separated : "/" + separated;
    // A UNC path starts with the two separators an empty authority would be read as.
    final String authority = path.startsWith(AUTHORITY_PREFIX) ? null : "";
    try {
      return new URI(FILE_SCHEME, authority, path, null, null);
    } catch (final URISyntaxException e) {
      throw new IllegalArgumentException("location has no URI: " + location, e);
    }
  }

  /**
   * @return The location, or {@link Optional#empty()} if this spec has none.
   */
  public Optional<URI> location() {
    return Optional.ofNullable(location);
  }

  /**
   * @return The location as a path, or {@link Optional#empty()} if this spec has no location or
   *         the location is not a hierarchical {@code file} URI of the default file system.
   */
  public Optional<Path> path() {
    if (location == null || location.isOpaque() || !FILE_SCHEME.equalsIgnoreCase(location.getScheme())) {
      return Optional.empty();
    }
    try {
      return Optional.of(Path.of(location));
    } catch (final IllegalArgumentException e) {
      return Optional.empty();
    }
  }

  /**
   * @return The options in insertion order. Unmodifiable and never {@code null}.
   */
  public Map<String, String> options() {
    return options;
  }

  /**
   * Reads an option.
   *
   * @param key The option name. Must not be {@code null}.
   * @param defaultValue The value to return if the option is absent. May be {@code null}.
   * @return The option value, or {@code defaultValue}.
   * @throws IllegalArgumentException Thrown if {@code key} is {@code null}.
   */
  public String option(final String key, final String defaultValue) {
    if (key == null) {
      throw new IllegalArgumentException("key must not be null");
    }
    return options.getOrDefault(key, defaultValue);
  }

  /**
   * Checks whether every option of this spec has one of the given names.
   *
   * @param names The accepted option names. Must not be {@code null} or contain a
   *              {@code null} element.
   * @return {@code true} if this spec has no option with another name.
   * @throws IllegalArgumentException Thrown if {@code names} is {@code null} or contains a
   *                                  {@code null} element.
   */
  public boolean hasOnlyOptions(final String... names) {
    if (names == null) {
      throw new IllegalArgumentException("names must not be null");
    }
    for (final String name : names) {
      if (name == null) {
        throw new IllegalArgumentException("names must not contain a null element");
      }
    }
    for (final String key : options.keySet()) {
      if (!contains(names, key)) {
        return false;
      }
    }
    return true;
  }

  /**
   * Checks whether the last path segment of the location ends with a suffix, ignoring case. A
   * query and a fragment are not part of the comparison. For an opaque URI, such as
   * {@code jar:file:/models.jar!/model.onnx}, the scheme-specific part is used as path. This is a
   * convenience for routing a request to a provider, not a validation of the location.
   *
   * @param suffix The suffix, for example {@code .onnx}. Must not be {@code null} or empty.
   * @return {@code true} if this spec has a location and its last path segment ends with
   *         {@code suffix}.
   * @throws IllegalArgumentException Thrown if {@code suffix} is {@code null} or empty.
   */
  public boolean locationEndsWith(final String suffix) {
    if (suffix == null || suffix.isEmpty()) {
      throw new IllegalArgumentException("suffix must not be null or empty");
    }
    if (location == null) {
      return false;
    }
    final String raw = location.isOpaque()
        ? location.getRawSchemeSpecificPart() : location.getRawPath();
    if (raw == null) {
      return false;
    }
    final String path = decode(cutQuery(raw));
    if (hasControlCharacter(path)) {
      return false;
    }
    final int nameStart = path.lastIndexOf(PATH_SEPARATOR) + 1;
    final int suffixStart = path.length() - suffix.length();
    return suffixStart >= nameStart && path.regionMatches(true, suffixStart, suffix, 0, suffix.length());
  }

  /** {@inheritDoc} */
  @Override
  public boolean equals(final Object other) {
    if (this == other) {
      return true;
    }
    if (!(other instanceof ProviderSpec spec)) {
      return false;
    }
    return Objects.equals(location, spec.location) && options.equals(spec.options);
  }

  /** {@inheritDoc} */
  @Override
  public int hashCode() {
    return Objects.hash(location, options);
  }

  /**
   * {@inheritDoc}
   * The option values and the user info, query and fragment of the location are omitted, as they
   * may hold credentials.
   */
  @Override
  public String toString() {
    return "ProviderSpec[location=" + printableLocation() + ", options=" + options.keySet() + "]";
  }

  private boolean contains(final String[] names, final String key) {
    for (final String name : names) {
      if (key.equals(name)) {
        return true;
      }
    }
    return false;
  }

  private String printableLocation() {
    return location == null ? null : printable(location);
  }

  /**
   * Renders a location without the parts that may hold credentials, for a message or a log entry.
   *
   * @param location The location. Must not be {@code null}.
   * @return The location without its user info, query and fragment. Never {@code null}.
   */
  static String printable(final URI location) {
    if (location.isOpaque()) {
      return location.getScheme() + ':' + redact(cutQuery(location.getRawSchemeSpecificPart()));
    }
    final StringBuilder text = new StringBuilder(location.getScheme()).append(':');
    if (location.getRawAuthority() != null) {
      text.append(AUTHORITY_PREFIX).append(withoutUserInfo(location.getRawAuthority()));
    }
    return text.append(location.getRawPath() == null ? "" : location.getRawPath()).toString();
  }

  /**
   * @return The scheme-specific part of an opaque location, such as
   *         {@code https://user@host/models.jar!/model.onnx}, without the user info of a nested
   *         authority.
   */
  private static String redact(final String part) {
    final int prefix = part.indexOf(AUTHORITY_PREFIX);
    final int start = prefix < 0 ? 0 : prefix + AUTHORITY_PREFIX.length();
    final int path = part.indexOf(PATH_SEPARATOR, start);
    final int end = path < 0 ? part.length() : path;
    return part.substring(0, start) + withoutUserInfo(part.substring(start, end))
        + part.substring(end);
  }

  private static String withoutUserInfo(final String authority) {
    final int userInfo = authority.lastIndexOf(USER_INFO_SEPARATOR);
    return userInfo < 0 ? authority : authority.substring(userInfo + 1);
  }

  private boolean hasControlCharacter(final String path) {
    for (int character = 0; character < path.length(); character++) {
      if (Character.isISOControl(path.charAt(character))) {
        return true;
      }
    }
    return false;
  }

  /**
   * Decodes the percent escapes of a raw location, unlike {@link java.net.URLDecoder}, which
   * would also read {@code +} as a space. A run of escapes is decoded as UTF-8, and the
   * characters between them, which a raw location holds unescaped if they are not ASCII, are
   * kept as they are.
   *
   * @return The decoded part.
   */
  private String decode(final String part) {
    if (part.indexOf(ESCAPE) < 0) {
      return part;
    }
    final StringBuilder decoded = new StringBuilder(part.length());
    final ByteArrayOutputStream escapes = new ByteArrayOutputStream();
    for (int character = 0; character < part.length(); character++) {
      final int escaped = escapedByte(part, character);
      if (escaped < 0) {
        appendEscapes(decoded, escapes);
        decoded.append(part.charAt(character));
      } else {
        escapes.write(escaped);
        character += 2;
      }
    }
    appendEscapes(decoded, escapes);
    return decoded.toString();
  }

  private void appendEscapes(final StringBuilder decoded, final ByteArrayOutputStream escapes) {
    if (escapes.size() > 0) {
      decoded.append(escapes.toString(StandardCharsets.UTF_8));
      escapes.reset();
    }
  }

  private int escapedByte(final String part, final int character) {
    if (part.charAt(character) != ESCAPE || character + 2 >= part.length()) {
      return -1;
    }
    final int high = Character.digit(part.charAt(character + 1), 16);
    final int low = Character.digit(part.charAt(character + 2), 16);
    return high < 0 || low < 0 ? -1 : high << 4 | low;
  }

  private static String cutQuery(final String part) {
    final int query = part.indexOf(QUERY_SEPARATOR);
    return query < 0 ? part : part.substring(0, query);
  }
}
