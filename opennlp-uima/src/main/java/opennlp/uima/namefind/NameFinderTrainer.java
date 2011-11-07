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

package opennlp.uima.namefind;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import opennlp.maxent.GIS;
import opennlp.tools.namefind.NameFinderME;
import opennlp.tools.namefind.NameSample;
import opennlp.tools.namefind.NameSampleDataStream;
import opennlp.tools.namefind.TokenNameFinderModel;
import opennlp.tools.util.ObjectStream;
import opennlp.tools.util.ObjectStreamUtils;
import opennlp.tools.util.PlainTextByLineStream;
import opennlp.tools.util.Span;
import opennlp.uima.util.CasConsumerUtil;
import opennlp.uima.util.ContainingConstraint;
import opennlp.uima.util.OpennlpUtil;
import opennlp.uima.util.UimaUtil;

import org.apache.uima.cas.CAS;
import org.apache.uima.cas.FSIndex;
import org.apache.uima.cas.Type;
import org.apache.uima.cas.TypeSystem;
import org.apache.uima.cas.text.AnnotationFS;
import org.apache.uima.collection.CasConsumer_ImplBase;
import org.apache.uima.resource.ResourceInitializationException;
import org.apache.uima.resource.ResourceProcessException;
import org.apache.uima.util.Level;
import org.apache.uima.util.Logger;
import org.apache.uima.util.ProcessTrace;

/**
 * OpenNLP NameFinder trainer.
 * <p>
 * Mandatory parameters
 * <table border=1>
 *   <tr><th>Type</th> <th>Name</th> <th>Description</th></tr>
 *   <tr><td>String</td> <td>opennlp.uima.ModelName</td> <td>The name of the model file</td></tr>
 *   <tr><td>String</td> <td>opennlp.uima.Language</td> <td>The language code</td></tr>
 *   <tr><td>String</td> <td>opennlp.uima.SentenceType</td> <td>The full name of the sentence type</td></tr>
 *   <tr><td>String</td> <td>opennlp.uima.TokenType</td> <td>The full name of the token type</td></tr>
 *   <tr><td>String</td> <td>opennlp.uima.NameType</td> <td>The full name of the name type</td></tr>
 *  </table>
 *  
 * Optional parameters
 * <table border=1>
 *   <tr><th>Type</th> <th>Name</th> <th>Description</th></tr>
 *   <tr><td>String</td> <td>opennlp.uima.AdditionalTrainingDataFile</td> <td>Training file which contains additional data in the OpenNLP format</td></tr>
 *   <tr><td>String</td> <td>opennlp.uima.AdditionalTrainingDataEncoding</td> <td>Encoding of the additional training data</td></tr>
 *   <tr><td>Integer</td> <td>opennlp.uima.Cutoff</td> <td>(default=5)</td></tr>
 *   <tr><td>Integer</td> <td>opennlp.uima.Iterations</td> <td>(default=100)</td></tr>
 * </table>
 * <p>
 */
public final class NameFinderTrainer extends CasConsumer_ImplBase {
    
  private Logger logger;
  
  private String modelPath;
  
  private String additionalTrainingDataFile;
  
  private String additionalTrainingDataEncoding;
  
  private Type sentenceType;

  private Type tokenType;

  private Type nameType;
  
  private String language;
  
  private int cutoff;
  
  private int iterations;
  
  // TODO: Keeping all events in memory limits the size of the training corpus
  // Possible solutions:
  // - Write all events to disk
  // - Directly start indexing with a blocking sample stream, the indexer will then write everything
  //   to disk or could store the events much more space efficient in memory
  
  private List<NameSample> nameFinderSamples = new ArrayList<NameSample>();
  
  /**
   * Initializes the current instance.
   */
  public void initialize() throws ResourceInitializationException {
    
    super.initialize();
    
    logger = getUimaContext().getLogger();
    
    if (logger.isLoggable(Level.INFO)) {
      logger.log(Level.INFO, "Initializing the OpenNLP Name Trainer.");
    } 
    
    modelPath = CasConsumerUtil.getRequiredStringParameter(getUimaContext(),
        UimaUtil.MODEL_PARAMETER);
    
    language = CasConsumerUtil.getRequiredStringParameter(getUimaContext(),
        UimaUtil.LANGUAGE_PARAMETER);
    
    cutoff = CasConsumerUtil.getOptionalIntegerParameter(getUimaContext(), UimaUtil.CUTOFF_PARAMETER, 5);
    iterations = CasConsumerUtil.getOptionalIntegerParameter(getUimaContext(), UimaUtil.ITERATIONS_PARAMETER, 100);
    
    additionalTrainingDataFile = CasConsumerUtil.getOptionalStringParameter(
        getUimaContext(), UimaUtil.ADDITIONAL_TRAINING_DATA_FILE);
    
    // If the additional training data is specified, the encoding must be provided!
    if (additionalTrainingDataFile != null) {
      additionalTrainingDataEncoding = CasConsumerUtil.getRequiredStringParameter(
          getUimaContext(), UimaUtil.ADDITIONAL_TRAINING_DATA_ENCODING);
    }
  }

  /**
   * Initialize the current instance with the given type system.
   */
  public void typeSystemInit(TypeSystem typeSystem)
      throws ResourceInitializationException {

    String sentenceTypeName = 
        CasConsumerUtil.getRequiredStringParameter(getUimaContext(),
        UimaUtil.SENTENCE_TYPE_PARAMETER);

    sentenceType = CasConsumerUtil.getType(typeSystem, sentenceTypeName);

    String tokenTypeName = CasConsumerUtil.getRequiredStringParameter(getUimaContext(),
        UimaUtil.TOKEN_TYPE_PARAMETER);

    tokenType = CasConsumerUtil.getType(typeSystem, tokenTypeName);

    String nameTypeName = CasConsumerUtil.getRequiredStringParameter(getUimaContext(),
        NameFinder.NAME_TYPE_PARAMETER);
    
    nameType = CasConsumerUtil.getType(typeSystem, nameTypeName);
  }

  /**
   * Creates a {@link List} from an {@link Iterator}.
   * 
   * @param <T>
   * @param it
   * @return
   */
  private static <T> List<T> iteratorToList(Iterator<T> it) {
    List<T> list = new LinkedList<T>();
    
    while (it.hasNext()) {
      list.add(it.next());
    }
    
    return list;
  }

  private static boolean isContaining(AnnotationFS annotation,
      AnnotationFS containtedAnnotation) {
    boolean isStartContaining = annotation.getBegin() <= containtedAnnotation
        .getBegin();
    if (!isStartContaining) {
      return false;
    }

    boolean isEndContaining = annotation.getEnd() >= containtedAnnotation
        .getEnd();
    if (!isEndContaining) {
      return false;
    }

    return true;
  }
  
  /**
   * Creates the name spans out of a list of token annotations and a list of entity annotations.
   * <p>
   * The name spans for the name finder use a token index and not on a character index which
   * is used by the entity annotations.
   * 
   * @param tokenList
   * @param entityAnnotations
   * @return
   */
  private static Span[] createNames(List<AnnotationFS> tokenList, List<AnnotationFS> entityAnnotations) {

    List<Span> nameList = new LinkedList<Span>();

    AnnotationFS currentEntity = null;

    int startIndex = -1;
    int index = 0;
    for (Iterator<AnnotationFS> tokenIterator = tokenList.iterator(); tokenIterator.hasNext();) {
      AnnotationFS token = (AnnotationFS) tokenIterator.next();

      for (Iterator<AnnotationFS> it = entityAnnotations.iterator(); it.hasNext();) {

        AnnotationFS entity = (AnnotationFS) it.next();

        if (!isContaining(entity, token)) {
          // ... end of an entity
          if (currentEntity == entity) {
            nameList.add(new Span(startIndex, index));

            startIndex = -1;
            currentEntity = null;
            // break;
          } else {
            continue;
          }
        }

        // is this token start of new entity
        if (currentEntity == null && isContaining(entity, token)) {
          startIndex = index;

          currentEntity = entity;
        }
      }

      index++;
    }

    if (currentEntity != null) {
      Span name = new Span(startIndex, index);
      nameList.add(name);
    }

    return nameList.toArray(new Span[nameList.size()]);
  }
  
  /**
   * Process the given CAS object.
   */
  public void processCas(CAS cas) {
    FSIndex<AnnotationFS> sentenceIndex = cas.getAnnotationIndex(sentenceType);

    Iterator<AnnotationFS> sentenceIterator = sentenceIndex.iterator();
    while (sentenceIterator.hasNext()) {
      AnnotationFS sentenceAnnotation = sentenceIterator.next();

      ContainingConstraint sentenceContainingConstraint = new ContainingConstraint(
          sentenceAnnotation);
      
      FSIndex<AnnotationFS> tokenAnnotations = cas.getAnnotationIndex(tokenType);
      
      Iterator<AnnotationFS> containingTokens = cas.createFilteredIterator(tokenAnnotations
          .iterator(), sentenceContainingConstraint);
      
      FSIndex<AnnotationFS> allNames = cas.getAnnotationIndex(nameType);
      
      Iterator<AnnotationFS> containingNames = cas.createFilteredIterator(allNames.iterator(),
          sentenceContainingConstraint);
      
      List<AnnotationFS> tokenList = iteratorToList(containingTokens);
      
      Span names[] = createNames(tokenList, iteratorToList(containingNames));
      
      // create token array
      String tokenArray[] = new String[tokenList.size()];
      
      for (int i = 0; i < tokenArray.length; i++) {
        tokenArray[i] = ((AnnotationFS) tokenList.get(i))
            .getCoveredText();
      }
      
      NameSample traingSentence = new NameSample(tokenArray, names, null, false);
      
      if (traingSentence.getSentence().length != 0) {
      	nameFinderSamples.add(traingSentence);
      }
      else {
      	if (logger.isLoggable(Level.INFO)) {
      		logger.log(Level.INFO, "Sentence without tokens: " + 
      				sentenceAnnotation.getCoveredText());
      	}
      }
    }
  }
  
  /**
   * Called if the processing is finished, this method
   * does the training.
   */
  public void collectionProcessComplete(ProcessTrace trace)
      throws ResourceProcessException, IOException {
   
    if (logger.isLoggable(Level.INFO)) {
      logger.log(Level.INFO, "Collected " + nameFinderSamples.size() + 
          " name samples.");
    }
    
    GIS.PRINT_MESSAGES = false;
    
    // create training stream ... 
    ObjectStream<NameSample> samples = ObjectStreamUtils.createObjectStream(nameFinderSamples);
    
    InputStream additionalTrainingDataIn = null;
    TokenNameFinderModel nameModel;
    try {
      if (additionalTrainingDataFile != null) {
        
        if (logger.isLoggable(Level.INFO)) {
          logger.log(Level.INFO, "Using addional training data file: " + additionalTrainingDataFile); 
        }
        
        additionalTrainingDataIn = new FileInputStream(additionalTrainingDataFile);
        
        // TODO: Make encoding configurable, otherwise use UTF-8 as default!
        ObjectStream<NameSample> additionalSamples = new NameSampleDataStream(
            new PlainTextByLineStream(new InputStreamReader(additionalTrainingDataIn, additionalTrainingDataEncoding)));
        
        samples = ObjectStreamUtils.createObjectStream(samples, additionalSamples);
      }
      
      // TODO: Make sure its possible to pass custom feature generator
      // User could subclass this trainer to provide a custom feature generator
      nameModel = NameFinderME.train(language, null,
          samples, Collections.EMPTY_MAP, iterations, cutoff);
      
    }
    finally {
      if (additionalTrainingDataIn != null)
        additionalTrainingDataIn.close();
    }
    
    // dereference to allow garbage collection
    nameFinderSamples = null;

    File modelFile = new File(getUimaContextAdmin().getResourceManager()
        .getDataPath() + File.separatorChar + modelPath);

    OpennlpUtil.serialize(nameModel, modelFile);
    
    if (logger.isLoggable(Level.INFO)) {
      logger.log(Level.INFO, "Model was written to: " + modelFile.getAbsolutePath());
    }
  }
  
  /**
   * The trainer is not stateless.
   */
  public boolean isStateless() {
    return false;
  }
  
  /**
   * Destroys the current instance.
   */
  public void destroy() {
    // dereference to allow garbage collection
    nameFinderSamples = null;
  }
}