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

package io.bitsquare.gui.popups;

import io.bitsquare.common.util.Utilities;
import javafx.geometry.HPos;
import javafx.geometry.Insets;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.web.WebView;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class WebViewPopup extends Popup {
    private static final Logger log = LoggerFactory.getLogger(WebViewPopup.class);

    protected WebView webView;
    protected String url;

    public static String getLocalUrl(String htmlFile) {
        return WebViewPopup.class.getResource("/html/" + htmlFile + ".html").toExternalForm();
    }

    public WebViewPopup() {
    }

    @Override
    public WebViewPopup show() {
        width = 700;

        webView = new WebView();
        webView.setPrefHeight(0);

        // open links with http and _blank in default web browser instead of webView
        Utilities.setupWebViewPopupHandler(webView.getEngine());

        webView.getEngine().documentProperty().addListener((observable, oldValue, newValue) -> {
            String heightInPx = webView.getEngine()
                    .executeScript("window.getComputedStyle(document.body, null).getPropertyValue('height')").toString();
            double height = Double.valueOf(heightInPx.replace("px", ""));
            webView.setPrefHeight(height);
            stage.setMinHeight(height + gridPane.getHeight());
            centerPopup();
        });

        createGridPane();
        addHtmlContent();
        createPopup();
        return this;
    }

    public WebViewPopup url(String url) {
        this.url = url;
        return this;
    }

    protected void addHtmlContent() {
        webView.getEngine().load(url);
        GridPane.setHalignment(webView, HPos.LEFT);
        GridPane.setHgrow(webView, Priority.ALWAYS);
        GridPane.setMargin(webView, new Insets(3, 0, 0, 0));
        GridPane.setRowIndex(webView, ++rowIndex);
        GridPane.setColumnIndex(webView, 0);
        GridPane.setColumnSpan(webView, 2);
        gridPane.getChildren().add(webView);
    }
}
