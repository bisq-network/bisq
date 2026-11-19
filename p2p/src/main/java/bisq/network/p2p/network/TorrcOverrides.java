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

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;

import static com.google.common.base.Preconditions.checkArgument;

/**
 * The torrc entries which the user adds with --torrcFile and --torrcOptions. An entry of --torrcOptions overrides an
 * entry of the file with the same key. Parses like netlayer's Torrc did, except that a tab in a line of the file also
 * separates the key from the value.
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
record TorrcOverrides(LinkedHashMap<String, String> entries, Set<String> keysFromConfigFile) {
    static TorrcOverrides parse(TorrcSettings torrcSettings) {
        LinkedHashMap<String, String> entries = new LinkedHashMap<>();
        Set<String> keysFromConfigFile = new HashSet<>();
        File torrcFile = torrcSettings.torrcFile();
        if (torrcFile != null) {
            LinkedHashMap<String, String> fileEntries = parseTorrcFile(readTorrcFile(torrcFile));
            entries.putAll(fileEntries);
            if (!torrcSettings.torrcFileSetOnCommandLine()) {
                keysFromConfigFile.addAll(fileEntries.keySet());
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

    // Config checks that the file can be read when Bisq starts, but the file can change until Tor starts, and a file
    // which is not UTF-8 text fails only here. The exception has no cause, because the failed start shows the message
    // of the root cause, which would only say for example "Input length = 1".
    private static List<String> readTorrcFile(File torrcFile) {
        try {
            return Files.readAllLines(torrcFile.toPath(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalArgumentException("Bisq cannot read the torrc file " + torrcFile + " as UTF-8 text (" +
                    e + "). Bisq starts Tor only with all entries of the file.");
        }
    }

    // As in a torrc of Tor, a tab separates the key from the value as well as a space. netlayer took only a space, so
    // it dropped an entry such as ExcludeNodes with a tab, and Tor started without the restriction.
    private static LinkedHashMap<String, String> parseTorrcFile(List<String> lines) {
        LinkedHashMap<String, String> entries = new LinkedHashMap<>();
        lines.stream()
                .map(String::trim)
                .filter(line -> line.length() > 5 && !line.startsWith("#"))
                .map(line -> line.split("\\s", 2))
                .filter(keyAndValue -> keyAndValue.length == 2)
                .forEach(keyAndValue -> putEntry(entries, keyAndValue[0], keyAndValue[1]));
        return entries;
    }

    // Tor starts only with all entries of the user: an entry such as ExcludeNodes restricts the relays which Tor uses,
    // and a user who relies on it would not notice that it is missing. The message does not show the part, which may
    // contain a password.
    private static LinkedHashMap<String, String> parseTorrcOptions(String torrcOptions) {
        LinkedHashMap<String, String> entries = new LinkedHashMap<>();
        String[] parts = torrcOptions.split(",");
        for (int i = 0; i < parts.length; i++) {
            String line = parts[i].trim();
            // An empty part, for example after a comma which follows the last entry, contains no entry
            if (line.isEmpty()) {
                continue;
            }
            String[] keyAndValue = line.split("\\s", 2);
            checkArgument(keyAndValue.length == 2, "Part %s of --torrcOptions is no torrc entry of a key, whitespace " +
                    "and a value. Bisq starts Tor only with all entries.", i + 1);
            putEntry(entries, keyAndValue[0], keyAndValue[1]);
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
