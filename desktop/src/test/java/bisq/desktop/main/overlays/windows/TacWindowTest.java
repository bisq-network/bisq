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

import bisq.core.app.TermsAndConditions;

import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TacWindowTest {
    // The window shows an icon for each risk of the terms, in their order
    @Test
    void theRiskRowsAreTheRisksOfTheTerms() {
        assertEquals(TermsAndConditions.RISKS, TacWindow.RISK_ROWS.stream()
                .map(TacWindow.RiskRow::name)
                .collect(Collectors.toList()));
    }
}
