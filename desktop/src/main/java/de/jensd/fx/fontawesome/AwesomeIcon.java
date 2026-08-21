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
 * Icon glyphs are from Font Awesome v3.2.1 by Dave Gandy
 * (http://fontawesome.io), licensed under SIL OFL 1.1 (font) and MIT (code).
 * Code points are in the Unicode Private Use Area.
 */
package de.jensd.fx.fontawesome;

import de.jensd.fx.glyphs.GlyphIcons;

public enum AwesomeIcon implements GlyphIcons {

    ARROW_RIGHT(''),
    BAN_CIRCLE(''),
    CIRCLE_ARROW_DOWN(''),
    CIRCLE_ARROW_UP(''),
    COPY(''),
    ENVELOPE(''),
    ENVELOPE_ALT(''),
    EXCHANGE(''),
    EXCLAMATION_SIGN(''),
    EXTERNAL_LINK(''),
    EYE_CLOSE(''),
    EYE_OPEN(''),
    FILE_TEXT(''),
    INFO_SIGN(''),
    LEAF(''),
    LOCK(''),
    MAIL_REPLY(''),
    MINUS(''),
    MONEY(''),
    OK(''),
    OK_CIRCLE(''),
    OK_SIGN(''),
    QUESTION(''),
    QUESTION_SIGN(''),
    REMOVE_CIRCLE(''),
    RETWEET(''),
    ROCKET(''),
    SHIELD(''),
    SIGNIN(''),
    SIGNOUT(''),
    THUMBS_DOWN(''),
    THUMBS_UP(''),
    TRASH(''),
    UNDO(''),
    UNLOCK(''),
    WARNING_SIGN('');

    public static final String FONT_FAMILY = "FontAwesome";

    private final char unicode;

    AwesomeIcon(char unicode) {
        this.unicode = unicode;
    }

    public char getChar() {
        return unicode;
    }

    @Override
    public String toString() {
        return String.valueOf(unicode);
    }

    @Override
    public String unicode() {
        return String.valueOf(unicode);
    }

    @Override
    public String fontFamily() {
        return FONT_FAMILY;
    }
}
