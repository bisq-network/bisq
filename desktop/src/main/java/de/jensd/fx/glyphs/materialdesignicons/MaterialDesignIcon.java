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
 * Trimmed for Bisq — only the constants referenced in this project are retained.
 *
 * Icon glyphs are from Material Design Icons by Austin Andrews / community,
 * licensed under SIL OFL 1.1 (font) and Apache 2.0 (code).
 * See https://materialdesignicons.com for the upstream project.
 */
package de.jensd.fx.glyphs.materialdesignicons;

import de.jensd.fx.glyphs.GlyphIcons;

public enum MaterialDesignIcon implements GlyphIcons {

    ALERT_CIRCLE_OUTLINE(""),
    APPROVAL(""),
    ARROW_RIGHT_BOLD_BOX_OUTLINE(""),
    BOX_SHADOW(""),
    CHART_LINE(""),
    CHECKBOX_MARKED_CIRCLE(""),
    CHECKBOX_MARKED_OUTLINE(""),
    CHECK_CIRCLE(""),
    CIRCLE(""),
    CLOCK(""),
    CLOSE(""),
    CLOSE_CIRCLE(""),
    COMMENT_MULTIPLE_OUTLINE(""),
    CONTENT_COPY(""),
    DELETE_FOREVER(""),
    EYE_OFF(""),
    GAVEL(""),
    HELP_CIRCLE_OUTLINE(""),
    INFORMATION_OUTLINE(""),
    LINK(""),
    LINK_OFF(""),
    LOCK(""),
    PENCIL(""),
    QRCODE(""),
    SHIELD_HALF_FULL(""),
    SWAP_VERTICAL("");

    public static final String FONT_FAMILY = "'Material Design Icons'";

    private final String unicode;

    MaterialDesignIcon(String unicode) {
        this.unicode = unicode;
    }

    @Override
    public String unicode() {
        return unicode;
    }

    @Override
    public String fontFamily() {
        return FONT_FAMILY;
    }
}
