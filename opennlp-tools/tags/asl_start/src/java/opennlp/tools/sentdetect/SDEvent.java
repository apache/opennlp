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



package opennlp.tools.sentdetect;

import opennlp.model.Event;

/**
 * An Event which can hold a pointer to another Event for use in a
 * linked list.
 *
 * Created: Sat Oct 27 11:53:55 2001
 *
 * @author Eric D. Friedman
 * @version $Id: SDEvent.java,v 1.3 2008-09-28 18:12:11 tsmorton Exp $
 */

class SDEvent extends Event  {
  private static final long serialVersionUID = 1;
  
  SDEvent next;
    
  SDEvent(String oc, String[] c) {
    super(oc,c);
  }   
}
