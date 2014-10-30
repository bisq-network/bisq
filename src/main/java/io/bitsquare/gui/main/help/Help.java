/*
 * This file is part of Bitsquare.
 *
 * Bitsquare is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * Bitsquare is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Bitsquare. If not, see <http://www.gnu.org/licenses/>.
 */

package io.bitsquare.gui.main.help;

import io.bitsquare.BitsquareUI;

import java.net.MalformedURLException;
import java.net.URL;

import javafx.scene.*;
import javafx.scene.web.*;
import javafx.stage.Modality;
import javafx.stage.Stage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// TODO Find good solution for a web based help content management system.
public class Help {
    private static final Logger log = LoggerFactory.getLogger(Help.class);

    private static Stage helpWindow;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Public methods
    ///////////////////////////////////////////////////////////////////////////////////////////

    public static void openWindow(HelpId id) {
        try {
            URL url = new URL("https://docs.bitsquare.io/0.1.0-SNAPSHOT/userguide/index.html");
            // URL url = new URL("https://docs.bitsquare.io/0.1.0-SNAPSHOT/userguide/index.html#" + id);
            WebView webView;
            if (helpWindow == null) {
                helpWindow = new Stage();
                helpWindow.initModality(Modality.NONE);
                helpWindow.initOwner(BitsquareUI.getPrimaryStage());
                webView = new WebView();
                helpWindow.setScene(new Scene(webView, 800, 600));
            }
            else {
                webView = (WebView) helpWindow.getScene().getRoot();
            }
            helpWindow.setTitle(url.toString());
            webView.getEngine().load(url.toString());
            helpWindow.show();
        } catch (MalformedURLException e) {
            log.error(e.getMessage());
        }
    }

}
