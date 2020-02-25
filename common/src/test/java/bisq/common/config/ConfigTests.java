package bisq.common.config;

import java.nio.file.Files;
import java.nio.file.Path;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.UncheckedIOException;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import static bisq.common.config.Config.*;
import static java.lang.String.format;
import static org.hamcrest.CoreMatchers.containsString;
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
    // These options include appName, userDataDir, appDataDir and configFile

    @Test
    public void whenNoArgCtorIsCalled_thenDefaultAppNameIsSetToTempValue() {
        Config config = new Config();
        String defaultAppName = config.defaultAppName;
        String regex = "Bisq\\d{2,}Temp";
        assertTrue(format("Temp app name '%s' failed to match '%s'", defaultAppName, regex),
                defaultAppName.matches(regex));
    }

    @Test
    public void whenAppNameOptionIsSet_thenAppNamePropertyDiffersFromDefaultAppNameProperty() {
        Config config = configWithOpts(opt(APP_NAME, "My-Bisq"));
        assertThat(config.appName, equalTo("My-Bisq"));
        assertThat(config.appName, not(equalTo(config.defaultAppName)));
    }

    @Test
    public void whenNoOptionsAreSet_thenDataDirPropertiesEqualDefaultValues() {
        Config config = new Config();
        assertThat(config.appName, equalTo(config.defaultAppName));
        assertThat(config.userDataDir, equalTo(config.defaultUserDataDir));
        assertThat(config.appDataDir, equalTo(config.defaultAppDataDir));
        assertThat(config.configFile, equalTo(config.defaultConfigFile));
    }

    @Test
    public void whenAppNameOptionIsSet_thenDataDirPropertiesReflectItsValue() {
        Config config = configWithOpts(opt(APP_NAME, "My-Bisq"));
        assertThat(config.appName, equalTo("My-Bisq"));
        assertThat(config.userDataDir, equalTo(config.defaultUserDataDir));
        assertThat(config.appDataDir, equalTo(new File(config.userDataDir, "My-Bisq")));
        assertThat(config.configFile, equalTo(new File(config.appDataDir, DEFAULT_CONFIG_FILE_NAME)));
    }

    @Test
    public void whenAppDataDirOptionIsSet_thenDataDirPropertiesReflectItsValue() throws IOException {
        File appDataDir = Files.createTempDirectory("myapp").toFile();
        Config config = configWithOpts(opt(APP_DATA_DIR, appDataDir));
        assertThat(config.appName, equalTo(config.defaultAppName));
        assertThat(config.userDataDir, equalTo(config.defaultUserDataDir));
        assertThat(config.appDataDir, equalTo(appDataDir));
        assertThat(config.configFile, equalTo(new File(config.appDataDir, DEFAULT_CONFIG_FILE_NAME)));
    }

    @Test
    public void whenUserDataDirOptionIsSet_thenDataDirPropertiesReflectItsValue() throws IOException {
        File userDataDir = Files.createTempDirectory("myuserdata").toFile();
        Config config = configWithOpts(opt(USER_DATA_DIR, userDataDir));
        assertThat(config.appName, equalTo(config.defaultAppName));
        assertThat(config.userDataDir, equalTo(userDataDir));
        assertThat(config.appDataDir, equalTo(new File(userDataDir, config.defaultAppName)));
        assertThat(config.configFile, equalTo(new File(config.appDataDir, DEFAULT_CONFIG_FILE_NAME)));
    }

    @Test
    public void whenAppNameAndAppDataDirOptionsAreSet_thenDataDirPropertiesReflectTheirValues() throws IOException {
        File appDataDir = Files.createTempDirectory("myapp").toFile();
        Config config = configWithOpts(opt(APP_NAME, "My-Bisq"), opt(APP_DATA_DIR, appDataDir));
        assertThat(config.appName, equalTo("My-Bisq"));
        assertThat(config.userDataDir, equalTo(config.defaultUserDataDir));
        assertThat(config.appDataDir, equalTo(appDataDir));
        assertThat(config.configFile, equalTo(new File(config.appDataDir, DEFAULT_CONFIG_FILE_NAME)));
    }

    @Test
    public void whenOptionIsSetAtCommandLineAndInConfigFile_thenCommandLineValueTakesPrecedence() throws IOException {
        File configFile = File.createTempFile("bisq", "properties");
        try (PrintWriter writer = new PrintWriter(configFile)) {
            writer.println(new ConfigFileOption(APP_NAME, "Bisq-configFileValue"));
        }
        Config config = configWithOpts(opt(APP_NAME, "Bisq-commandLineValue"));
        assertThat(config.appName, equalTo("Bisq-commandLineValue"));
    }

    @Test
    public void whenUnrecognizedOptionIsSet_thenConfigExceptionIsThrown() {
        exceptionRule.expect(ConfigException.class);
        exceptionRule.expectMessage("problem parsing option 'bogus': bogus is not a recognized option");
        configWithOpts(opt("bogus"));
    }

    @Test
    public void whenUnrecognizedOptionIsSetInConfigFile_thenNoExceptionIsThrown() throws IOException {
        File configFile = File.createTempFile("bisq", "properties");
        try (PrintWriter writer = new PrintWriter(configFile)) {
            writer.println(new ConfigFileOption("bogusOption", "bogusValue"));
            writer.println(new ConfigFileOption(APP_NAME, "BisqTest"));
        }
        Config config = configWithOpts(opt(CONFIG_FILE, configFile.getAbsolutePath()));
        assertThat(config.appName, equalTo("BisqTest"));
    }

    @Test
    public void whenOptionFileArgumentDoesNotExist_thenConfigExceptionIsThrown() {
        String filepath = "/does/not/exist";
        if (System.getProperty("os.name").startsWith("Windows")) {
            filepath = "C:\\does\\not\\exist";
        }
        exceptionRule.expect(ConfigException.class);
        exceptionRule.expectMessage(format("problem parsing option 'torrcFile': File [%s] does not exist", filepath));
        configWithOpts(opt(TORRC_FILE, filepath));
    }

    @Test
    public void whenConfigFileOptionIsSetToNonExistentFile_thenConfigExceptionIsThrown() {
        String filepath = "/no/such/bisq.properties";
        if (System.getProperty("os.name").startsWith("Windows")) {
            filepath = "C:\\no\\such\\bisq.properties";
        }
        exceptionRule.expect(ConfigException.class);
        exceptionRule.expectMessage(format("The specified config file '%s' does not exist", filepath));
        configWithOpts(opt(CONFIG_FILE, filepath));
    }

    @Test
    public void whenConfigFileOptionIsSetInConfigFile_thenConfigExceptionIsThrown() throws IOException {
        File configFile = File.createTempFile("bisq", "properties");
        try (PrintWriter writer = new PrintWriter(configFile)) {
            writer.println(new ConfigFileOption(CONFIG_FILE, "/tmp/other.bisq.properties"));
        }
        exceptionRule.expect(ConfigException.class);
        exceptionRule.expectMessage(format("The '%s' option is disallowed in config files", CONFIG_FILE));
        configWithOpts(opt(CONFIG_FILE, configFile.getAbsolutePath()));
    }

    @Test
    public void whenConfigFileOptionIsSetToExistingFile_thenConfigFilePropertyReflectsItsValue() throws IOException {
        File configFile = File.createTempFile("bisq", "properties");
        Config config = configWithOpts(opt(CONFIG_FILE, configFile.getAbsolutePath()));
        assertThat(config.configFile, equalTo(configFile));
    }

    @Test
    public void whenConfigFileOptionIsSetToRelativePath_thenThePathIsPrefixedByAppDataDir() throws IOException {
        File configFile = Files.createTempFile("my-bisq", ".properties").toFile();
        File appDataDir = configFile.getParentFile();
        String relativeConfigFilePath = configFile.getName();
        Config config = configWithOpts(opt(APP_DATA_DIR, appDataDir), opt(CONFIG_FILE, relativeConfigFilePath));
        assertThat(config.configFile, equalTo(configFile));
    }

    @Test
    public void whenAppNameIsSetInConfigFile_thenDataDirPropertiesReflectItsValue() throws IOException {
        File configFile = File.createTempFile("bisq", "properties");
        try (PrintWriter writer = new PrintWriter(configFile)) {
            writer.println(new ConfigFileOption(APP_NAME, "My-Bisq"));
        }
        Config config = configWithOpts(opt(CONFIG_FILE, configFile.getAbsolutePath()));
        assertThat(config.appName, equalTo("My-Bisq"));
        assertThat(config.userDataDir, equalTo(config.defaultUserDataDir));
        assertThat(config.appDataDir, equalTo(new File(config.userDataDir, config.appName)));
        assertThat(config.configFile, equalTo(configFile));
    }

    @Test
    public void whenBannedBtcNodesOptionIsSet_thenBannedBtcNodesPropertyReturnsItsValue() {
        Config config = configWithOpts(opt(BANNED_BTC_NODES, "foo.onion:8333,bar.onion:8333"));
        assertThat(config.bannedBtcNodes, contains("foo.onion:8333", "bar.onion:8333"));
    }

    @Test
    public void whenHelpOptionIsSet_thenIsHelpRequestedIsTrue() {
        assertFalse(new Config().helpRequested);
        assertTrue(configWithOpts(opt(HELP)).helpRequested);
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

    @Test
    public void whenConfigIsConstructed_thenAppDataDirAndSubdirsAreCreated() {
        Config config = new Config();
        assertTrue(config.appDataDir.exists());
        assertTrue(config.keyStorageDir.exists());
        assertTrue(config.storageDir.exists());
        assertTrue(config.torDir.exists());
        assertTrue(config.walletDir.exists());
    }

    @Test
    public void whenAppDataDirCannotBeCreated_thenUncheckedIoExceptionIsThrown() throws IOException {
        // set a userDataDir that is actually a file so appDataDir cannot be created
        File aFile = Files.createTempFile("A", "File").toFile();
        exceptionRule.expect(UncheckedIOException.class);
        exceptionRule.expectMessage(containsString("Application data directory"));
        exceptionRule.expectMessage(containsString("could not be created"));
        configWithOpts(opt(USER_DATA_DIR, aFile));
    }

    @Test
    public void whenAppDataDirIsSymbolicLink_thenAppDataDirCreationIsNoOp() throws IOException {
        Path parentDir = Files.createTempDirectory("parent");
        Path targetDir = parentDir.resolve("target");
        Path symlink = parentDir.resolve("symlink");
        Files.createDirectory(targetDir);
        try {
            Files.createSymbolicLink(symlink, targetDir);
        } catch (Throwable ex) {
            // An error occurred trying to create a symbolic link, likely because the
            // operating system (e.g. Windows) does not support it, so we abort the test.
            return;
        }
        configWithOpts(opt(APP_DATA_DIR, symlink));
    }


    // == TEST SUPPORT FACILITIES ========================================================

    static Config configWithOpts(Opt... opts) {
        String[] args = new String[opts.length];
        for (int i = 0; i < opts.length; i++)
            args[i] = opts[i].toString();
        return new Config(args);
    }

    static Opt opt(String name) {
        return new Opt(name);
    }

    static Opt opt(String name, Object arg) {
        return new Opt(name, arg.toString());
    }

    static class Opt {
        private final String name;
        private final String arg;

        public Opt(String name) {
            this(name, null);
        }

        public Opt(String name, String arg) {
            this.name = name;
            this.arg = arg;
        }

        @Override
        public String toString() {
            return format("--%s%s", name, arg != null ? ("=" + arg) : "");
        }
    }
}
