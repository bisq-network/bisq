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
package bisq.core.app;

import bisq.core.locale.Res;

import java.util.ArrayList;
import java.util.List;

/**
 * The texts of the terms and conditions, which the desktop application shows in its terms window and a headless
 * application prints when they are not accepted yet.
 */
public final class TermsAndConditions {
    // The names of the texts of the risks, in the order the terms show them
    public static final List<String> RISKS = List.of("p2p", "financial", "noGuarantees");
    public static final int NUM_LEGAL_SECTIONS = 7;

    private TermsAndConditions() {
    }

    public static String getRiskTitle(String risk) {
        return Res.get("tacWindow.risk." + risk + ".title");
    }

    public static String getRiskBody(String risk) {
        return Res.get("tacWindow.risk." + risk + ".body");
    }

    public static String getLegalSectionTitle(int section) {
        return Res.get("tacWindow.legal.section" + section + ".title");
    }

    public static String getLegalSectionBody(int section) {
        return Res.get("tacWindow.legal.section" + section + ".body");
    }

    // The text of both pages of the terms window in their order, without the page subtitles, the acknowledgments and
    // the buttons
    public static String getText() {
        List<String> paragraphs = new ArrayList<>();
        paragraphs.add(Res.get("tacWindow.headline"));
        paragraphs.add(Res.get("tacWindow.risk.headline"));
        for (String risk : RISKS) {
            paragraphs.add(getRiskTitle(risk) + System.lineSeparator() + getRiskBody(risk));
        }
        paragraphs.add(Res.get("tacWindow.risk.warning"));
        paragraphs.add(Res.get("tacWindow.legal.headline"));
        for (int i = 1; i <= NUM_LEGAL_SECTIONS; i++) {
            paragraphs.add(i + ". " + getLegalSectionTitle(i) + System.lineSeparator() + getLegalSectionBody(i));
        }
        paragraphs.add(Res.get("tacWindow.legal.acknowledgment"));
        return String.join(System.lineSeparator() + System.lineSeparator(), paragraphs);
    }
}
