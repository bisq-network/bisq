/*
 * Copyright (c) 2010, 2013, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

package io.bisq.gui.components.indicator;

import io.bisq.gui.components.indicator.skin.StaticProgressIndicatorSkin;
import javafx.beans.property.DoubleProperty;
import javafx.css.StyleableProperty;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.Skin;

// TODO Copied form OpenJFX, check license issues and way how we integrated it
// We changed behaviour which was not exposed via APIs

/**
 * A circular control which is used for indicating progress, either
 * infinite (aka indeterminate) or finite. Often used with the Task API for
 * representing progress of background Tasks.
 * <p>
 * ProgressIndicator sets focusTraversable to false.
 * </p>
 * <p/>
 * <p/>
 * This first example creates a ProgressIndicator with an indeterminate value :
 * <pre><code>
 * import javafx.scene.control.ProgressIndicator;
 * ProgressIndicator p1 = new ProgressIndicator();
 * </code></pre>
 * <p/>
 * <p/>
 * This next example creates a ProgressIndicator which is 25% complete :
 * <pre><code>
 * import javafx.scene.control.ProgressIndicator;
 * ProgressIndicator p2 = new ProgressIndicator();
 * p2.setProgress(0.25F);
 * </code></pre>
 * <p/>
 * Implementation of ProgressIndicator According to JavaFX UI Control API Specification
 *
 * @since JavaFX 2.0
 */

@SuppressWarnings({"SameParameterValue", "WeakerAccess"})
public class TxConfidenceIndicator extends ProgressIndicator {

    /**
     * Value for progress indicating that the progress is indeterminate.
     *
     * @see #setProgress
     */
    public static final double INDETERMINATE_PROGRESS = -1;

    private static final String DEFAULT_STYLE_CLASS = "progress-indicator";
    private DoubleProperty progress;

    /**
     * Creates a new indeterminate ProgressIndicator.
     */
    public TxConfidenceIndicator() {
        this(INDETERMINATE_PROGRESS);
    }

    /**
     * Creates a new ProgressIndicator with the given progress value.
     */
    @SuppressWarnings("unchecked")
    public TxConfidenceIndicator(double progress) {
        // focusTraversable is styleable through css. Calling setFocusTraversable
        // makes it look to css like the user set the value and css will not
        // override. Initializing focusTraversable by calling applyStyle with null
        // StyleOrigin ensures that css will be able to override the value.
        ((StyleableProperty) focusTraversableProperty()).applyStyle(null, Boolean.FALSE);
        setProgress(progress);
        getStyleClass().setAll(DEFAULT_STYLE_CLASS);
    }

    @Override
    protected Skin<?> createDefaultSkin() {
        return new StaticProgressIndicatorSkin(this);
    }
}
