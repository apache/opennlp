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

import opennlp.model.MaxentModel;

/**
 * A object which can be used to ensure that a Maxent application can swap the
 * model currently in use with a new one in a thread-safe manner without
 * stopping the servicing of requests. Use this if your maxent application is
 * a heavy-weight one or you have only one particular MaxentModel to use with
 * your application. If your application class is lightweight and you will be
 * creating multiple instances of it with different underlying models, consider
 * using a DomainToModelMap object to ensure thread-safe model swapping.
 *
 * <p>For example, in your application, create a ModelReplacementManager as
 * follows:
 *
 *     <pre>
 *     private final ModelReplacementManager replacementManager =
 *	  new ModelReplacementManager(
 *	      new ModelSetter() {
 *		  public void setModel(MaxentModel m) {
 *		      model = m;
 *		  }
 *	      }
 *	  );
 *     </pre>
 *
 * where "model" would be the actual variable name of the model used by your
 * application which you wish to be able to swap (you might have other models
 * which need their own ModelReplacementManager).
 *
 * <p>You'll also need a method to swap the model which calls the manager's
 * replaceModel(MaxentModel m) method, e.g.,
 *
 *     <pre>
 *     public void replaceModel (MaxentModel newmod) {
 *	  replacementManager.replaceModel(newmod);
 *    }
 *     </pre>
 * 
 * Then, in the code that uses the model, you need to inform the
 * ModelReplacementManager when a thread is beginning to use the model and when
 * it no longer needs to be sure that the same model is being used.  For
 * example, it is quite common to evaluate a particular context, get back a
 * double[] which has the normalized probabilities of each of the outcomes given
 * that context, and then request the name of a particular outcome.  The model
 * cannot be swapped during that time since the mapping from outcome labels to
 * unique will (probably) be different between the different models.  So, do as
 * follows:
 *
 *     <pre>
 *	  replacementManager.startUsingModel();
 *	    // some code which evaluates the context, e.g.,
 *	    double[] probs = model.eval(someContext);
 *	    // some code which returns a particular outcome
 *	    if (model.getBestOutcome(probs).equals("T") ...
 *	  replacementManager.finishUsingModel();
 *     </pre>
 *
 * The manager will then make sure that all requests which are currently being
 * serviced are completed before the new model is swapped in.  New requests
 * which are made while the models are being swapped are forced to wait for the
 * swap to finish.  These requests will then be serviced by the new model.
 */
public class ModelReplacementManager {
  private ModelSetter setter;

  private int users = 0;
  private boolean replacementCanProceed = true;
  private Thread replacementThread = null;

  public ModelReplacementManager(ModelSetter ms) {
    setter = ms;
  }

  /**
   * Inform the manager that a thread is using the model. If a replacement is
   * underway, the thread is forced to join the replacement thread and thus wait
   * until it is finished to begin using the model.
   */
  public void startUsingModel() {
    if (replacementThread != null) {
      try {
        replacementThread.join();
      } catch (InterruptedException e) {
      }
    }
    replacementCanProceed = false;
    users++;
  }

  /**
   * Inform the manager that a thread is done using the model, and thus is not
   * dependending on it being unchanged.
   */
  public void finishUsingModel() {
    users--;
    if (users <= 0)
      replacementCanProceed = true;
  }

  /**
   * Replace the old model with a new one, forcing the replacement to wait until
   * all threads using the old model have finished using it.
   * 
   * @param model
   *          The new model which is being swapped in.
   */
  public synchronized void replaceModel(MaxentModel model) {
    replacementThread = Thread.currentThread();
    while (!replacementCanProceed)
      Thread.yield();
    setter.setModel(model);
    replacementThread = null;
  }
    
}
