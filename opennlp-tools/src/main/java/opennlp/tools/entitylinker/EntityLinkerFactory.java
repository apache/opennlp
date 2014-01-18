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
 * Generates an EntityLinker implementation via properties file configuration
 *
 */
public class EntityLinkerFactory {

  /**
   *
   * @param <I>        An type that extends EntityLinkerProperties.
   * @param entityType The type of entity being linked to. This value is used to
   *                   retrieve the implementation of the entitylinker from the
   *                   entitylinker properties file.
   * @param properties An object that extends EntityLinkerProperties. This
   *                   object will be passed into the implemented EntityLinker
   *                   init(..) within this getLinker method.
   * @return an EntityLinker impl
   */
  public static synchronized <I extends EntityLinkerProperties> EntityLinker getLinker(String entityType, I properties) {
    if (entityType == null || properties == null) {
      throw new IllegalArgumentException("Null argument in entityLinkerFactory");
    }
    EntityLinker linker = null;
    try {
      String linkerImplFullName = properties.getProperty("linker." + entityType, "");
      Class theClass = Class.forName(linkerImplFullName);
      linker = (EntityLinker) theClass.newInstance();
      System.out.println("EntityLinker factory instantiated: " + linker.getClass().getName());
      linker.init(properties);

    } catch (Exception ex) {
      System.out.println("Error in EntityLinker factory. Check the entity linker properties file. The entry must be formatted as linker.<type>=<fullclassname>, i.e linker.person=org.my.company.MyPersonLinker\n" + ex);
    }
    return linker;
  }
}
