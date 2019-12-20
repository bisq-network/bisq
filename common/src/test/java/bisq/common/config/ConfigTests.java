package bisq.common.config;

import java.nio.file.Files;

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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

public class ConfigTests {

    @Rule
    public ExpectedException exceptionRule = ExpectedException.none();

    // Note: "DataDirProperties" in the test method names below represent the group of
    // configuration options that influence the location of a Bisq node's data directory.
    // These options include appName, userDataDir, appDataDir, and configFile

    @Test
    public void whenNoArgCtorIsCalled_thenDefaultAppNameIsSetToTempValue() {
        Config config = new Config();
        String defaultAppName = config.getDefaultAppName();
        String regex = "Bisq\\d{2,}Temp";
        assertTrue(format("Temp app name '%s' failed to match '%s'", defaultAppName, regex),
                defaultAppName.matches(regex));
    }

    @Test
    public void whenAppNameOptionIsSet_thenAppNamePropertyDiffersFromDefaultAppNameProperty() {
        Config config = new Config("--appName=My-Bisq");
        assertThat(config.getAppName(), equalTo("My-Bisq"));
        assertThat(config.getAppName(), not(equalTo(config.getDefaultAppName())));
    }

    @Test
    public void whenNoOptionsAreSet_thenDataDirPropertiesEqualDefaultValues() {
        Config config = new Config();
        assertThat(config.getAppName(), equalTo(config.getDefaultAppName()));
        assertThat(config.getUserDataDir(), equalTo(config.getDefaultUserDataDir()));
        assertThat(config.getAppDataDir(), equalTo(config.getDefaultAppDataDir()));
        assertThat(config.getConfigFile(), equalTo(config.getDefaultConfigFile()));
    }

    @Test
    public void whenAppNameOptionIsSet_thenDataDirPropertiesReflectItsValue() {
        Config config = new Config("--appName=My-Bisq");
        assertThat(config.getAppName(), equalTo("My-Bisq"));
        assertThat(config.getUserDataDir(), equalTo(config.getDefaultUserDataDir()));
        assertThat(config.getAppDataDir(), equalTo(new File(config.getUserDataDir(), "My-Bisq")));
        assertThat(config.getConfigFile(), equalTo(new File(config.getAppDataDir(), DEFAULT_CONFIG_FILE_NAME)));
    }

    @Test
    public void whenAppDataDirOptionIsSet_thenDataDirPropertiesReflectItsValue() throws IOException {
        File appDataDir = Files.createTempDirectory("myapp").toFile();
        Config config = new Config("--appDataDir=" + appDataDir);
        assertThat(config.getAppName(), equalTo(config.getDefaultAppName()));
        assertThat(config.getUserDataDir(), equalTo(config.getDefaultUserDataDir()));
        assertThat(config.getAppDataDir(), equalTo(appDataDir));
        assertThat(config.getConfigFile(), equalTo(new File(config.getAppDataDir(), DEFAULT_CONFIG_FILE_NAME)));
    }

    @Test
    public void whenUserDataDirOptionIsSet_thenDataDirPropertiesReflectItsValue() throws IOException {
        File userDataDir = Files.createTempDirectory("myuserdata").toFile();
        Config config = new Config("--userDataDir=" + userDataDir);
        assertThat(config.getAppName(), equalTo(config.getDefaultAppName()));
        assertThat(config.getUserDataDir(), equalTo(userDataDir));
        assertThat(config.getAppDataDir(), equalTo(new File(userDataDir, config.getDefaultAppName())));
        assertThat(config.getConfigFile(), equalTo(new File(config.getAppDataDir(), DEFAULT_CONFIG_FILE_NAME)));
    }

    @Test
    public void whenAppNameAndAppDataDirOptionsAreSet_thenDataDirPropertiesReflectTheirValues() throws IOException {
        File appDataDir = Files.createTempDirectory("myapp").toFile();
        Config config = new Config("--appName=My-Bisq", "--appDataDir=" + appDataDir);
        assertThat(config.getAppName(), equalTo("My-Bisq"));
        assertThat(config.getUserDataDir(), equalTo(config.getDefaultUserDataDir()));
        assertThat(config.getAppDataDir(), equalTo(appDataDir));
        assertThat(config.getConfigFile(), equalTo(new File(config.getAppDataDir(), DEFAULT_CONFIG_FILE_NAME)));
    }

    @Test
    public void whenConfFileOptionIsSetToNonExistentFile_thenConfFilePropertyFallsBackToDefaultValue() {
        Config config = new Config("--configFile=/tmp/bogus.properties");
        assertThat(config.getConfigFile(), equalTo(new File(config.getAppDataDir(), DEFAULT_CONFIG_FILE_NAME)));
    }

    @Test
    public void whenOptionIsSetAtCommandLineAndInConfigFile_thenCommandLineValueTakesPrecedence() throws IOException {
        File configFile = File.createTempFile("bisq", "properties");
        try (PrintWriter writer = new PrintWriter(configFile)) {
            writer.println("appName=Bisq-configFileValue");
        }
        Config config = new Config("--appName=Bisq-commandLineValue");
        assertThat(config.getAppName(), equalTo("Bisq-commandLineValue"));
    }

    @Test
    public void whenUnrecognizedOptionIsSet_thenConfigExceptionIsThrown() {
        exceptionRule.expect(ConfigException.class);
        exceptionRule.expectMessage("problem parsing option 'bogus': bogus is not a recognized option");
        new Config("--bogus");
    }

    @Test
    public void whenOptionFileArgumentDoesNotExist_thenConfigExceptionIsThrown() {
        exceptionRule.expect(ConfigException.class);
        exceptionRule.expectMessage("problem parsing option 'torrcFile': File [/does/not/exist] does not exist");
        new Config("--torrcFile=/does/not/exist");
    }

    @Test
    public void whenConfigFileOptionIsSetInConfigFile_thenConfigExceptionIsThrown() throws IOException {
        File configFile = File.createTempFile("bisq", "properties");
        try (PrintWriter writer = new PrintWriter(configFile)) {
            writer.println("configFile=/tmp/other.bisq.properties");
        }
        exceptionRule.expect(ConfigException.class);
        exceptionRule.expectMessage("The 'configFile' option is disallowed in config files");
        new Config("--configFile=" + configFile.getAbsolutePath());
    }

    @Test
    public void whenConfigFileOptionIsSetToExistingFile_thenConfigFilePropertyReflectsItsValue() throws IOException {
        File configFile = File.createTempFile("bisq", "properties");
        Config config = new Config("--configFile=" + configFile.getAbsolutePath());
        assertThat(config.getConfigFile(), equalTo(configFile));
    }

    @Test
    public void whenAppNameIsSetInConfigFile_thenDataDirPropertiesReflectItsValue() throws IOException {
        File configFile = File.createTempFile("bisq", "properties");
        try (PrintWriter writer = new PrintWriter(configFile)) {
            writer.println("appName=My-Bisq");
        }
        Config config = new Config("--configFile=" + configFile.getAbsolutePath());
        assertThat(config.getAppName(), equalTo("My-Bisq"));
        assertThat(config.getUserDataDir(), equalTo(config.getDefaultUserDataDir()));
        assertThat(config.getAppDataDir(), equalTo(new File(config.getUserDataDir(), config.getAppName())));
        assertThat(config.getConfigFile(), equalTo(configFile));
    }

    @Test
    public void whenBannedBtcNodesOptionIsSet_thenBannedBtcNodesPropertyReturnsItsValue() {
        Config config = new Config("--bannedBtcNodes=foo.onion:8333,bar.onion:8333");
        assertThat(config.getBannedBtcNodes(), contains("foo.onion:8333", "bar.onion:8333"));
    }

    @Test
    public void whenHelpOptionIsSet_thenIsHelpRequestedIsTrue() {
        assertFalse(new Config().isHelpRequested());
        assertTrue(new Config("--help").isHelpRequested());
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
