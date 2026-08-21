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
package de.jensd.fx.glyphs;

public interface GlyphIcons {

    String name();

    String unicode();

    String fontFamily();
}
