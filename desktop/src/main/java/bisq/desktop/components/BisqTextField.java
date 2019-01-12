package bisq.desktop.components;

import com.jfoenix.controls.JFXTextField;

import javafx.scene.control.Skin;

public class BisqTextField extends JFXTextField {

    public BisqTextField(String value) {
        super(value);
    }

    public BisqTextField() {
        super();
    }

    @Override
    protected Skin<?> createDefaultSkin() {
        return new JFXTextFieldSkinBisqStyle<>(this, 0);
    }
}
