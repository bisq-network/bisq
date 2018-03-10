package io.bisq.gui.components;

import com.sun.javafx.scene.control.skin.ToggleButtonSkin;
import javafx.scene.Node;
import javafx.scene.control.Skin;
import javafx.scene.control.ToggleButton;

import static io.bisq.gui.components.TooltipUtil.showTooltipIfTruncated;

public class AutoTooltipToggleButton extends ToggleButton {

    public AutoTooltipToggleButton() {
        super();
    }

    public AutoTooltipToggleButton(String text) {
        super(text);
    }

    public AutoTooltipToggleButton(String text, Node graphic) {
        super(text, graphic);
    }

    @Override
    protected Skin<?> createDefaultSkin() {
        return new AutoTooltipToggleButtonSkin(this);
    }

    private class AutoTooltipToggleButtonSkin extends ToggleButtonSkin {
        public AutoTooltipToggleButtonSkin(ToggleButton toggleButton) {
            super(toggleButton);
        }

        @Override
        protected void layoutChildren(double x, double y, double w, double h) {
            super.layoutChildren(x, y, w, h);
            showTooltipIfTruncated(this, getSkinnable());
        }
    }
}
