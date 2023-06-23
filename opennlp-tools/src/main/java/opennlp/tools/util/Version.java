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


package opennlp.tools.util;

import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;
import java.util.Properties;

/**
 * The {@link Version} class represents the OpenNLP Tools library version.
 * <p>
 * The version has three parts:
 * <ul>
 * <li>Major: OpenNLP Tools libraries with a different major version are not interchangeable.</li>
 * <li>Minor: OpenNLP Tools libraries with an identical major version, but different
 *     minor version may be interchangeable. See release notes for further details.</li>
 * <li>Revision: OpenNLP Tools libraries with same major and minor version, but a different
 *     revision, are fully interchangeable.</li>
 * </ul>
 */
public class Version {

  private static final String DEV_VERSION_STRING = "0.0.0-SNAPSHOT";

  public static final Version DEV_VERSION = Version.parse(DEV_VERSION_STRING);

  private static final String SNAPSHOT_MARKER = "-SNAPSHOT";

  private final int major;

  private final int minor;

  private final int revision;

  private final boolean snapshot;

  /**
   * Initializes a {@link Version} instance with the provided version elements.
   *
   * @param major Must not be negative.
   * @param minor Must not be negative.
   * @param revision Must not be negative.
   * @param snapshot {@code true} if the version represents a snapshot, {@code false} otherwise.
   */
  public Version(int major, int minor, int revision, boolean snapshot) {
    this.major = major;
    this.minor = minor;
    this.revision = revision;
    this.snapshot = snapshot;
  }

  /**
   * Initializes a {@link Version} instance with the provided version elements.
   * The {@link Version} will not be a snapshot, yet a release version.
   *
   * @param major Must not be negative.
   * @param minor Must not be negative.
   * @param revision Must not be negative.
   */
  public Version(int major, int minor, int revision) {
    this(major, minor, revision, false);
  }

  /**
   * @return Retrieves the major version, guaranteed to be non-negative.
   */
  public int getMajor() {
    return major;
  }

  /**
   * @return Retrieves the minor version, guaranteed to be non-negative.
   */
  public int getMinor() {
    return minor;
  }

  /**
   * @return Retrieves the revision version, guaranteed to be non-negative.
   */
  public int getRevision() {
    return revision;
  }

  public boolean isSnapshot() {
    return snapshot;
  }

  /**
   * The {@link #parse(String)} method can create an instance
   * of {@link Version} with the returned version value string.
   *
   * @return Retrieves a human-readable version representation.
   */
  @Override
  public String toString() {
    return getMajor() + "." + getMinor() + "." + getRevision() + (isSnapshot() ? SNAPSHOT_MARKER : "");
  }

  @Override
  public int hashCode() {
    return Objects.hash(getMajor(), getMinor(), getRevision(), isSnapshot());
  }

  @Override
  public boolean equals(Object obj) {
    if (obj == this) {
      return true;
    }

    if (obj instanceof Version version) {

      return getMajor() == version.getMajor()
          && getMinor() == version.getMinor()
          && getRevision() == version.getRevision()
          && isSnapshot() == version.isSnapshot();
    }

    return false;
  }

  /**
   * Return a new {@link Version} initialized to the value
   * represented by the specified {@link String}
   *
   * @param version The string to be parsed
   *
   * @return The version represented by the string value
   *
   * @throws NumberFormatException Thrown if {@code version} does not adhere to a valid form.
   */
  public static Version parse(String version) {

    int indexFirstDot = version.indexOf('.');

    int indexSecondDot = version.indexOf('.', indexFirstDot + 1);

    if (indexFirstDot == -1 || indexSecondDot == -1) {
      throw new NumberFormatException("Invalid version format '" + version + "', expected two dots!");
    }

    int indexFirstDash = version.indexOf('-');

    int versionEnd;
    if (indexFirstDash == -1) {
      versionEnd = version.length();
    }
    else {
      versionEnd = indexFirstDash;
    }

    boolean snapshot = version.endsWith(SNAPSHOT_MARKER);

    return new Version(Integer.parseInt(version.substring(0, indexFirstDot)),
        Integer.parseInt(version.substring(indexFirstDot + 1, indexSecondDot)),
        Integer.parseInt(version.substring(indexSecondDot + 1, versionEnd)), snapshot);
  }

  /**
   * @return Retrieves the current version of the OpenNLP Tools library.
   */
  public static Version currentVersion() {

    Properties manifest = new Properties();

    // Try to read the version from the version file if it is available,
    // otherwise set the version to the development version

    try (InputStream versionIn =
        Version.class.getResourceAsStream("opennlp.version")) {
      if (versionIn != null) {
        manifest.load(versionIn);
      }
    } catch (IOException e) {
      // ignore error
    }

    String versionString = manifest.getProperty("OpenNLP-Version", DEV_VERSION_STRING);

    if (versionString.equals("${project.version}"))
      versionString = DEV_VERSION_STRING;

    return Version.parse(versionString);
  }
}
