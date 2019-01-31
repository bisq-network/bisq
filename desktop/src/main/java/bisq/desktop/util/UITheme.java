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

package bisq.desktop.util;

import lombok.Getter;

public enum UITheme {
    LIGHT_THEME(0, "theme-light", "Light theme", false),
    DARK_THEME(1, "theme-dark", "Dark theme", true);

    @Getter
    private final int id;
    @Getter
    private final String cssName;
    @Getter
    private final String label;
    @Getter
    private boolean darkMode;

    UITheme(int id, String cssName, String label, boolean darkMode) {
        this.id = id;
        this.cssName = cssName;
        this.label = label;
        this.darkMode = darkMode;
    }
}
