package bisq.desktop.components.controls;

import bisq.desktop.components.controls.skin.BisqRadioButtonSkin;

import javafx.scene.control.RadioButton;
import javafx.scene.control.Skin;

/** Drop-in replacement for {@code com.jfoenix.controls.JFXRadioButton}. */
public class BisqJfxRadioButton extends RadioButton {

    public BisqJfxRadioButton() {
        super();
        getStyleClass().add("jfx-radio-button");
    }

    public BisqJfxRadioButton(String text) {
        super(text);
        getStyleClass().add("jfx-radio-button");
    }

    @Override
    protected Skin<?> createDefaultSkin() {
        return new BisqRadioButtonSkin(this);
    }
}
