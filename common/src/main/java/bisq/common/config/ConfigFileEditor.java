package bisq.common.config;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.UncheckedIOException;

import java.util.List;

import org.slf4j.LoggerFactory;

import ch.qos.logback.classic.Logger;

public class ConfigFileEditor {

    private static final Logger log = (Logger) LoggerFactory.getLogger(ConfigFileEditor.class);

    private final File file;
    private final ConfigFileReader reader;

    public ConfigFileEditor(File file) {
        this.file = file;
        this.reader = new ConfigFileReader(file);
    }

    public void setOption(String name) {
        setOption(name, null);
    }

    public void setOption(String name, String arg) {
        tryCreate(file);
        List<String> lines = reader.getLines();
        try (PrintWriter writer = new PrintWriter(file)) {
            boolean fileAlreadyContainsTargetOption = false;
            for (String line : lines) {
                if (ConfigFileOption.isOption(line)) {
                    ConfigFileOption existingOption = ConfigFileOption.parse(line);
                    if (existingOption.name.equals(name)) {
                        fileAlreadyContainsTargetOption = true;
                        if (!existingOption.arg.equals(arg)) {
                            ConfigFileOption newOption = new ConfigFileOption(name, arg);
                            writer.println(newOption);
                            log.warn("Overwrote existing config file option '{}' as '{}'", existingOption, newOption);
                            continue;
                        }
                    }
                }
                writer.println(line);
            }
            if (!fileAlreadyContainsTargetOption)
                writer.println(new ConfigFileOption(name, arg));
        } catch (FileNotFoundException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    public void clearOption(String name) {
        if (!file.exists())
            return;

        List<String> lines = reader.getLines();
        try (PrintWriter writer = new PrintWriter(file)) {
            for (String line : lines) {
                if (ConfigFileOption.isOption(line)) {
                    ConfigFileOption option = ConfigFileOption.parse(line);
                    if (option.name.equals(name)) {
                        log.debug("Cleared existing config file option '{}'", option);
                        continue;
                    }
                }
                writer.println(line);
            }
        } catch (FileNotFoundException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    private void tryCreate(File file) {
        try {
            if (file.createNewFile())
                log.info("Created config file '{}'", file);
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }
}
