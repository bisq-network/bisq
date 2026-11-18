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

import java.io.InputStream;
import java.io.InputStreamReader;

import java.util.List;
import java.util.Properties;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class DisplayStringsTest {
    private static final Pattern ARGUMENT = Pattern.compile("\\{\\d+[},]");
    // An odd number of apostrophes in a row, of which one starts a quoted part
    private static final Pattern SINGLE_APOSTROPHE = Pattern.compile("(?<!')'(?:'')*(?!')");

    // A text with an argument is formatted as a message format, in which a single apostrophe starts a quoted part:
    // the apostrophe is not shown, and an argument after it is shown as {0}. Such a text writes an apostrophe as ''.
    @Test
    void textsWithArgumentsDoubleTheirApostrophes() throws Exception {
        Properties english = load("displayStrings.properties");

        assertEquals(List.of(), textsWithSingleApostrophe(english, english));
    }

    // A translation is formatted with the arguments of the English text. The translations come from Transifex. Of
    // them only French is checked, as several others still have texts with a single apostrophe.
    @Test
    void frenchTextsWithArgumentsDoubleTheirApostrophes() throws Exception {
        Properties english = load("displayStrings.properties");
        Properties french = load("displayStrings_fr.properties");

        assertEquals(List.of(), textsWithSingleApostrophe(english, french));
    }

    private static List<String> textsWithSingleApostrophe(Properties english, Properties texts) {
        return texts.stringPropertyNames().stream()
                .filter(key -> english.containsKey(key) && ARGUMENT.matcher(english.getProperty(key)).find())
                .filter(key -> SINGLE_APOSTROPHE.matcher(texts.getProperty(key)).find())
                .sorted()
                .collect(Collectors.toList());
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
