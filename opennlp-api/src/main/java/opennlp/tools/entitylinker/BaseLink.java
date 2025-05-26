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

package opennlp.tools.entitylinker;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Represents a minimal tuple of information.
 * Intended to be used with {@link LinkedSpan}.
 *
 * @see EntityLinker
 * @see LinkedSpan
 */
public abstract class BaseLink {

  private String itemParentID;
  private String itemID;
  private String itemName;
  private String itemType;
  private Map<String, Double> scoreMap = new HashMap<>();

  public BaseLink(String itemParentID, String itemID, String itemName, String itemType) {
    this.itemParentID = itemParentID;
    this.itemID = itemID;
    this.itemName = itemName;
    this.itemType = itemType;
  }

  public String getItemParentID() {
    return itemParentID;
  }

  /**
   * @param itemParentID The parent ID of the linked item
   */
  public void setItemParentID(String itemParentID) {
    this.itemParentID = itemParentID;
  }

  public String getItemID() {
    return itemID;
  }

  /**
   * @param itemID This field can store, for example, the primary key of
   *               a now in an external/linked data source.
   */
  public void setItemID(String itemID) {
    this.itemID = itemID;
  }

  public String getItemName() {
    return itemName;
  }

  /**
   * @param itemName An item name can be the native name (often a normalized
   *                 version of something) from an external linked data source.
   */
  public void setItemName(String itemName) {
    this.itemName = itemName;
  }

  public String getItemType() {
    return itemType;
  }

  /**
   *
   * @param itemType An item type can be the native type from an external
   *                 linked database. For instance, a product type or code.
   */
  public void setItemType(String itemType) {
    this.itemType = itemType;
  }

  public Map<String, Double> getScoreMap() {
    return scoreMap;
  }

  public void setScoreMap(Map<String, Double> scoreMap) {
    this.scoreMap = scoreMap;
  }

  @Override
  public String toString() {
    return "\tBaseLink" + "\n\titemParentID=" + itemParentID + ", \n\titemID=" + itemID
        + ", \n\titemName=" + itemName + ", \n\titemType=" + itemType + ", \n\tscoreMap="
        + scoreMap + "\n";
  }

  @Override
  public int hashCode() {
    return Objects.hash(itemParentID, itemID, itemName, itemType);
  }

  @Override
  public boolean equals(Object obj) {
    if (obj == this) {
      return true;
    }

    if (obj instanceof BaseLink other) {

      return Objects.equals(itemParentID, other.itemParentID)
          && Objects.equals(itemID, other.itemID)
          && Objects.equals(itemName, other.itemName)
          && Objects.equals(itemType, other.itemType);
    }

    return false;
  }
}
