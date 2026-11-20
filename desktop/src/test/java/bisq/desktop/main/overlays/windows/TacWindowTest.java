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

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TacWindowTest {
    @BeforeEach
    void setUp() {
        Res.setup();
    }

    @Test
    void theCopiedTextHasTheTextOfBothPagesInTheirOrder() {
        List<String> shown = List.of("tacWindow.headline",
                "tacWindow.risk.headline",
                "tacWindow.risk.p2p.title",
                "tacWindow.risk.p2p.body",
                "tacWindow.risk.financial.title",
                "tacWindow.risk.financial.body",
                "tacWindow.risk.noGuarantees.title",
                "tacWindow.risk.noGuarantees.body",
                "tacWindow.risk.warning",
                "tacWindow.legal.headline",
                "tacWindow.legal.section1.title",
                "tacWindow.legal.section1.body",
                "tacWindow.legal.section7.title",
                "tacWindow.legal.section7.body",
                "tacWindow.legal.acknowledgment");

        String text = new TacWindow().getAgreementText();

        int from = 0;
        for (String key : shown) {
            int at = text.indexOf(Res.get(key), from);
            assertTrue(at >= 0, key);
            from = at + Res.get(key).length();
        }
    }

    // A section added to or removed from the texts must also be shown and copied
    @Test
    void everyLegalSectionOfTheTextsIsCopied() {
        int numSections = 0;
        while (Res.getResourceBundle().containsKey("tacWindow.legal.section" + (numSections + 1) + ".title")) {
            numSections++;
        }
        assertEquals(TacWindow.NUM_LEGAL_SECTIONS, numSections);

        String text = new TacWindow().getAgreementText();

        for (int section = 1; section <= numSections; section++) {
            assertTrue(text.contains(section + ". " + Res.get("tacWindow.legal.section" + section + ".title") +
                            System.lineSeparator() + Res.get("tacWindow.legal.section" + section + ".body")),
                    "section " + section);
        }
    }

    // A risk added to or removed from the texts must also be shown and copied
    @Test
    void theRiskRowsAreTheRisksOfTheTexts() {
        Set<String> risksOfTheTexts = Res.getResourceBundle().keySet().stream()
                .filter(key -> key.startsWith("tacWindow.risk.") && key.endsWith(".title"))
                .map(key -> key.substring("tacWindow.risk.".length(), key.length() - ".title".length()))
                .collect(Collectors.toSet());

        assertEquals(risksOfTheTexts, TacWindow.RISK_ROWS.stream()
                .map(TacWindow.RiskRow::name)
                .collect(Collectors.toSet()));
    }

    // The texts of the risks already say what the user acknowledges
    @Test
    void theAcknowledgmentsAreNotCopied() {
        for (boolean wasAccepted : new boolean[]{false, true}) {
            String text = new TacWindow(wasAccepted).getAgreementText();

            for (String key : List.of("tacWindow.risk.confirm.headline",
                    "tacWindow.risk.confirm.headline.accepted",
                    "tacWindow.risk.accept1",
                    "tacWindow.risk.accept2",
                    "tacWindow.legal.accept3")) {
                assertFalse(text.contains(Res.get(key)), key);
            }
        }
    }
}
