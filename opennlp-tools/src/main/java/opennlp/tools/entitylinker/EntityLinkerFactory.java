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

/**
 * Generates an EntityLinker implementation via properties file
 * configuration
 *
 */
public class EntityLinkerFactory {

  /**
   * instantiates a single linker based on properties file configuration.
   * @param entityType the type of entity, i.e. person, organization, location
   * @param properties the properties file that holds the configuration for entitylinkers.
   * @return
   */
  public static synchronized EntityLinker getLinker(String entityType, EntityLinkerProperties properties) {
    if (entityType == null || properties == null) {
      throw new IllegalArgumentException("Null argument in entityLinkerFactory");
    }
    EntityLinker linker = null;
    try {
      String linkerImplFullName = properties.getProperty("linker." + entityType,"");
      Class theClass = Class.forName(linkerImplFullName);
      linker = (EntityLinker) theClass.newInstance();
      System.out.println("EntityLinker factory instantiated: " + linker.getClass().getName());
      linker.setEntityLinkerProperties(properties);

    } catch (Exception ex) {
      System.out.println("Check the entity linker properties file. The entry must be formatted as linker.<type>=<fullclassname>, i.e linker.person=org.my.company.MyPersonLinker\n" + ex);
   }
    return linker;
  }

}
