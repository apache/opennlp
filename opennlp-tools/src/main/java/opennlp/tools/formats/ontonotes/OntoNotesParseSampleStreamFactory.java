package opennlp.tools.formats.ontonotes;

import java.io.File;
import java.io.FileFilter;
import java.nio.charset.Charset;

import opennlp.tools.cmdline.ArgumentParser;
import opennlp.tools.cmdline.StreamFactoryRegistry;
import opennlp.tools.formats.AbstractSampleStreamFactory;
import opennlp.tools.formats.DirectorySampleStream;
import opennlp.tools.formats.convert.FileToStringSampleStream;
import opennlp.tools.parser.Parse;
import opennlp.tools.util.ObjectStream;

public class OntoNotesParseSampleStreamFactory extends AbstractSampleStreamFactory<Parse> {


  protected OntoNotesParseSampleStreamFactory() {
    super(OntoNotesFormatParameters.class);
  }

  public ObjectStream<Parse> create(String[] args) {

    OntoNotesFormatParameters params = ArgumentParser.parse(args, OntoNotesFormatParameters.class);

    ObjectStream<File> documentStream = new DirectorySampleStream(new File(
        params.getOntoNotesDir()), new FileFilter() {

      public boolean accept(File file) {
        if (file.isFile()) {
          return file.getName().endsWith(".parse");
        }

        return file.isDirectory();
      }
    }, true);

    // We need file to line here ... and that is probably best doen with the plain text stream
    // lets copy it over here, refactor it, and then at some point we replace the current version
    // with the refactored version

    return new OntoNotesParseSampleStream(new DocumentToLineStream(new FileToStringSampleStream(
        documentStream, Charset.forName("UTF-8"))));
  }

  public static void registerFactory() {
    StreamFactoryRegistry.registerFactory(Parse.class, "ontonotes",
        new OntoNotesParseSampleStreamFactory());
  }
}
