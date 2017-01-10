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
 * The {@link Version} class represents the OpenNlp Tools library version.
 * <p>
 * The version has three parts:
 * <ul>
 * <li>Major: OpenNlp Tools libraries with a different major version are not interchangeable.</li>
 * <li>Minor: OpenNlp Tools libraries with an identical major version, but different
 *     minor version may be interchangeable. See release notes for further details.</li>
 * <li>Revision: OpenNlp Tools libraries with same major and minor version, but a different
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
   * Initializes the current instance with the provided
   * versions.
   *
   * @param major
   * @param minor
   * @param revision
   * @param snapshot
   */
  public Version(int major, int minor, int revision, boolean snapshot) {
    this.major = major;
    this.minor = minor;
    this.revision = revision;
    this.snapshot = snapshot;
  }

  /**
   * Initializes the current instance with the provided
   * versions. The version will not be a snapshot version.
   *
   * @param major
   * @param minor
   * @param revision
   */
  public Version(int major, int minor, int revision) {
   this(major, minor, revision, false);
  }

  /**
   * Retrieves the major version.
   *
   * @return major version
   */
  public int getMajor() {
    return major;
  }

  /**
   * Retrieves the minor version.
   *
   * @return minor version
   */
  public int getMinor() {
    return minor;
  }

  /**
   * Retrieves the revision version.
   *
   * @return revision version
   */
  public int getRevision() {
    return revision;
  }

  public boolean isSnapshot() {
    return snapshot;
  }

  /**
   * Retrieves the version string.
   *
   * The {@link #parse(String)} method can create an instance
   * of {@link Version} with the returned version value string.
   *
   * @return the version value string
   */
  @Override
  public String toString() {
    return Integer.toString(getMajor()) + "." + Integer.toString(getMinor()) +
      "." + Integer.toString(getRevision()) + (isSnapshot() ? SNAPSHOT_MARKER : "");
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

    if (obj instanceof Version) {
      Version version = (Version) obj;

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
   * @param version the string to be parsed
   *
   * @return the version represented by the string value
   *
   * @throws NumberFormatException if the string does
   *     not contain a valid version
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
   * Retrieves the current version of the OpenNlp Tools library.
   *
   * @return the current version
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

    if (versionString.equals("${pom.version}"))
      versionString = DEV_VERSION_STRING;

    return Version.parse(versionString);
  }
}
