package bisq.desktop.components.controls;

import bisq.desktop.components.controls.skin.BisqButtonSkin;

import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Skin;

/**
 * Drop-in replacement for {@code com.jfoenix.controls.JFXButton}.
 * Same CSS surface ({@code .jfx-button}) so existing stylesheets match unchanged.
 *
 * NOTE: jfoenix's click-time ripple animation is not reimplemented here. The fixture-driven
 * pixel diff will surface whether it matters; if it does, add via {@link BisqRippler}.
 */
public class BisqJfxButton extends Button {

    public BisqJfxButton() {
        super();
        getStyleClass().add("jfx-button");
    }

    public BisqJfxButton(String text) {
        super(text);
        getStyleClass().add("jfx-button");
    }

    public BisqJfxButton(String text, Node graphic) {
        super(text, graphic);
        getStyleClass().add("jfx-button");
    }

    @Override
    protected Skin<?> createDefaultSkin() {
        return new BisqButtonSkin(this);
    }
}
