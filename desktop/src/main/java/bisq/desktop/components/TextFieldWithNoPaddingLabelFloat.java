package bisq.desktop.components;

import com.jfoenix.controls.JFXTextField;

import javafx.scene.control.Skin;

public class TextFieldWithNoPaddingLabelFloat extends JFXTextField {

    @Override
    protected Skin<?> createDefaultSkin() {
        return new JFXTextFieldSkinBisqStyle<>(this, 0);
    }
}
