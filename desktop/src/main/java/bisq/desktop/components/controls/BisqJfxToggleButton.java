package bisq.desktop.components.controls;

import bisq.desktop.components.controls.skin.BisqToggleButtonSkin;

import javafx.beans.property.ObjectProperty;
import javafx.css.CssMetaData;
import javafx.css.SimpleStyleableObjectProperty;
import javafx.css.Styleable;
import javafx.css.StyleableObjectProperty;
import javafx.css.StyleableProperty;
import javafx.css.converter.PaintConverter;
import javafx.scene.control.Skin;
import javafx.scene.control.ToggleButton;
import javafx.scene.paint.Paint;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Drop-in replacement for {@code com.jfoenix.controls.JFXToggleButton}.
 *
 * Exposes a CSS-styleable {@code -jfx-toggle-color} (Paint) so per-instance overrides
 * (e.g. {@code #charts-legend-toggle0 { -jfx-toggle-color: ...; }} in bisq.css) keep working
 * after the jfoenix removal. {@link BisqToggleButtonSkin} consumes the value to colour the
 * selected-state thumb + track.
 */
public class BisqJfxToggleButton extends ToggleButton {

    private static final CssMetaData<BisqJfxToggleButton, Paint> TOGGLE_COLOR_META =
            new CssMetaData<>("-jfx-toggle-color", PaintConverter.getInstance(), null) {
                @Override
                public boolean isSettable(BisqJfxToggleButton control) {
                    return !control.toggleColor.isBound();
                }

                @Override
                public StyleableProperty<Paint> getStyleableProperty(BisqJfxToggleButton control) {
                    return control.toggleColor;
                }
            };

    private static final List<CssMetaData<? extends Styleable, ?>> CSS_META_DATA;

    static {
        List<CssMetaData<? extends Styleable, ?>> list =
                new ArrayList<>(ToggleButton.getClassCssMetaData());
        list.add(TOGGLE_COLOR_META);
        CSS_META_DATA = Collections.unmodifiableList(list);
    }

    private final StyleableObjectProperty<Paint> toggleColor =
            new SimpleStyleableObjectProperty<>(TOGGLE_COLOR_META, this, "toggleColor", null);

    public BisqJfxToggleButton() {
        super();
        getStyleClass().add("jfx-toggle-button");
    }

    public BisqJfxToggleButton(String text) {
        super(text);
        getStyleClass().add("jfx-toggle-button");
    }

    public ObjectProperty<Paint> toggleColorProperty() {
        return toggleColor;
    }

    public Paint getToggleColor() {
        return toggleColor.get();
    }

    public void setToggleColor(Paint color) {
        toggleColor.set(color);
    }

    public static List<CssMetaData<? extends Styleable, ?>> getClassCssMetaData() {
        return CSS_META_DATA;
    }

    @Override
    public List<CssMetaData<? extends Styleable, ?>> getControlCssMetaData() {
        return CSS_META_DATA;
    }

    @Override
    protected Skin<?> createDefaultSkin() {
        return new BisqToggleButtonSkin(this);
    }
}
