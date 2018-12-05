package bisq.desktop.components;

import com.jfoenix.controls.JFXTextArea;

import javafx.scene.control.Skin;

public class BisqTextArea extends JFXTextArea {
    @Override
    protected Skin<?> createDefaultSkin() {
        return new JFXTextAreaSkinBisqStyle(this);
    }
}
