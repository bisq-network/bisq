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
    private static final Pattern SINGLE_APOSTROPHE = Pattern.compile("(?<!')'(?!')");

    // A text with an argument is formatted as a message format, in which a single apostrophe starts a quoted part:
    // the apostrophe is not shown, and an argument after it is shown as {0}. Such a text writes an apostrophe as ''.
    // The translations come from Transifex and are not checked here.
    @Test
    void textsWithArgumentsDoubleTheirApostrophes() throws Exception {
        Properties displayStrings = new Properties();
        try (InputStream inputStream = getClass().getResourceAsStream("/i18n/displayStrings.properties")) {
            assertNotNull(inputStream);
            displayStrings.load(new InputStreamReader(inputStream, StandardCharsets.UTF_8));
        }
        assertFalse(displayStrings.isEmpty());

        List<String> textsWithSingleApostrophe = displayStrings.stringPropertyNames().stream()
                .filter(key -> ARGUMENT.matcher(displayStrings.getProperty(key)).find())
                .filter(key -> SINGLE_APOSTROPHE.matcher(displayStrings.getProperty(key)).find())
                .sorted()
                .collect(Collectors.toList());

        assertEquals(List.of(), textsWithSingleApostrophe);
    }
}
