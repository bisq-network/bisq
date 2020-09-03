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

import javafx.scene.Scene;

public class CssTheme {
    public static final int CSS_THEME_LIGHT = 0;
    public static final int CSS_THEME_DARK = 1;

    private static int currentCSSTheme;
    private static boolean useDevHeader;

    public static void loadSceneStyles(Scene scene, int cssTheme, boolean devHeader) {
        String cssThemeFolder = "/bisq/desktop/";
        String cssThemeFile = "";

        currentCSSTheme = cssTheme;
        useDevHeader = devHeader;

        switch (cssTheme) {

            case CSS_THEME_DARK:
                cssThemeFile = "theme-dark.css";
                break;

            case CSS_THEME_LIGHT:
            default:
                cssThemeFile = "theme-light.css";
                break;
        }

        scene.getStylesheets().setAll(
                // load base styles first
                cssThemeFolder + "bisq.css",
                cssThemeFolder + "images.css",
                cssThemeFolder + "CandleStickChart.css",

                // load theme last to allow override
                cssThemeFolder + cssThemeFile
        );
        if (useDevHeader)
            scene.getStylesheets().add(cssThemeFolder + "theme-dev.css");
    }

    public static boolean isDarkTheme() {
        return currentCSSTheme == CSS_THEME_DARK;
    }

}
