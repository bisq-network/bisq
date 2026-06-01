package bisq.desktop.components;

import bisq.desktop.components.controls.BisqJfxTextArea;
import bisq.desktop.components.controls.skin.BisqTextAreaSkin;

import javafx.scene.control.Skin;

public class BisqTextArea extends BisqJfxTextArea {
    @Override
    protected Skin<?> createDefaultSkin() {
        return new BisqTextAreaSkin(this);
    }
}
