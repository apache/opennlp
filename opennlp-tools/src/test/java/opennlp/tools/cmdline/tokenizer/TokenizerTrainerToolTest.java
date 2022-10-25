package opennlp.tools.cmdline.tokenizer;

import junit.framework.TestCase;
import opennlp.tools.cmdline.StreamFactoryRegistry;
import opennlp.tools.cmdline.TerminateToolException;
import opennlp.tools.dictionary.Dictionary;
import opennlp.tools.util.InsufficientTrainingDataException;
import org.apache.commons.io.FileUtils;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.*;
import java.nio.charset.StandardCharsets;

public class TokenizerTrainerToolTest extends TestCase {

    private TokenizerTrainerTool tokenizerTrainerTool;

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    private String sampleSuccessData = "Pierre Vinken<SPLIT>, 61 years old<SPLIT>, will join the board as a nonexecutive " +
            "director Nov. 29<SPLIT>.\n" +
            "Mr. Vinken is chairman of Elsevier N.V.<SPLIT>, the Dutch publishing group<SPLIT>.\n" +
            "Rudolph Agnew<SPLIT>, 55 years old and former chairman of Consolidated Gold Fields PLC<SPLIT>,\n" +
            "    was named a nonexecutive director of this British industrial conglomerate<SPLIT>.\n" ;

    private String sampleFailureData = "It is Fail Test Case.\n\nNothing in this sentence.";

    public void setUp() throws Exception {
        super.setUp();
    }

    public void tearDown() throws Exception {
    }

    public void testGetShortDescription() {
        tokenizerTrainerTool = new TokenizerTrainerTool();
        assertEquals(tokenizerTrainerTool.getShortDescription(),"trainer for the learnable tokenizer");
    }

    public void testLoadDictHappyCase() throws IOException {
        Dictionary dict = TokenizerTrainerTool.loadDict(prepareDataFile("opennlp/tools/sentdetect/abb.xml"));
        assertNotNull(dict);
    }

    public void testLoadDictFailCase() {
    }

    @Test()
    public void testTestRunHappyCase() throws IOException {
        tempFolder.create();
        File model = tempFolder.newFile("model-en.bin");

        String[] args = new String[]{"-model",model.getAbsolutePath(),"-alphaNumOpt","false", "-lang","en",
                "-data", String.valueOf(prepareDataFile(sampleSuccessData)),"-encoding","UTF-8"};

        InputStream stream = new ByteArrayInputStream(sampleSuccessData.getBytes(StandardCharsets.UTF_8));
        System.setIn(stream);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PrintStream ps = new PrintStream(baos);
        System.setOut(ps);

        tokenizerTrainerTool = new TokenizerTrainerTool();
        tokenizerTrainerTool.run(StreamFactoryRegistry.DEFAULT_FORMAT,args);

        final String content = new String(baos.toByteArray(), StandardCharsets.UTF_8);
        Assert.assertTrue(content.contains("Number of Event Tokens: 171"));
        model.delete();
    }

    @Test(expected = TerminateToolException.class)
    public void testTestRunExceptionCase() throws IOException {
        tempFolder.create();
        File model = tempFolder.newFile("model-en.bin");

        String[] args = new String[]{"-model",model.getAbsolutePath(),"-alphaNumOpt","false", "-lang","en",
                "-data", String.valueOf(prepareDataFile(sampleFailureData)),"-encoding","UTF-8"};

        InputStream stream = new ByteArrayInputStream(sampleFailureData.getBytes(StandardCharsets.UTF_8));
        System.setIn(stream);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PrintStream ps = new PrintStream(baos);
        System.setOut(ps);

        tokenizerTrainerTool = new TokenizerTrainerTool();
        tokenizerTrainerTool.run(StreamFactoryRegistry.DEFAULT_FORMAT,args);
        model.delete();
    }

    private File prepareDataFile(String input) throws IOException {
        tempFolder.create();
        // This is guaranteed to be deleted after the test finishes.
        File dataFile = tempFolder.newFile("data-en.train");
        FileUtils.writeStringToFile(dataFile, input, "ISO-8859-1");
        return dataFile;
    }
}