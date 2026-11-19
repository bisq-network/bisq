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

package bisq.core.locale;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import java.io.InputStream;
import java.io.InputStreamReader;

import java.text.MessageFormat;

import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class DisplayStringsTest {
    private static final Pattern ARGUMENT = Pattern.compile("\\{(\\d+)[},]");
    // An odd number of apostrophes in a row, of which one starts a quoted part
    private static final Pattern SINGLE_APOSTROPHE = Pattern.compile("(?<!')'(?:'')*(?!')");

    // A text with an argument is formatted as a message format, in which a single apostrophe starts a quoted part:
    // the apostrophe is not shown, and an argument after it is shown as {0}. Such a text writes an apostrophe as ''.
    @Test
    void textsWithArgumentsDoubleTheirApostrophes() throws Exception {
        Properties english = load("displayStrings.properties");

        assertEquals(List.of(), textsWithSingleApostrophe(english, english));
    }

    // A translation is formatted with the arguments of the English text
    @ParameterizedTest
    @MethodSource("translations")
    void translatedTextsWithArgumentsDoubleTheirApostrophes(String fileName) throws Exception {
        Properties english = load("displayStrings.properties");
        Properties translation = load(fileName);

        assertEquals(List.of(), textsWithSingleApostrophe(english, translation), fileName);
    }

    // A text without arguments is shown as it is, so an apostrophe written as '' would be shown twice
    @ParameterizedTest
    @MethodSource("translations")
    void translatedTextsWithoutArgumentsWriteTheirApostrophesOnce(String fileName) throws Exception {
        Properties english = load("displayStrings.properties");
        Properties translation = load(fileName);

        List<String> textsWithDoubledApostrophe = translation.stringPropertyNames().stream()
                .filter(key -> english.containsKey(key) && !ARGUMENT.matcher(english.getProperty(key)).find())
                .filter(key -> translation.getProperty(key).contains("''"))
                .sorted()
                .collect(Collectors.toList());

        assertEquals(List.of(), textsWithDoubledApostrophe, fileName);
    }

    // A translation with another argument than the English text shows it as {0}, and one without an argument of the
    // English text leaves out its value, for example the trade ID or the address in a warning.
    @ParameterizedTest
    @MethodSource("translations")
    void translatedTextsUseTheArgumentsOfTheEnglishText(String fileName) throws Exception {
        Properties english = load("displayStrings.properties");
        Properties translation = load(fileName);

        List<String> textsWithOtherArguments = translation.stringPropertyNames().stream()
                .filter(english::containsKey)
                .filter(key -> !arguments(translation.getProperty(key)).equals(arguments(english.getProperty(key))) ||
                        !isValidMessageFormat(english.getProperty(key), translation.getProperty(key)))
                .sorted()
                .collect(Collectors.toList());

        assertEquals(List.of(), textsWithOtherArguments, fileName);
    }

    // The translations next to the English texts, displayStrings_<language>.properties
    static Stream<String> translations() throws Exception {
        Path english = Paths.get(DisplayStringsTest.class.getResource("/i18n/displayStrings.properties").toURI());
        List<String> fileNames;
        try (Stream<Path> files = Files.list(english.getParent())) {
            fileNames = files.map(path -> path.getFileName().toString())
                    .filter(name -> name.matches("displayStrings_.+\\.properties"))
                    .sorted()
                    .collect(Collectors.toList());
        }
        assertFalse(fileNames.isEmpty());
        return fileNames.stream();
    }

    private static List<String> textsWithSingleApostrophe(Properties english, Properties texts) {
        return texts.stringPropertyNames().stream()
                .filter(key -> english.containsKey(key) && ARGUMENT.matcher(english.getProperty(key)).find())
                .filter(key -> SINGLE_APOSTROPHE.matcher(texts.getProperty(key)).find())
                .sorted()
                .collect(Collectors.toList());
    }

    private static Set<String> arguments(String text) {
        Set<String> arguments = new TreeSet<>();
        Matcher matcher = ARGUMENT.matcher(text);
        while (matcher.find()) {
            arguments.add(matcher.group(1));
        }
        return arguments;
    }

    // Only a text with arguments is formatted as a message format
    private static boolean isValidMessageFormat(String englishText, String text) {
        if (!ARGUMENT.matcher(englishText).find()) {
            return true;
        }
        try {
            new MessageFormat(text);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private Properties load(String fileName) throws Exception {
        Properties properties = new Properties();
        try (InputStream inputStream = getClass().getResourceAsStream("/i18n/" + fileName)) {
            assertNotNull(inputStream);
            properties.load(new InputStreamReader(inputStream, StandardCharsets.UTF_8));
        }
        assertFalse(properties.isEmpty());
        return properties;
    }
}
