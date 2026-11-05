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
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;

import lombok.extern.slf4j.Slf4j;

import static com.google.common.base.Preconditions.checkArgument;

/**
 * The torrc entries which the user adds with --torrcFile and --torrcOptions. An entry of --torrcOptions overrides an
 * entry of the file with the same key. Parses like netlayer's Torrc did.
 * <p>
 * The node writes bisq.properties itself, and whoever can change a config file in the data directory could otherwise
 * make the next start of Tor run code with the rights of the user, also with the sandbox off. So the Tor library gets
 * the keys of the entries which a config file brings, and it refuses those which make Tor start a program or load a
 * library.
 *
 * @param entries            the entries in the order of the file and then of the options
 * @param keysFromConfigFile the keys of the entries which come from the option torrcOptions of a config file, or from a
 *                           torrc file which a config file names. An entry of the options of the command line replaces
 *                           the entry of such a file with the same key, and so it comes from the command line.
 */
@Slf4j
record TorrcOverrides(LinkedHashMap<String, String> entries, Set<String> keysFromConfigFile) {
    static TorrcOverrides parse(TorrcSettings torrcSettings) {
        LinkedHashMap<String, String> entries = new LinkedHashMap<>();
        Set<String> keysFromConfigFile = new HashSet<>();
        File torrcFile = torrcSettings.torrcFile();
        if (torrcFile != null) {
            try {
                LinkedHashMap<String, String> fileEntries =
                        parseTorrcFile(Files.readAllLines(torrcFile.toPath(), StandardCharsets.UTF_8));
                entries.putAll(fileEntries);
                if (!torrcSettings.torrcFileSetOnCommandLine()) {
                    keysFromConfigFile.addAll(fileEntries.keySet());
                }
            } catch (IOException e) {
                log.error("custom torrc file not found ('{}'). Proceeding with defaults.", torrcFile);
            }
        }
        LinkedHashMap<String, String> optionEntries = parseTorrcOptions(torrcSettings.torrcOptions());
        entries.putAll(optionEntries);
        if (torrcSettings.torrcOptionsSetOnCommandLine()) {
            keysFromConfigFile.removeAll(optionEntries.keySet());
        } else {
            keysFromConfigFile.addAll(optionEntries.keySet());
        }
        return new TorrcOverrides(entries, Set.copyOf(keysFromConfigFile));
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
