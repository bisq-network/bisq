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

package io.bisq.gui.components.indicator.skin;

import com.sun.javafx.css.converters.BooleanConverter;
import com.sun.javafx.css.converters.PaintConverter;
import com.sun.javafx.css.converters.SizeConverter;
import com.sun.javafx.scene.control.skin.BehaviorSkinBase;
import io.bisq.gui.components.indicator.TxConfidenceIndicator;
import io.bisq.gui.components.indicator.behavior.StaticProgressIndicatorBehavior;
import javafx.beans.InvalidationListener;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.ObjectProperty;
import javafx.css.*;
import javafx.geometry.Insets;
import javafx.geometry.NodeOrientation;
import javafx.scene.Node;
import javafx.scene.control.SkinBase;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.paint.Paint;
import javafx.scene.shape.Arc;
import javafx.scene.shape.ArcType;
import javafx.scene.shape.Circle;
import javafx.scene.transform.Scale;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

// TODO Copied form OpenJFX, check license issues and way how we integrated it
// We changed behaviour which was not exposed via APIs

public class StaticProgressIndicatorSkin extends BehaviorSkinBase<TxConfidenceIndicator,
        StaticProgressIndicatorBehavior<TxConfidenceIndicator>> {

    /**
     * ************************************************************************
     * *
     * UI SubComponents                                                        *
     * *
     * ************************************************************************
     */

    private IndeterminateSpinner spinner;
    /**
     * The number of segments in the spinner.
     */

    private final IntegerProperty indeterminateSegmentCount = new StyleableIntegerProperty(8) {

        @Override
        protected void invalidated() {
            if (spinner != null) {
                spinner.rebuild();
            }
        }


        @Override
        public Object getBean() {
            return StaticProgressIndicatorSkin.this;
        }


        @Override
        public String getName() {
            return "indeterminateSegmentCount";
        }


        @Override
        public CssMetaData<TxConfidenceIndicator, Number> getCssMetaData() {
            return StyleableProperties.INDETERMINATE_SEGMENT_COUNT;
        }
    };
    /**
     * True if the progress indicator should rotate as well as animate opacity.
     */

    private final BooleanProperty spinEnabled = new StyleableBooleanProperty(false) {
        @Override
        protected void invalidated() {
            if (spinner != null) {
                spinner.setSpinEnabled(get());
            }
        }


        @Override
        public CssMetaData<TxConfidenceIndicator, Boolean> getCssMetaData() {
            return StyleableProperties.SPIN_ENABLED;
        }


        @Override
        public Object getBean() {
            return StaticProgressIndicatorSkin.this;
        }


        @Override
        public String getName() {
            return "spinEnabled";
        }
    };

    private DeterminateIndicator determinateIndicator;
    /**
     * The colour of the progress segment.
     */

    private final ObjectProperty<Paint> progressColor = new StyleableObjectProperty<Paint>(null) {

        @Override
        public void set(Paint newProgressColor) {
            final Paint color = (newProgressColor instanceof Color) ? newProgressColor : null;
            super.set(color);
        }

        @Override
        protected void invalidated() {
            if (spinner != null) {
                spinner.setFillOverride(get());
            }
            if (determinateIndicator != null) {
                determinateIndicator.setFillOverride(get());
            }
        }


        @Override
        public Object getBean() {
            return StaticProgressIndicatorSkin.this;
        }


        @Override
        public String getName() {
            return "progressColorProperty";
        }


        @Override
        public CssMetaData<TxConfidenceIndicator, Paint> getCssMetaData() {
            return StyleableProperties.PROGRESS_COLOR;
        }
    };
    private boolean timeLineNulled = false;

    /**
     * ************************************************************************
     * *
     * Constructors                                                            *
     * *
     * ************************************************************************
     */
    @SuppressWarnings("deprecation")
    public StaticProgressIndicatorSkin(TxConfidenceIndicator control) {
        super(control, new StaticProgressIndicatorBehavior<>(control));

        InvalidationListener indeterminateListener = valueModel -> initialize();
        control.indeterminateProperty().addListener(indeterminateListener);

        InvalidationListener visibilityListener = valueModel -> {
            if (getSkinnable().isIndeterminate() && timeLineNulled && spinner == null) {
                timeLineNulled = false;
                spinner = new IndeterminateSpinner(
                        getSkinnable(), StaticProgressIndicatorSkin.this,
                        spinEnabled.get(), progressColor.get());
                getChildren().add(spinner);
            }

            if (spinner != null) {
                if (!(getSkinnable().impl_isTreeVisible() && getSkinnable().getScene() != null)) {
                    getChildren().remove(spinner);
                    spinner = null;
                    timeLineNulled = true;
                }
            }
        };
        control.visibleProperty().addListener(visibilityListener);
        control.parentProperty().addListener(visibilityListener);

        InvalidationListener sceneListener = valueModel -> {
            if (spinner != null) {
                if (getSkinnable().getScene() == null) {
                    getChildren().remove(spinner);
                    spinner = null;
                    timeLineNulled = true;
                }
            } else {
                if (getSkinnable().getScene() != null && getSkinnable().isIndeterminate()) {
                    timeLineNulled = false;
                    spinner = new IndeterminateSpinner(
                            getSkinnable(), StaticProgressIndicatorSkin.this,
                            spinEnabled.get(), progressColor.get());
                    getChildren().add(spinner);
                    getSkinnable().requestLayout();
                }
            }
        };
        control.sceneProperty().addListener(sceneListener);

        initialize();
        getSkinnable().requestLayout();
    }

    /**
     * @return The CssMetaData associated with this class, which may include the
     * CssMetaData of its super classes.
     */
    @SuppressWarnings("SameReturnValue")
    public static List<CssMetaData<? extends Styleable, ?>> getClassCssMetaData() {
        return StyleableProperties.STYLEABLES;
    }

    @SuppressWarnings("deprecation")
    private void initialize() {
        TxConfidenceIndicator control = getSkinnable();
        boolean isIndeterminate = control.isIndeterminate();
        if (isIndeterminate) {
            // clean up determinateIndicator
            determinateIndicator = null;
            // create spinner
            spinner = new IndeterminateSpinner(control, this, spinEnabled.get(), progressColor.get());
            getChildren().clear();
            getChildren().add(spinner);
        } else {
            // clean up after spinner
            if (spinner != null) {
                spinner = null;
            }
            // create determinateIndicator
            determinateIndicator =
                    new StaticProgressIndicatorSkin.DeterminateIndicator(control, this, progressColor.get());
            getChildren().clear();
            getChildren().add(determinateIndicator);
        }
    }

    @Override
    public void dispose() {
        super.dispose();
        if (spinner != null) {
            spinner = null;
        }
    }

    @Override
    protected void layoutChildren(final double x, final double y, final double w, final double h) {
        if (spinner != null && getSkinnable().isIndeterminate()) {
            spinner.layoutChildren();
            spinner.resizeRelocate(0, 0, w, h);
        } else if (determinateIndicator != null) {
            determinateIndicator.layoutChildren();
            determinateIndicator.resizeRelocate(0, 0, w, h);
        }
    }

    public Paint getProgressColor() {
        return progressColor.get();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<CssMetaData<? extends Styleable, ?>> getCssMetaData() {
        return getClassCssMetaData();
    }

    // *********** Stylesheet Handling *****************************************

    /**
     * ************************************************************************
     * *
     * DeterminateIndicator                                                    *
     * *
     * ************************************************************************
     */

    @SuppressWarnings({"SameReturnValue", "UnusedParameters"})
    static class DeterminateIndicator extends Region {
        //private double textGap = 2.0F;


        private final TxConfidenceIndicator control;
        //private Text text;

        private final StackPane indicator;

        private final StackPane progress;

        private final StackPane tick;

        private final Arc arcShape;

        private final Circle indicatorCircle;
        // only update progress text on whole percentages
        private int intProgress;
        // only update pie arc to nearest degree
        private int degProgress;

        public DeterminateIndicator(TxConfidenceIndicator control, StaticProgressIndicatorSkin s,
                                    Paint fillOverride) {
            this.control = control;

            getStyleClass().add("determinate-indicator");

            intProgress = (int) Math.round(control.getProgress() * 100.0);
            degProgress = (int) (360 * control.getProgress());

            InvalidationListener progressListener = valueModel -> updateProgress();
            control.progressProperty().addListener(progressListener);

            getChildren().clear();

            // The circular background for the progress pie piece
            indicator = new StackPane();
            indicator.setScaleShape(false);
            indicator.setCenterShape(false);
            indicator.getStyleClass().setAll("indicator");
            indicatorCircle = new Circle();
            indicator.setShape(indicatorCircle);

            // The shape for our progress pie piece
            arcShape = new Arc();
            arcShape.setType(ArcType.ROUND);
            arcShape.setStartAngle(90.0F);

            // Our progress pie piece
            progress = new StackPane();
            progress.getStyleClass().setAll("progress");
            progress.setScaleShape(false);
            progress.setCenterShape(false);
            progress.setShape(arcShape);
            progress.getChildren().clear();
            setFillOverride(fillOverride);

            // The check mark that's drawn at 100%
            tick = new StackPane();
            tick.getStyleClass().setAll("tick");

            getChildren().setAll(indicator, progress, /*text,*/ tick);
            updateProgress();
        }

        private void setFillOverride(Paint fillOverride) {
            if (fillOverride instanceof Color) {
                Color c = (Color) fillOverride;
                progress.setStyle("-fx-background-color: rgba(" + ((int) (255 * c.getRed())) + "," +
                        "" + ((int) (255 * c.getGreen())) + "," + ((int) (255 * c.getBlue())) + "," +
                        "" + c.getOpacity() + ");");
            } else {
                progress.setStyle(null);
            }
        }

        //@Override
        public boolean isAutomaticallyMirrored() {
            // This is used instead of setting NodeOrientation,
            // allowing the Text node to inherit the current
            // orientation.
            return false;
        }

        private void updateProgress() {
            intProgress = (int) Math.round(control.getProgress() * 100.0);
            // text.setText((control.getProgress() >= 1) ? (DONE) : ("" + intProgress + "%"));

            degProgress = (int) (360 * control.getProgress());
            arcShape.setLength(-degProgress);
            indicator.setOpacity(control.getProgress() == 0 ? 0 : 1);
            requestLayout();
        }

        @Override
        protected void layoutChildren() {
            // Position and size the circular background
            //double doneTextHeight = doneText.getLayoutBounds().getHeight();
            final Insets controlInsets = control.getInsets();
            final double left = snapSize(controlInsets.getLeft());
            final double right = snapSize(controlInsets.getRight());
            final double top = snapSize(controlInsets.getTop());
            final double bottom = snapSize(controlInsets.getBottom());

            /*
            ** use the min of width, or height, keep it a circle
            */
            final double areaW = control.getWidth() - left - right;
            final double areaH = control.getHeight() - top - bottom /*- textGap - doneTextHeight*/;
            final double radiusW = areaW / 2;
            final double radiusH = areaH / 2;
            final double radius = Math.round(Math.min(radiusW, radiusH)); // use round instead of floor
            final double centerX = snapPosition(left + radiusW);
            final double centerY = snapPosition(top + radius);

            // find radius that fits inside radius - insetsPadding
            final Insets indicatorInsets = indicator.getInsets();
            final double iLeft = snapSize(indicatorInsets.getLeft());
            final double iRight = snapSize(indicatorInsets.getRight());
            final double iTop = snapSize(indicatorInsets.getTop());
            final double iBottom = snapSize(indicatorInsets.getBottom());
            final double progressRadius = snapSize(Math.min(Math.min(radius - iLeft, radius - iRight),
                    Math.min(radius - iTop, radius - iBottom)));

            indicatorCircle.setRadius(radius);
            indicator.setLayoutX(centerX);
            indicator.setLayoutY(centerY);

            arcShape.setRadiusX(progressRadius);
            arcShape.setRadiusY(progressRadius);
            progress.setLayoutX(centerX);
            progress.setLayoutY(centerY);

            // find radius that fits inside progressRadius - progressInsets
            final Insets progressInsets = progress.getInsets();
            final double pLeft = snapSize(progressInsets.getLeft());
            final double pRight = snapSize(progressInsets.getRight());
            final double pTop = snapSize(progressInsets.getTop());
            final double pBottom = snapSize(progressInsets.getBottom());
            final double indicatorRadius = snapSize(Math.min(Math.min(progressRadius - pLeft,
                    progressRadius - pRight), Math.min(progressRadius - pTop, progressRadius - pBottom)));

            // find size of spare box that fits inside indicator radius
            double squareBoxHalfWidth = Math.ceil(Math.sqrt((indicatorRadius * indicatorRadius) / 2));
            // double squareBoxHalfWidth2 = indicatorRadius * (Math.sqrt(2) / 2);

            tick.setLayoutX(centerX - squareBoxHalfWidth);
            tick.setLayoutY(centerY - squareBoxHalfWidth);
            tick.resize(squareBoxHalfWidth + squareBoxHalfWidth, squareBoxHalfWidth + squareBoxHalfWidth);
            tick.setVisible(control.getProgress() >= 1);
        }

        @Override
        protected double computePrefWidth(double height) {
            final Insets controlInsets = control.getInsets();
            final double left = snapSize(controlInsets.getLeft());
            final double right = snapSize(controlInsets.getRight());
            final Insets indicatorInsets = indicator.getInsets();
            final double iLeft = snapSize(indicatorInsets.getLeft());
            final double iRight = snapSize(indicatorInsets.getRight());
            final double iTop = snapSize(indicatorInsets.getTop());
            final double iBottom = snapSize(indicatorInsets.getBottom());
            final double indicatorMax = snapSize(Math.max(Math.max(iLeft, iRight), Math.max(iTop, iBottom)));
            final Insets progressInsets = progress.getInsets();
            final double pLeft = snapSize(progressInsets.getLeft());
            final double pRight = snapSize(progressInsets.getRight());
            final double pTop = snapSize(progressInsets.getTop());
            final double pBottom = snapSize(progressInsets.getBottom());
            final double progressMax = snapSize(Math.max(Math.max(pLeft, pRight), Math.max(pTop, pBottom)));
            final Insets tickInsets = tick.getInsets();
            final double tLeft = snapSize(tickInsets.getLeft());
            final double tRight = snapSize(tickInsets.getRight());
            final double indicatorWidth = indicatorMax + progressMax + tLeft + tRight + progressMax + indicatorMax;
            return left + indicatorWidth + /*Math.max(indicatorWidth, doneText.getLayoutBounds().getWidth()) + */right;
        }

        @Override
        protected double computePrefHeight(double width) {
            final Insets controlInsets = control.getInsets();
            final double top = snapSize(controlInsets.getTop());
            final double bottom = snapSize(controlInsets.getBottom());
            final Insets indicatorInsets = indicator.getInsets();
            final double iLeft = snapSize(indicatorInsets.getLeft());
            final double iRight = snapSize(indicatorInsets.getRight());
            final double iTop = snapSize(indicatorInsets.getTop());
            final double iBottom = snapSize(indicatorInsets.getBottom());
            final double indicatorMax = snapSize(Math.max(Math.max(iLeft, iRight), Math.max(iTop, iBottom)));
            final Insets progressInsets = progress.getInsets();
            final double pLeft = snapSize(progressInsets.getLeft());
            final double pRight = snapSize(progressInsets.getRight());
            final double pTop = snapSize(progressInsets.getTop());
            final double pBottom = snapSize(progressInsets.getBottom());
            final double progressMax = snapSize(Math.max(Math.max(pLeft, pRight), Math.max(pTop, pBottom)));
            final Insets tickInsets = tick.getInsets();
            final double tTop = snapSize(tickInsets.getTop());
            final double tBottom = snapSize(tickInsets.getBottom());
            final double indicatorHeight = indicatorMax + progressMax + tTop + tBottom + progressMax + indicatorMax;
            return top + indicatorHeight /*+ textGap  + doneText.getLayoutBounds().getHeight()*/ + bottom;
        }

        @Override
        protected double computeMaxWidth(double height) {
            return computePrefWidth(height);
        }

        @Override
        protected double computeMaxHeight(double width) {
            return computePrefHeight(width);
        }
    }

    /**
     * ************************************************************************
     * *
     * IndeterminateSpinner                                                    *
     * *
     * ************************************************************************
     */

    @SuppressWarnings({"ConstantConditions", "MismatchedQueryAndUpdateOfCollection"})
    static class IndeterminateSpinner extends Region {
        private final TxConfidenceIndicator control;
        private final StaticProgressIndicatorSkin skin;

        private final IndicatorPaths pathsG;

        private final List<Double> opacities = new ArrayList<>();
        private boolean spinEnabled = false;

        private Paint fillOverride = null;

        public IndeterminateSpinner(TxConfidenceIndicator control, StaticProgressIndicatorSkin s,
                                    boolean spinEnabled, Paint fillOverride) {
            this.control = control;
            this.skin = s;
            this.spinEnabled = spinEnabled;
            this.fillOverride = fillOverride;

            setNodeOrientation(NodeOrientation.LEFT_TO_RIGHT);
            getStyleClass().setAll("spinner");

            pathsG = new IndicatorPaths(this);
            getChildren().add(pathsG);

            rebuild();
        }

        public void setFillOverride(Paint fillOverride) {
            this.fillOverride = fillOverride;
            rebuild();
        }

        public void setSpinEnabled(boolean spinEnabled) {
            this.spinEnabled = spinEnabled;
        }

        @Override
        protected void layoutChildren() {
            Insets controlInsets = control.getInsets();
            final double w = control.getWidth() - controlInsets.getLeft() - controlInsets.getRight();
            final double h = control.getHeight() - controlInsets.getTop() - controlInsets.getBottom();
            final double prefW = pathsG.prefWidth(-1);
            final double prefH = pathsG.prefHeight(-1);
            double scaleX = w / prefW;
            double scale = scaleX;
            if ((scaleX * prefH) > h) {
                scale = h / prefH;
            }
            double indicatorW = prefW * scale - 3;
            double indicatorH = prefH * scale - 3;
            pathsG.resizeRelocate((w - indicatorW) / 2, (h - indicatorH) / 2, indicatorW, indicatorH);
        }

        private void rebuild() {
            // update indeterminate indicator
            final int segments = skin.indeterminateSegmentCount.get();
            opacities.clear();
            pathsG.getChildren().clear();
            final double step = 0.8 / (segments - 1);
            for (int i = 0; i < segments; i++) {
                Region region = new Region();
                region.setScaleShape(false);
                region.setCenterShape(false);
                region.getStyleClass().addAll("segment", "segment" + i);
                if (fillOverride instanceof Color) {
                    Color c = (Color) fillOverride;
                    region.setStyle("-fx-background-color: rgba(" + ((int) (255 * c.getRed())) + "," +
                            "" + ((int) (255 * c.getGreen())) + "," + ((int) (255 * c.getBlue())) + "," +
                            "" + c.getOpacity() + ");");
                } else {
                    region.setStyle(null);
                }
                double opacity = Math.min(1, i * step);
                opacities.add(opacity);
                region.setOpacity(opacity);
                pathsG.getChildren().add(region);
            }
        }

        @SuppressWarnings("deprecation")
        private class IndicatorPaths extends Pane {
            final IndeterminateSpinner piSkin;

            IndicatorPaths(IndeterminateSpinner pi) {
                super();
                piSkin = pi;
            }

            @Override
            protected double computePrefWidth(double height) {
                double w = 0;
                for (Node child : getChildren()) {
                    if (child instanceof Region) {
                        Region region = (Region) child;
                        if (region.getShape() != null) {
                            w = Math.max(w, region.getShape().getLayoutBounds().getMaxX());
                        } else {
                            w = Math.max(w, region.prefWidth(height));
                        }
                    }
                }
                return w;
            }

            @Override
            protected double computePrefHeight(double width) {
                double h = 0;
                for (Node child : getChildren()) {
                    if (child instanceof Region) {
                        Region region = (Region) child;
                        if (region.getShape() != null) {
                            h = Math.max(h, region.getShape().getLayoutBounds().getMaxY());
                        } else {
                            h = Math.max(h, region.prefHeight(width));
                        }
                    }
                }
                return h;
            }

            @Override
            protected void layoutChildren() {
                // calculate scale
                double scale = getWidth() / computePrefWidth(-1);
                getChildren().stream().filter(child -> child instanceof Region).forEach(child -> {
                    Region region = (Region) child;
                    if (region.getShape() != null) {
                        region.resize(region.getShape().getLayoutBounds().getMaxX(),
                                region.getShape().getLayoutBounds().getMaxY());
                        region.getTransforms().setAll(new Scale(scale, scale, 0, 0));
                    } else {
                        region.autosize();
                    }
                });
            }
        }
    }

    /**
     * Super-lazy instantiation pattern from Bill Pugh.
     */
    @SuppressWarnings({"deprecation", "unchecked", "ConstantConditions"})
    private static class StyleableProperties {
        public static final List<CssMetaData<? extends Styleable, ?>> STYLEABLES;

        private static final CssMetaData<TxConfidenceIndicator, Paint> PROGRESS_COLOR =
                new CssMetaData<TxConfidenceIndicator, Paint>(
                        "-fx-progress-color", PaintConverter.getInstance(), null) {

                    @Override
                    public boolean isSettable(TxConfidenceIndicator n) {
                        final StaticProgressIndicatorSkin skin = (StaticProgressIndicatorSkin) n.getSkin();
                        return skin.progressColor == null || !skin.progressColor.isBound();
                    }


                    @Override
                    public StyleableProperty<Paint> getStyleableProperty(TxConfidenceIndicator n) {
                        final StaticProgressIndicatorSkin skin = (StaticProgressIndicatorSkin) n.getSkin();
                        return (StyleableProperty<Paint>) skin.progressColor;
                    }
                };


        private static final CssMetaData<TxConfidenceIndicator, Number> INDETERMINATE_SEGMENT_COUNT =
                new CssMetaData<TxConfidenceIndicator, Number>(
                        "-fx-indeterminate-segment-count", SizeConverter.getInstance(), 8) {

                    @Override
                    public void set(TxConfidenceIndicator node, Number value, StyleOrigin origin) {
                        super.set(node, value.intValue(), origin);
                    }

                    @Override
                    public boolean isSettable(TxConfidenceIndicator n) {
                        final StaticProgressIndicatorSkin skin = (StaticProgressIndicatorSkin) n.getSkin();
                        return skin.indeterminateSegmentCount == null || !skin.indeterminateSegmentCount.isBound();
                    }


                    @Override
                    public StyleableProperty<Number> getStyleableProperty(TxConfidenceIndicator n) {
                        final StaticProgressIndicatorSkin skin = (StaticProgressIndicatorSkin) n.getSkin();
                        return (StyleableProperty<Number>) skin.indeterminateSegmentCount;
                    }
                };

        private static final CssMetaData<TxConfidenceIndicator, Boolean> SPIN_ENABLED = new
                CssMetaData<TxConfidenceIndicator, Boolean>("-fx-spin-enabled",
                        BooleanConverter.getInstance(),
                        Boolean.FALSE) {

                    @Override
                    public boolean isSettable(TxConfidenceIndicator node) {
                        final StaticProgressIndicatorSkin skin = (StaticProgressIndicatorSkin) node.getSkin();
                        return skin.spinEnabled == null || !skin.spinEnabled.isBound();
                    }


                    @Override
                    public StyleableProperty<Boolean> getStyleableProperty(TxConfidenceIndicator node) {
                        final StaticProgressIndicatorSkin skin = (StaticProgressIndicatorSkin) node.getSkin();
                        return (StyleableProperty<Boolean>) skin.spinEnabled;
                    }
                };

        static {
            final List<CssMetaData<? extends Styleable, ?>> styleables =
                    new ArrayList<>(SkinBase.getClassCssMetaData());
            styleables.add(PROGRESS_COLOR);
            styleables.add(INDETERMINATE_SEGMENT_COUNT);
            styleables.add(SPIN_ENABLED);
            STYLEABLES = Collections.unmodifiableList(styleables);
        }
    }

}
