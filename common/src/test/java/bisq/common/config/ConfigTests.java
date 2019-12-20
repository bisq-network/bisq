package bisq.common.config;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.io.PrintWriter;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import static bisq.common.config.Config.DEFAULT_CONFIG_FILE_NAME;
import static java.lang.String.format;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.isEmptyString;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class ConfigTests {

    @Rule
    public ExpectedException exceptionRule = ExpectedException.none();

    // Note: "DataDirProperties" in the test method names below represent the group of
    // configuration options that influence the location of a Bisq node's data directory.
    // These options include appName, userDataDir, appDataDir, and configFile

    @Test
    public void whenTestConfigNoArgCtorIsCalled_thenDefaultAppNameIsSetToRandomValue() {
        Config config = new TestConfig();
        String defaultAppName = config.getDefaultAppName();
        String regex = "Bisq\\d{2,}Test";
        assertTrue(format("Test app name '%s' failed to match '%s'", defaultAppName, regex),
                defaultAppName.matches(regex));
    }

    @Test
    public void whenStringConstructorIsCalled_thenDefaultAppNamePropertyIsAssignedToItsValue() {
        Config config = new Config("Custom-Bisq");
        assertThat(config.getDefaultAppName(), equalTo("Custom-Bisq"));
    }

    @Test
    public void whenAppNameOptionIsSet_thenAppNamePropertyDiffersFromDefaultAppNameProperty() {
        Config config = new TestConfig("--appName=My-Bisq");
        assertThat(config.getAppName(), equalTo("My-Bisq"));
        assertThat(config.getAppName(), not(equalTo(config.getDefaultAppName())));
    }

    @Test
    public void whenNoOptionsAreSet_thenDataDirPropertiesEqualDefaultValues() {
        Config config = new TestConfig();
        assertThat(config.getAppName(), equalTo(config.getDefaultAppName()));
        assertThat(config.getUserDataDir(), equalTo(config.getDefaultUserDataDir()));
        assertThat(config.getAppDataDir(), equalTo(config.getDefaultAppDataDir()));
        assertThat(config.getConfigFile(), equalTo(config.getDefaultConfigFile()));
    }

    @Test
    public void whenAppNameOptionIsSet_thenDataDirPropertiesReflectItsValue() {
        Config config = new TestConfig("--appName=My-Bisq");
        assertThat(config.getAppName(), equalTo("My-Bisq"));
        assertThat(config.getUserDataDir(), equalTo(config.getDefaultUserDataDir()));
        assertThat(config.getAppDataDir(), equalTo(new File(config.getUserDataDir(), "My-Bisq")));
        assertThat(config.getConfigFile(), equalTo(new File(config.getAppDataDir(), DEFAULT_CONFIG_FILE_NAME)));
    }

    @Test
    public void whenAppDataDirOptionIsSet_thenDataDirPropertiesReflectItsValue() {
        Config config = new TestConfig("--appDataDir=/mydata/myapp");
        assertThat(config.getAppName(), equalTo(config.getDefaultAppName()));
        assertThat(config.getUserDataDir(), equalTo(config.getDefaultUserDataDir()));
        assertThat(config.getAppDataDir(), equalTo(new File("/mydata/myapp")));
        assertThat(config.getConfigFile(), equalTo(new File(config.getAppDataDir(), DEFAULT_CONFIG_FILE_NAME)));
    }

    @Test
    public void whenUserDataDirOptionIsSet_thenDataDirPropertiesReflectItsValue() {
        Config config = new TestConfig("--userDataDir=/mydata");
        assertThat(config.getAppName(), equalTo(config.getDefaultAppName()));
        assertThat(config.getUserDataDir(), equalTo(new File("/mydata")));
        assertThat(config.getAppDataDir(), equalTo(new File("/mydata", config.getDefaultAppName())));
        assertThat(config.getConfigFile(), equalTo(new File(config.getAppDataDir(), DEFAULT_CONFIG_FILE_NAME)));
    }

    @Test
    public void whenAppNameAndAppDataDirOptionsAreSet_thenDataDirPropertiesReflectTheirValues() {
        Config config = new TestConfig("--appName=My-Bisq", "--appDataDir=/mydata/myapp");
        assertThat(config.getAppName(), equalTo("My-Bisq"));
        assertThat(config.getUserDataDir(), equalTo(config.getDefaultUserDataDir()));
        assertThat(config.getAppDataDir(), equalTo(new File("/mydata/myapp")));
        assertThat(config.getConfigFile(), equalTo(new File(config.getAppDataDir(), DEFAULT_CONFIG_FILE_NAME)));
    }

    @Test
    public void whenConfFileOptionIsSetToNonExistentFile_thenConfFilePropertyFallsBackToDefaultValue() {
        Config config = new TestConfig("--configFile=/tmp/bogus.properties");
        assertThat(config.getConfigFile(), equalTo(new File(config.getAppDataDir(), DEFAULT_CONFIG_FILE_NAME)));
    }

    @Test
    public void whenOptionIsSetAtCommandLineAndInConfigFile_thenCommandLineValueTakesPrecedence() throws IOException {
        File configFile = File.createTempFile("bisq", "properties");
        try (PrintWriter writer = new PrintWriter(configFile)) {
            writer.println("appName=Bisq-configFileValue");
        }
        Config config = new TestConfig("--appName=Bisq-commandLineValue");
        assertThat(config.getAppName(), equalTo("Bisq-commandLineValue"));
    }

    @Test
    public void whenUnrecognizedOptionIsSet_thenThrowConfigException() {
        exceptionRule.expect(ConfigException.class);
        exceptionRule.expectMessage("problem parsing option 'bogus': bogus is not a recognized option");
        new TestConfig("--bogus");
    }

    @Test
    public void whenOptionFileArgumentDoesNotExist_thenThrowConfigException() {
        exceptionRule.expect(ConfigException.class);
        exceptionRule.expectMessage("problem parsing option 'torrcFile': File [/does/not/exist] does not exist");
        new TestConfig("--torrcFile=/does/not/exist");
    }

    @Test
    public void whenConfigFileOptionIsSetInConfigFile_thenDisallowedOptionExceptionisThrown() throws IOException {
        File configFile = File.createTempFile("bisq", "properties");
        try (PrintWriter writer = new PrintWriter(configFile)) {
            writer.println("configFile=/tmp/other.bisq.properties");
        }
        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage("The 'configFile' option is disallowed in config files");
        new TestConfig("--configFile=" + configFile.getAbsolutePath());
    }

    @Test
    public void whenConfigFileOptionIsSetToExistingFile_thenConfigFilePropertyReflectsItsValue() throws IOException {
        File configFile = File.createTempFile("bisq", "properties");
        Config config = new TestConfig("--configFile=" + configFile.getAbsolutePath());
        assertThat(config.getConfigFile(), equalTo(configFile));
    }

    @Test
    public void whenAppNameIsSetInConfigFile_thenDataDirPropertiesReflectItsValue() throws IOException {
        File configFile = File.createTempFile("bisq", "properties");
        try (PrintWriter writer = new PrintWriter(configFile)) {
            writer.println("appName=My-Bisq");
        }
        Config config = new TestConfig("--configFile=" + configFile.getAbsolutePath());
        assertThat(config.getAppName(), equalTo("My-Bisq"));
        assertThat(config.getUserDataDir(), equalTo(config.getDefaultUserDataDir()));
        assertThat(config.getAppDataDir(), equalTo(new File(config.getUserDataDir(), config.getAppName())));
        assertThat(config.getConfigFile(), equalTo(configFile));
    }

    @Test
    public void whenBannedBtcNodesOptionIsSet_thenBannedBtcNodesPropertyReturnsItsValue() {
        Config config = new TestConfig("--bannedBtcNodes=foo.onion:8333,bar.onion:8333");
        assertThat(config.getBannedBtcNodes(), contains("foo.onion:8333", "bar.onion:8333"));
    }

    @Test
    public void whenHelpOptionIsSet_thenHelpRequestedIsThrown() {
        new TestConfig("--help");
        fail();
    }

    @Test
    public void whenConfigIsConstructed_thenNoConsoleOutputSideEffectsShouldOccur() {
        PrintStream outOrig = System.out;
        PrintStream errOrig = System.err;
        ByteArrayOutputStream outBytes = new ByteArrayOutputStream();
        ByteArrayOutputStream errBytes = new ByteArrayOutputStream();
        try (PrintStream outTest = new PrintStream(outBytes);
             PrintStream errTest = new PrintStream(errBytes)) {
            System.setOut(outTest);
            System.setErr(errTest);
            new Config();
            assertThat(outBytes.toString(), isEmptyString());
            assertThat(errBytes.toString(), isEmptyString());
        } finally {
            System.setOut(outOrig);
            System.setErr(errOrig);
        }
    }
}
