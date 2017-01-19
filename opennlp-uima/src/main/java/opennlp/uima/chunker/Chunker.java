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

package opennlp.uima.chunker;

import org.apache.uima.UimaContext;
import org.apache.uima.analysis_component.CasAnnotator_ImplBase;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.cas.CAS;
import org.apache.uima.cas.FSIndex;
import org.apache.uima.cas.Feature;
import org.apache.uima.cas.Type;
import org.apache.uima.cas.TypeSystem;
import org.apache.uima.cas.text.AnnotationFS;
import org.apache.uima.resource.ResourceAccessException;
import org.apache.uima.resource.ResourceInitializationException;
import org.apache.uima.util.Level;
import org.apache.uima.util.Logger;

import opennlp.tools.chunker.ChunkerME;
import opennlp.tools.chunker.ChunkerModel;
import opennlp.uima.util.AnnotatorUtil;
import opennlp.uima.util.UimaUtil;

/**
 * OpenNLP Chunker annotator.
 * <p>
 * Mandatory parameters
 * <table border=1>
 * <caption></caption>
 * <tr><th>Type</th> <th>Name</th> <th>Description</th></tr>
 * <tr><td>String</td> <td>opennlp.uima.ModelName</td> <td>The name of the model file</td></tr>
 * <tr><td>String</td> <td>opennlp.uima.SentenceType</td> <td>The full name of the sentence type</td></tr>
 * <tr><td>String</td> <td>opennlp.uima.TokenType</td> <td>The full name of the token type</td></tr>
 * <tr><td>String</td> <td>opennlp.uima.POSFeature</td></tr>
 * <tr><td>String</td> <td>opennlp.uima.ChunkType</td></tr>
 * <tr><td>String</td> <td>opennlp.uima.ChunkTagFeature</td></tr>
 * </table>
 * <p>
 * Optional parameters
 * <table border=1>
 * <caption></caption>
 * <tr><th>Type</th> <th>Name</th> <th>Description</th></tr>
 * <tr><td>Integer</td> <td>opennlp.uima.BeamSize</td></tr>
 * </table>
 */
public final class Chunker extends CasAnnotator_ImplBase {

  /**
   * The chunk type parameter.
   */
  public static final String CHUNK_TYPE_PARAMETER = "opennlp.uima.ChunkType";

  /**
   * The chunk tag feature parameter
   */
  public static final String CHUNK_TAG_FEATURE_PARAMETER =
      "opennlp.uima.ChunkTagFeature";

  private Type mTokenType;

  private Type mChunkType;

  private Feature mPosFeature;

  private ChunkerME mChunker;

  private UimaContext context;

  private Logger mLogger;

  private Feature mChunkFeature;

  /**
   * Initializes a new instance.
   * <p>
   * Note: Use {@link #initialize(UimaContext) } to initialize
   * this instance. Not use the constructor.
   */
  public Chunker() {
    // must not be implemented !
  }

  /**
   * Initializes the current instance with the given context.
   * <p>
   * Note: Do all initialization in this method, do not use the constructor.
   */
  public void initialize(UimaContext context)
      throws ResourceInitializationException {

    super.initialize(context);

    this.context = context;

    mLogger = context.getLogger();

    if (mLogger.isLoggable(Level.INFO)) {
      mLogger.log(Level.INFO, "Initializing the OpenNLP Chunker annotator.");
    }

    ChunkerModel model;

    try {
      ChunkerModelResource modelResource =
          (ChunkerModelResource) context.getResourceObject(UimaUtil.MODEL_PARAMETER);

      model = modelResource.getModel();
    } catch (ResourceAccessException e) {
      throw new ResourceInitializationException(e);
    }

    mChunker = new ChunkerME(model);
  }

  /**
   * Initializes the type system.
   */
  public void typeSystemInit(TypeSystem typeSystem)
      throws AnalysisEngineProcessException {

    // chunk type
    mChunkType = AnnotatorUtil.getRequiredTypeParameter(context, typeSystem,
        CHUNK_TYPE_PARAMETER);

    // chunk feature
    mChunkFeature = AnnotatorUtil.getRequiredFeatureParameter(context, mChunkType,
        CHUNK_TAG_FEATURE_PARAMETER, CAS.TYPE_NAME_STRING);

    // token type
    mTokenType = AnnotatorUtil.getRequiredTypeParameter(context, typeSystem,
        UimaUtil.TOKEN_TYPE_PARAMETER);

    // pos feature
    mPosFeature = AnnotatorUtil.getRequiredFeatureParameter(
        context, mTokenType, UimaUtil.POS_FEATURE_PARAMETER, CAS.TYPE_NAME_STRING);
  }

  private void addChunkAnnotation(CAS tcas, AnnotationFS tokenAnnotations[],
                                  String tag, int start, int end) {
    AnnotationFS chunk = tcas.createAnnotation(mChunkType,
        tokenAnnotations[start].getBegin(), tokenAnnotations[end - 1].getEnd());

    chunk.setStringValue(mChunkFeature, tag);

    tcas.getIndexRepository().addFS(chunk);
  }

  /**
   * Performs chunking on the given tcas object.
   */
  public void process(CAS tcas) {

    FSIndex<AnnotationFS> tokenAnnotationIndex = tcas.getAnnotationIndex(mTokenType);

    String tokens[] = new String[tokenAnnotationIndex.size()];
    String pos[] = new String[tokenAnnotationIndex.size()];
    AnnotationFS tokenAnnotations[] = new AnnotationFS[tokenAnnotationIndex
        .size()];

    int index = 0;

    for (AnnotationFS tokenAnnotation : tokenAnnotationIndex) {

      tokenAnnotations[index] = tokenAnnotation;

      tokens[index] = tokenAnnotation.getCoveredText();

      pos[index++] = tokenAnnotation.getFeatureValueAsString(
          mPosFeature);
    }

    String result[] = mChunker.chunk(tokens, pos);

    int start = -1;
    int end = -1;
    for (int i = 0; i < result.length; i++) {

      String chunkTag = result[i];

      if (chunkTag.startsWith("B")) {
        if (start != -1) {
          addChunkAnnotation(tcas, tokenAnnotations, result[i - 1].substring(2),
              start, end);
        }

        start = i;
        end = i + 1;
      } else if (chunkTag.startsWith("I")) {
        end = i + 1;
      } else if (chunkTag.startsWith("O")) {
        if (start != -1) {

          addChunkAnnotation(tcas, tokenAnnotations, result[i - 1].substring(2), start, end);

          start = -1;
          end = -1;
        }
      } else {
        System.out.println("Unexpected tag: " + result[i]);
      }
    }

    if (start != -1) {
      addChunkAnnotation(tcas, tokenAnnotations, result[result.length - 1].substring(2), start, end);
    }
  }

  /**
   * Releases allocated resources.
   */
  public void destroy() {
    // dereference model to allow garbage collection
    mChunker = null;
  }
}
