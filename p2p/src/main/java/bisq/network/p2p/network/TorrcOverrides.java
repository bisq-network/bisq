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

import lombok.extern.slf4j.Slf4j;

import javax.annotation.Nullable;

import static com.google.common.base.Preconditions.checkArgument;

/**
 * The torrc entries which the user adds with --torrcFile and --torrcOptions. An entry of --torrcOptions overrides an
 * entry of the file with the same key. Parses like netlayer's Torrc did.
 */
@Slf4j
final class TorrcOverrides {
    private TorrcOverrides() {
    }

    static LinkedHashMap<String, String> parse(@Nullable File torrcFile, String torrcOptions) {
        LinkedHashMap<String, String> torrcOverrides = new LinkedHashMap<>();
        if (torrcFile != null) {
            try {
                torrcOverrides.putAll(parseTorrcFile(Files.readAllLines(torrcFile.toPath(), StandardCharsets.UTF_8)));
            } catch (IOException e) {
                log.error("custom torrc file not found ('{}'). Proceeding with defaults.", torrcFile);
            }
        }
        torrcOverrides.putAll(parseTorrcOptions(torrcOptions));
        return torrcOverrides;
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
