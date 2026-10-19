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


package bisq.desktop.main.overlays.windows;

import bisq.core.locale.Res;

import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TradeRulesWindowTest {
    @BeforeEach
    void setUp() {
        Res.setup();
    }

    @Test
    void theCopiedTextHasTheHeadlineAndTheRulesInTheirOrder() {
        String text = new TradeRulesWindow().getRulesText();

        int from = text.indexOf(Res.get("tradeRules.headline"));
        assertTrue(from >= 0);
        for (int i = 0; i < TradeRulesWindow.RULES.size(); i++) {
            String rule = TradeRulesWindow.RULES.get(i);
            for (String part : new String[]{(i + 1) + ". " + Res.get("tradeRules." + rule + ".title"),
                    Res.get("tradeRules." + rule + ".body")}) {
                int at = text.indexOf(part, from);
                assertTrue(at >= from, part);
                from = at + part.length();
            }
        }
        assertFalse(text.contains(Res.get("tradeRules.accept")));
    }

    // A rule added to or removed from the texts must also be shown and copied
    @Test
    void theRulesAreTheRulesOfTheTexts() {
        Set<String> rulesOfTheTexts = Res.getResourceBundle().keySet().stream()
                .filter(key -> key.startsWith("tradeRules.") && key.endsWith(".title"))
                .map(key -> key.substring("tradeRules.".length(), key.length() - ".title".length()))
                .collect(Collectors.toSet());

        assertEquals(rulesOfTheTexts, Set.copyOf(TradeRulesWindow.RULES));
    }
}
