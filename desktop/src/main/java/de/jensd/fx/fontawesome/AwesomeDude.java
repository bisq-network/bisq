/*
 * Copyright (c) 2013-2016 Jens Deters <mail@jensd.de>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Originally part of FontAwesomeFX (https://bitbucket.org/Jerady/fontawesomefx).
 * Trimmed for Bisq — only the API surface used by the project is retained.
 */
package de.jensd.fx.fontawesome;

import de.jensd.fx.glyphs.GlyphIcons;

import javafx.scene.control.Label;
import javafx.scene.text.Font;

public final class AwesomeDude {

    public static final String FONT_AWESOME_TTF_PATH = "/font/fontawesome-webfont.ttf";
    public static final String DEFAULT_ICON_SIZE = "16.0";

    static {
        Font.loadFont(AwesomeDude.class.getResource(FONT_AWESOME_TTF_PATH).toExternalForm(), 10.0);
    }

    private AwesomeDude() {
    }

    public static void setIcon(Label label, AwesomeIcon icon) {
        setIcon(label, icon, DEFAULT_ICON_SIZE);
    }

    public static void setIcon(Label label, GlyphIcons icon) {
        if (icon instanceof AwesomeIcon) {
            setIcon(label, (AwesomeIcon) icon, DEFAULT_ICON_SIZE);
        } else {
            label.setText(icon.unicode());
            label.setStyle("-fx-font-family: " + icon.fontFamily() + "; -fx-font-size: " + DEFAULT_ICON_SIZE + ";");
        }
    }

    public static void setIcon(Label label, AwesomeIcon icon, String iconSize) {
        label.setText(icon.toString());
        label.getStyleClass().add("awesome");
        label.setStyle("-fx-font-family: FontAwesome; -fx-font-size: " + iconSize + ";");
    }
}
