package bisq.desktop.components;

import bisq.desktop.components.controls.BisqJfxTextField;
import bisq.desktop.components.controls.skin.BisqTextFieldSkin;

import javafx.scene.control.Skin;

public class BisqTextField extends BisqJfxTextField {

    public BisqTextField(String value) {
        super(value);
    }

    public BisqTextField() {
        super();
    }

    @Override
    protected Skin<?> createDefaultSkin() {
        return new BisqTextFieldSkin(this);
    }
}
