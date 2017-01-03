/*
 * Copyright 2013 The Apache Software Foundation.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package opennlp.tools.entitylinker;

import java.util.HashMap;
import java.util.Objects;

/**
 * Stores a minimal tuple of information. Intended to be used with LinkedSpan
 *
 *
 */
public abstract class BaseLink {

  private String itemParentID;
  private String itemID;
  private String itemName;
  private String itemType;
  private HashMap<String, Double> scoreMap = new HashMap<>();

  public BaseLink() {
  }

  public BaseLink(String itemParentID, String itemID, String itemName, String itemType) {
    this.itemParentID = itemParentID;
    this.itemID = itemID;
    this.itemName = itemName;
    this.itemType = itemType;
  }

  /**
   * Any parent ID for the linked item
   * @return
   */
  public String getItemParentID() {
    return itemParentID;
  }

  /**
   * returns the parent ID of the linked item
   * @param itemParentID
   */
  public void setItemParentID(String itemParentID) {
    this.itemParentID = itemParentID;
  }

  /**
   * returns the itemid
   *
   * @return
   */
  public String getItemID() {
    return itemID;
  }

  /**
   * sets the item id. This field can store, for example, the primary key of a
   * row in an external/linked database
   *
   * @param itemID
   */
  public void setItemID(String itemID) {
    this.itemID = itemID;
  }

  /**
   * returns the name
   *
   * @return
   */
  public String getItemName() {
    return itemName;
  }

  /**
   * Sets the item name. An item name can be the native name (often a normalized
   * version of something) from an external linked database
   *
   * @param itemName
   */
  public void setItemName(String itemName) {
    this.itemName = itemName;
  }

  /**
   * returns the type
   *
   * @return
   */
  public String getItemType() {
    return itemType;
  }

  /**
   * sets the item type. An item type can be the native type from an external
   * linked database. For instance, a product type or code
   *
   * @param itemType
   */
  public void setItemType(String itemType) {
    this.itemType = itemType;
  }

  public HashMap<String, Double> getScoreMap() {
    return scoreMap;
  }

  public void setScoreMap(HashMap<String, Double> scoreMap) {
    this.scoreMap = scoreMap;
  }

  @Override
  public String toString() {
    return "\tBaseLink" + "\n\titemParentID=" + itemParentID + ", \n\titemID=" + itemID + ", \n\titemName=" + itemName + ", \n\titemType=" + itemType + ", \n\tscoreMap=" + scoreMap + "\n";
  }

  @Override
  public int hashCode() {
    int hash = 7;
    hash = 71 * hash + Objects.hashCode(this.itemParentID);
    hash = 71 * hash + Objects.hashCode(this.itemID);
    hash = 71 * hash + Objects.hashCode(this.itemName);
    hash = 71 * hash + Objects.hashCode(this.itemType);
    return hash;
  }

  @Override
  public boolean equals(Object obj) {
    if (obj == null) {
      return false;
    }
    if (getClass() != obj.getClass()) {
      return false;
    }
    final BaseLink other = (BaseLink) obj;
    if (!Objects.equals(this.itemParentID, other.itemParentID)) {
      return false;
    }
    if (!Objects.equals(this.itemID, other.itemID)) {
      return false;
    }
    return Objects.equals(this.itemName, other.itemName) && Objects.equals(this.itemType, other.itemType);
  }
}