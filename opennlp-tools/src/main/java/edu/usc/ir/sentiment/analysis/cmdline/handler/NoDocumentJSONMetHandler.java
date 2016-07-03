package edu.usc.ir.sentiment.analysis.cmdline.handler;

import java.io.PrintWriter;
import java.util.Arrays;

import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.serialization.JsonMetadataDeserializer;
import org.apache.tika.metadata.serialization.JsonMetadataSerializer;
import org.apache.tika.metadata.serialization.PrettyMetadataKeyComparator;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

public class NoDocumentJSONMetHandler extends DefaultHandler {

  protected final Metadata metadata;

  protected PrintWriter writer;

  protected boolean prettyPrint = true;

  private boolean metOutput;

  private static class SortedJsonMetadataSerializer
      extends JsonMetadataSerializer {
    @Override
    public String[] getNames(Metadata m) {
      String[] names = m.names();
      Arrays.sort(names, new PrettyMetadataKeyComparator());
      return names;
    }
  }

  public NoDocumentJSONMetHandler(Metadata metadata, PrintWriter writer) {
    this.metadata = metadata;
    this.writer = writer;
    this.metOutput = false;
  }

  @Override
  public void endDocument() throws SAXException {
    // try {
    // JsonMetadata.setPrettyPrinting(prettyPrint);
    // JsonMetadata.toJson(metadata, writer);
    // writer.flush();
    // } catch (TikaException e) {
    // throw new SAXException(e);
    // }
    GsonBuilder builder = new GsonBuilder();
    builder.registerTypeHierarchyAdapter(Metadata.class,
        new SortedJsonMetadataSerializer());
    builder.registerTypeHierarchyAdapter(Metadata.class,
        new JsonMetadataDeserializer());
    builder.setPrettyPrinting();
    Gson gson = builder.create();
    gson.toJson(metadata, writer);
    this.metOutput = true;
  }

  /**
   * Checks the output
   *
   * @return true or false
   */
  public boolean metOutput() {
    return this.metOutput;
  }

}
