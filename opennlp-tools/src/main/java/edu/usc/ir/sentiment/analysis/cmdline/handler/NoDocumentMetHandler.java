package edu.usc.ir.sentiment.analysis.cmdline.handler;

import java.io.PrintWriter;
import java.util.Arrays;

import org.apache.tika.metadata.Metadata;
import org.xml.sax.helpers.DefaultHandler;

public class NoDocumentMetHandler extends DefaultHandler {

  protected final Metadata metadata;

  protected PrintWriter writer;

  private boolean metOutput;

  public NoDocumentMetHandler(Metadata metadata, PrintWriter writer) {
    this.metadata = metadata;
    this.writer = writer;
    this.metOutput = false;
  }

  /**
   * Ends the document given
   */
  @Override
  public void endDocument() {
    String[] names = metadata.names();
    Arrays.sort(names);
    outputMetadata(names);
    writer.flush();
    this.metOutput = true;
  }

  /**
   * Outputs the metadata
   *
   * @param names
   *          the names provided
   */
  public void outputMetadata(String[] names) {
    for (String name : names) {
      for (String value : metadata.getValues(name)) {
        writer.println(name + ": " + value);
      }
    }
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

