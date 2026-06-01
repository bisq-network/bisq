package bisq.desktop.components.controls;

import bisq.desktop.components.controls.skin.BisqTabPaneSkin;

import javafx.scene.control.Skin;
import javafx.scene.control.TabPane;

/** Drop-in replacement for {@code com.jfoenix.controls.JFXTabPane}. */
public class BisqJfxTabPane extends TabPane {

    public BisqJfxTabPane() {
        super();
        getStyleClass().add("jfx-tab-pane");
    }

    @Override
    protected Skin<?> createDefaultSkin() {
        return new BisqTabPaneSkin(this);
    }
}
