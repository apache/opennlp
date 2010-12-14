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


package opennlp.tools.util;

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

  private final int major;

  private final int minor;

  private final int revision;

  /**
   * Initializes the current instance with the provided
   * versions.
   *
   * @param major
   * @param minor
   * @param revision
   */
  public Version(int major, int minor, int revision) {
    this.major = major;
    this.minor = minor;
    this.revision = revision;
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

  /**
   * Retrieves the version string.
   *
   * The {@link #parse(String)} method can create an instance
   * of {@link Version} with the returned version value string.
   *
   * @return the version value string
   */
  public String toString() {
    return Integer.toString(getMajor()) + "." + Integer.toString(getMinor()) +
      "." + Integer.toString(getRevision());
  }

  @Override
  public boolean equals(Object o) {
    if (o == this) {
      return true;
    } else if (o instanceof Version) {
      Version version = (Version) o;

      return getMajor() == version.getMajor()
          && getMinor() == version.getMinor()
          && getRevision() == version.getRevision();
    }
    else {
      return false;
    }
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
   * not contain a valid version
   */
  public static Version parse(String version) {

    int indexFirstDot = version.indexOf('.');

    int indexSecondDot = version.indexOf('.', indexFirstDot + 1);

    if (indexFirstDot == -1 || indexSecondDot == -1)
        throw new NumberFormatException("Invalid version!");

    return new Version(Integer.parseInt(version.substring(0, indexFirstDot)),
        Integer.parseInt(version.substring(indexFirstDot + 1, indexSecondDot)),
        Integer.parseInt(version.substring(indexSecondDot + 1)));
  }

  /**
   * Retrieves the current version of the OpenNlp Tools library.
   *
   * @return the current version
   */
  public static Version currentVersion() {
    return new Version(1, 5, 0);
  }
}
