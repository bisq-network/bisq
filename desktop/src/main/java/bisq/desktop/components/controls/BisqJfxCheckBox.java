package bisq.desktop.components.controls;

import bisq.desktop.components.controls.skin.BisqCheckBoxSkin;

import javafx.scene.control.CheckBox;
import javafx.scene.control.Skin;

/** Drop-in replacement for {@code com.jfoenix.controls.JFXCheckBox} — keeps the {@code .jfx-check-box} style class. */
public class BisqJfxCheckBox extends CheckBox {

    public BisqJfxCheckBox() {
        super();
        getStyleClass().add("jfx-check-box");
    }

    public BisqJfxCheckBox(String text) {
        super(text);
        getStyleClass().add("jfx-check-box");
    }

    @Override
    protected Skin<?> createDefaultSkin() {
        return new BisqCheckBoxSkin(this);
    }
}
