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

package bisq.desktop.components.indicator;

import bisq.desktop.components.indicator.skin.StaticProgressIndicatorSkin;

import javafx.scene.control.Control;
import javafx.scene.control.Skin;

import javafx.css.PseudoClass;
import javafx.css.StyleableProperty;

import javafx.beans.property.DoubleProperty;
import javafx.beans.property.DoublePropertyBase;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyBooleanWrapper;

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
public class TxConfidenceIndicator extends Control {

    /**
     * Value for progress indicating that the progress is indeterminate.
     *
     * @see #setProgress
     */
    public static final double INDETERMINATE_PROGRESS = -1;

    /***************************************************************************
     *                                                                         *
     * Constructors                                                            *
     *                                                                         *
     **************************************************************************/
    /**
     * Initialize the style class to 'progress-indicator'.
     * <p/>
     * This is the selector class from which CSS can be used to style
     * this control.
     */
    private static final String DEFAULT_STYLE_CLASS = "progress-indicator";
    /**
     * Pseudoclass indicating this is a determinate (i.e., progress can be
     * determined) progress indicator.
     */
    private static final PseudoClass PSEUDO_CLASS_DETERMINATE = PseudoClass.getPseudoClass("determinate");
    /***************************************************************************
     *                                                                         *
     * Properties                                                              *
     *                                                                         *
     **************************************************************************/
    /**
     * Pseudoclass indicating this is an indeterminate (i.e., progress cannot
     * be determined) progress indicator.
     */
    private static final PseudoClass PSEUDO_CLASS_INDETERMINATE = PseudoClass.getPseudoClass("indeterminate");
    /**
     * A flag indicating whether it is possible to determine the progress
     * of the ProgressIndicator. Typically indeterminate progress bars are
     * rendered with some form of animation indicating potentially "infinite"
     * progress.
     */
    private ReadOnlyBooleanWrapper indeterminate;
    /**
     * The actual progress of the ProgressIndicator. A negative value for
     * progress indicates that the progress is indeterminate. A positive value
     * between 0 and 1 indicates the percentage of progress where 0 is 0% and 1
     * is 100%. Any value greater than 1 is interpreted as 100%.
     */
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

        // need to initialize pseudo-class state
        final int c = Double.compare(INDETERMINATE_PROGRESS, progress);
        pseudoClassStateChanged(PSEUDO_CLASS_INDETERMINATE, c == 0);
        pseudoClassStateChanged(PSEUDO_CLASS_DETERMINATE, c != 0);
    }

    public final boolean isIndeterminate() {
        return indeterminate == null || indeterminate.get();
    }

    private void setIndeterminate(boolean value) {
        indeterminatePropertyImpl().set(value);
    }

    public final ReadOnlyBooleanProperty indeterminateProperty() {
        return indeterminatePropertyImpl().getReadOnlyProperty();
    }

    private ReadOnlyBooleanWrapper indeterminatePropertyImpl() {
        if (indeterminate == null) {
            indeterminate = new ReadOnlyBooleanWrapper(true) {
                @Override
                protected void invalidated() {
                    final boolean active = get();
                    pseudoClassStateChanged(PSEUDO_CLASS_INDETERMINATE, active);
                    pseudoClassStateChanged(PSEUDO_CLASS_DETERMINATE, !active);
                }


                @Override
                public Object getBean() {
                    return TxConfidenceIndicator.this;
                }


                @Override
                public String getName() {
                    return "indeterminate";
                }
            };
        }
        return indeterminate;
    }

    /**
     * ************************************************************************
     * *
     * Methods                                                                 *
     * *
     * ************************************************************************
     */

    public final double getProgress() {
        return progress == null ? INDETERMINATE_PROGRESS : progress.get();
    }

    /**
     * ************************************************************************
     * *
     * Stylesheet Handling                                                     *
     * *
     * ************************************************************************
     */

    public final void setProgress(double value) {
        progressProperty().set(value);
    }

    public final DoubleProperty progressProperty() {
        if (progress == null) {
            progress = new DoublePropertyBase(-1.0) {
                @Override
                protected void invalidated() {
                    setIndeterminate(getProgress() < 0.0);
                }


                @Override
                public Object getBean() {
                    return TxConfidenceIndicator.this;
                }


                @Override
                public String getName() {
                    return "progress";
                }
            };
        }
        return progress;
    }

    /**
     * {@inheritDoc}
     */

    @Override
    protected Skin<?> createDefaultSkin() {
        return new StaticProgressIndicatorSkin(this);
    }

    /**
     * Most Controls return true for focusTraversable, so Control overrides
     * this method to return true, but ProgressIndicator returns false for
     * focusTraversable's initial value; hence the override of the override.
     * This method is called from CSS code to get the correct initial value.
     */
    @Deprecated
    @SuppressWarnings("deprecation")
    protected /*do not make final*/ Boolean impl_cssGetFocusTraversableInitialValue() {
        return Boolean.FALSE;
    }


}
