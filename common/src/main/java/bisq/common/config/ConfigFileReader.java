package bisq.common.config;

import java.nio.file.Files;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;

import java.util.List;

import static java.util.stream.Collectors.toList;

class ConfigFileReader {

    private final File file;

    public ConfigFileReader(File file) {
        this.file = file;
    }

    public List<String> getLines() {
        if (!file.exists())
            throw new ConfigException("Config file %s does not exist", file);

        if (!file.canRead())
            throw new ConfigException("Config file %s is not readable", file);

        try {
            return Files.readAllLines(file.toPath()).stream()
                    .map(ConfigFileReader::cleanLine)
                    .collect(toList());
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    public List<String> getOptionLines() {
        return getLines().stream()
                .filter(ConfigFileOption::isOption)
                .collect(toList());
    }

    private static String cleanLine(String line) {
        return ConfigFileOption.isOption(line) ? ConfigFileOption.clean(line) : line;
    }
}
