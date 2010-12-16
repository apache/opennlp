/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 * 
 *   http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package opennlp.maxent;

/**
 * Main file for opennlp.maxent.  Right now just tells the user that
 * the executable jar doesn't actually execute anything but the
 * message telling the user that the jar doesn't execute anything
 * but...
*/
public class Main {

    public static void main (String[] args) {
	System.out.println(
       "\n********************************************************************\n"
     + "The \"executable\" jar of OpenNLP Maxent does not currently execute\n"
     + "anything except this message.  It exists only so that there is a jar\n"
     + "of the package which contains all of the other jar dependencies\n"
     + "needed by Maxent so that users can download it and be able to use\n"
     + "it to build maxent applications without hunting down the other jars.\n"
     + "********************************************************************\n"
        );
    }
    
}
