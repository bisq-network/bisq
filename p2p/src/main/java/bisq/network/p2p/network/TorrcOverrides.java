/*
 * This file is part of Bisq.
 *
 * Bisq is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * Bisq is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Bisq. If not, see <http://www.gnu.org/licenses/>.
 */

package bisq.network.p2p.network;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import java.io.File;
import java.io.IOException;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;

import lombok.extern.slf4j.Slf4j;

import static com.google.common.base.Preconditions.checkArgument;

/**
 * The torrc entries which the user adds with --torrcFile and --torrcOptions. An entry of --torrcOptions overrides an
 * entry of the file with the same key. Parses like netlayer's Torrc did.
 * <p>
 * An entry which makes Tor start a program is accepted only if the command line sets the option which brings it. The
 * node writes bisq.properties itself, and whoever can change a config file in the data directory could otherwise run
 * a program with the rights of the user at the next start of Tor, also with the sandbox off.
 */
@Slf4j
final class TorrcOverrides {
    // Tor starts the program of such an entry. Tor ignores the case of option names and accepts the start of a name.
    private static final List<String> KEYS_STARTING_PROGRAMS = List.of("clienttransportplugin", "servertransportplugin");

    private TorrcOverrides() {
    }

    /**
     * @throws IllegalArgumentException if a config file brings an entry which makes Tor start a program
     */
    static LinkedHashMap<String, String> parse(TorrcSettings torrcSettings) {
        LinkedHashMap<String, String> torrcOverrides = new LinkedHashMap<>();
        File torrcFile = torrcSettings.torrcFile();
        if (torrcFile != null) {
            try {
                LinkedHashMap<String, String> fileEntries =
                        parseTorrcFile(Files.readAllLines(torrcFile.toPath(), StandardCharsets.UTF_8));
                if (!torrcSettings.torrcFileSetOnCommandLine()) {
                    rejectProgramStarts(fileEntries, "the torrc file " + torrcFile + ", which a config file names");
                }
                torrcOverrides.putAll(fileEntries);
            } catch (IOException e) {
                log.error("custom torrc file not found ('{}'). Proceeding with defaults.", torrcFile);
            }
        }
        LinkedHashMap<String, String> optionEntries = parseTorrcOptions(torrcSettings.torrcOptions());
        if (!torrcSettings.torrcOptionsSetOnCommandLine()) {
            rejectProgramStarts(optionEntries, "the option torrcOptions of a config file");
        }
        torrcOverrides.putAll(optionEntries);
        return torrcOverrides;
    }

    static boolean startsAProgram(String key) {
        String lowerCaseKey = key.toLowerCase(Locale.ROOT);
        return !lowerCaseKey.isEmpty() &&
                KEYS_STARTING_PROGRAMS.stream().anyMatch(name -> name.startsWith(lowerCaseKey));
    }

    private static void rejectProgramStarts(LinkedHashMap<String, String> entries, String source) {
        entries.keySet().stream()
                .filter(TorrcOverrides::startsAProgram)
                .findFirst()
                .ifPresent(key -> {
                    throw new IllegalArgumentException("The torrc entry " + key + " of " + source + " makes Tor " +
                            "start a program. Bisq accepts such entries only on the command line: pass --torrcOptions " +
                            "or --torrcFile there, and remove the option from the config file.");
                });
    }

    private static LinkedHashMap<String, String> parseTorrcFile(List<String> lines) {
        LinkedHashMap<String, String> entries = new LinkedHashMap<>();
        lines.stream()
                .map(String::trim)
                .filter(line -> line.length() > 5 && !line.startsWith("#") && line.contains(" "))
                .forEach(line -> {
                    int delimiter = line.indexOf(' ');
                    putEntry(entries, line.substring(0, delimiter), line.substring(delimiter));
                });
        return entries;
    }

    private static LinkedHashMap<String, String> parseTorrcOptions(String torrcOptions) {
        LinkedHashMap<String, String> entries = new LinkedHashMap<>();
        if (!"".equals(torrcOptions)) {
            Arrays.asList(torrcOptions.split(",")).forEach(line -> {
                line = line.trim();
                if (line.matches("^[^\\s]+\\s.+")) {
                    String[] tmp = line.split("\\s", 2);
                    putEntry(entries, tmp[0], tmp[1]);
                } else {
                    log.error("custom torrc override parse error ('{}'). Proceeding without custom overrides.", line);
                    entries.clear();
                }
            });
        }
        return entries;
    }

    private static void putEntry(LinkedHashMap<String, String> entries, String key, String value) {
        String trimmedKey = key.trim();
        checkArgument(!trimmedKey.isEmpty() && trimmedKey.chars().noneMatch(Character::isWhitespace),
                "Invalid torrc directive key: %s", key);
        entries.put(trimmedKey, value.trim());
    }
}
