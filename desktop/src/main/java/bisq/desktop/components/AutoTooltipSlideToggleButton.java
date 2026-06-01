package bisq.desktop.components;

import bisq.desktop.components.controls.BisqJfxToggleButton;
import bisq.desktop.components.controls.skin.BisqToggleButtonSkin;

import javafx.scene.control.Skin;

import static bisq.desktop.components.TooltipUtil.showTooltipIfTruncated;

public class AutoTooltipSlideToggleButton extends BisqJfxToggleButton {
    public AutoTooltipSlideToggleButton() {
        super();
    }

    @Override
    protected Skin<?> createDefaultSkin() {
        return new AutoTooltipSlideToggleButton.AutoTooltipSlideToggleButtonSkin(this);
    }

    private class AutoTooltipSlideToggleButtonSkin extends BisqToggleButtonSkin {
        public AutoTooltipSlideToggleButtonSkin(BisqJfxToggleButton toggleButton) {
            super(toggleButton);
        }

        @Override
        protected void layoutChildren(double x, double y, double w, double h) {
            super.layoutChildren(x, y, w, h);
            showTooltipIfTruncated(this, getSkinnable());
        }
    }
}
