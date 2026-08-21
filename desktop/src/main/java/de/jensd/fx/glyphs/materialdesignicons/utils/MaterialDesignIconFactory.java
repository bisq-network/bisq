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
package de.jensd.fx.glyphs.materialdesignicons.utils;

import de.jensd.fx.glyphs.GlyphIcons;

import javafx.scene.control.Button;
import javafx.scene.control.ContentDisplay;
import javafx.scene.text.Font;
import javafx.scene.text.Text;

public final class MaterialDesignIconFactory {

    public static final String TTF_PATH = "/de/jensd/fx/glyphs/materialdesignicons/materialdesignicons-webfont.ttf";

    private static MaterialDesignIconFactory instance;

    static {
        Font.loadFont(MaterialDesignIconFactory.class.getResource(TTF_PATH).toExternalForm(), 10.0);
    }

    private MaterialDesignIconFactory() {
    }

    public static MaterialDesignIconFactory get() {
        if (instance == null) {
            instance = new MaterialDesignIconFactory();
        }
        return instance;
    }

    public Text createIcon(GlyphIcons icon, String iconSize) {
        Text text = new Text(icon.unicode());
        text.getStyleClass().add("glyph-icon");
        text.setStyle("-fx-font-family: " + icon.fontFamily() + "; -fx-font-size: " + iconSize + ";");
        return text;
    }

    public Button createIconButton(GlyphIcons icon, String text) {
        Button button = new Button(text);
        button.setGraphic(createIcon(icon, "16.0"));
        return button;
    }

    public Button createIconButton(GlyphIcons icon, String text, String iconSize, String fontSize, ContentDisplay contentDisplay) {
        Text graphic = createIcon(icon, iconSize);
        Button button = new Button(text);
        if (fontSize != null) {
            button.setStyle("-fx-font-size: " + fontSize);
        }
        button.setGraphic(graphic);
        button.setContentDisplay(contentDisplay);
        return button;
    }
}
