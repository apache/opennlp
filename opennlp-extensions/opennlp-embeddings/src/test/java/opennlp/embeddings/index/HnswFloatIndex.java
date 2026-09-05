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
package opennlp.embeddings.index;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;

import org.apache.lucene.document.Document;
import org.apache.lucene.document.KnnFloatVectorField;
import org.apache.lucene.document.StoredField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.StoredFields;
import org.apache.lucene.index.TieredMergePolicy;
import org.apache.lucene.index.VectorSimilarityFunction;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.KnnFloatVectorQuery;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.ByteBuffersDirectory;

/**
 * In-memory Lucene HNSW index used by the evaluation tests. It adapts one graph-searched vector
 * field to the {@link VectorIndex} contract.
 *
 * <p>Vectors are L2-normalized at add time and searched with
 * {@link VectorSimilarityFunction#DOT_PRODUCT}, which is cosine similarity on unit vectors;
 * Lucene's {@code (1 + dot) / 2} scores are mapped back to cosine in the returned hits. A
 * zero vector is indexed unchanged and so scores zero, like the exact index scores it. The
 * graph uses Lucene's default construction parameters (16 neighbors per node, beam width
 * 100).</p>
 *
 * <p>Lucene searches the graph with a beam of {@code k} candidates, so a small {@code k}
 * can reduce recall. Each query gathers at least {@link #DEFAULT_SEARCH_WIDTH} candidates and
 * returns the top {@code k}.</p>
 */
public final class HnswFloatIndex implements VectorIndex, AutoCloseable {

  /** The default number of graph-search candidates gathered per query. */
  public static final int DEFAULT_SEARCH_WIDTH = 100;

  private static final String VECTOR_FIELD = "vector";
  private static final String ID_FIELD = "id";
  private final int dimension;
  private final int searchWidth;
  private VectorBuffer buffer;
  private ByteBuffersDirectory directory;
  private DirectoryReader reader;
  private IndexSearcher searcher;
  private int size;

  /**
   * Creates an empty index with the {@link #DEFAULT_SEARCH_WIDTH}.
   *
   * @param dimension The dimension every vector and query must have. Must be at least 1.
   * @throws IllegalArgumentException Thrown if {@code dimension} is below 1.
   */
  public HnswFloatIndex(int dimension) {
    this(dimension, DEFAULT_SEARCH_WIDTH);
  }

  /**
   * Creates an empty index.
   *
   * @param dimension   The dimension every vector and query must have. Must be at least 1.
   * @param searchWidth The minimum number of graph-search candidates gathered per query; a
   *                    query's {@code k} raises the beam beyond it. Must be at least 1.
   * @throws IllegalArgumentException Thrown if {@code dimension} or {@code searchWidth} is
   *     below 1.
   */
  public HnswFloatIndex(int dimension, int searchWidth) {
    if (searchWidth < 1) {
      throw new IllegalArgumentException("Search width must be at least 1, got " + searchWidth);
    }
    this.buffer = new VectorBuffer(dimension);
    this.dimension = dimension;
    this.searchWidth = searchWidth;
  }

  /** {@inheritDoc} */
  @Override
  public void add(String id, float[] vector) {
    if (buffer == null) {
      throw new IllegalStateException("The index is frozen; vectors can no longer be added");
    }
    buffer.add(id, vector);
  }

  /**
   * {@inheritDoc} Builds the Lucene index and its HNSW graph, merged to a single segment so
   * every query searches one graph.
   */
  @Override
  public void freeze() {
    if (buffer == null) {
      return;
    }
    final List<String> ids = buffer.ids();
    final float[] rowMajor = buffer.rowMajor();
    size = ids.size();
    try {
      directory = new ByteBuffersDirectory();
      final IndexWriterConfig config = new IndexWriterConfig();
      // Plain per-extension files expose the serialized vector and graph sizes.
      config.setUseCompoundFile(false);
      final TieredMergePolicy mergePolicy = new TieredMergePolicy();
      mergePolicy.setNoCFSRatio(0);
      config.setMergePolicy(mergePolicy);
      try (IndexWriter writer = new IndexWriter(directory, config)) {
        for (int row = 0; row < size; row++) {
          final float[] unit = new float[dimension];
          System.arraycopy(rowMajor, row * dimension, unit, 0, dimension);
          normalize(unit);
          final Document document = new Document();
          document.add(new KnnFloatVectorField(VECTOR_FIELD, unit,
              VectorSimilarityFunction.DOT_PRODUCT));
          document.add(new StoredField(ID_FIELD, ids.get(row)));
          writer.addDocument(document);
        }
        writer.forceMerge(1);
      }
      reader = DirectoryReader.open(directory);
      searcher = new IndexSearcher(reader);
    } catch (IOException e) {
      try {
        close();
      } catch (UncheckedIOException closeFailure) {
        e.addSuppressed(closeFailure);
      }
      throw new UncheckedIOException(e);
    }
    buffer = null;
  }

  /** {@inheritDoc} */
  @Override
  public List<Hit> topK(float[] query, int k) {
    if (buffer != null) {
      throw new IllegalStateException("The index is not frozen; freeze() ends the build phase");
    }
    final double queryNorm = IndexQueries.checkedQueryNorm(query, k, dimension);
    if (queryNorm == 0 || size == 0) {
      return List.of();
    }
    final float[] unit = query.clone();
    normalize(unit);
    try {
      final TopDocs top = searcher.search(
          new KnnFloatVectorQuery(VECTOR_FIELD, unit, Math.max(k, searchWidth)), k);
      final StoredFields storedFields = searcher.storedFields();
      final List<Hit> hits = new ArrayList<>(top.scoreDocs.length);
      for (final ScoreDoc scoreDoc : top.scoreDocs) {
        hits.add(new Hit(storedFields.document(scoreDoc.doc).get(ID_FIELD),
            2.0 * scoreDoc.score - 1.0));
      }
      return hits;
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  /** {@inheritDoc} */
  @Override
  public int size() {
    return buffer != null ? buffer.size() : size;
  }

  /** {@inheritDoc} */
  @Override
  public int dimension() {
    return dimension;
  }

  /**
   * {@return the serialized bytes per indexed vector: the vector data, vector metadata, and
   * graph files divided by the vector count, or zero when the index is empty}
   *
   * @throws IllegalStateException Thrown if the index is not frozen.
   */
  public double serializedBytesPerVector() {
    if (buffer != null) {
      throw new IllegalStateException("The index is not frozen; freeze() ends the build phase");
    }
    if (size == 0) {
      return 0;
    }
    try {
      long bytes = 0;
      for (final String name : directory.listAll()) {
        if (name.endsWith(".vec") || name.endsWith(".vem") || name.endsWith(".vex")) {
          bytes += directory.fileLength(name);
        }
      }
      return bytes / (double) size;
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  /** Releases the Lucene reader and directory. Calling this more than once has no effect. */
  @Override
  public void close() {
    try {
      if (reader != null) {
        reader.close();
        reader = null;
      }
      if (directory != null) {
        directory.close();
        directory = null;
      }
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  /**
   * Scales a vector to unit length in place; a vector without direction is left unchanged.
   *
   * @param vector The vector.
   */
  private void normalize(float[] vector) {
    double sumOfSquares = 0;
    for (final float value : vector) {
      sumOfSquares += (double) value * value;
    }
    final double norm = Math.sqrt(sumOfSquares);
    if (norm == 0) {
      return;
    }
    for (int d = 0; d < vector.length; d++) {
      vector[d] = (float) (vector[d] / norm);
    }
  }
}
