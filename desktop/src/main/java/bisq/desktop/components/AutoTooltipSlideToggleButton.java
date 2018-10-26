package bisq.desktop.components;

import com.jfoenix.controls.JFXToggleButton;
import com.jfoenix.skins.JFXToggleButtonSkin;

import javafx.scene.control.Skin;

import static bisq.desktop.components.TooltipUtil.showTooltipIfTruncated;

public class AutoTooltipSlideToggleButton extends JFXToggleButton {
    public AutoTooltipSlideToggleButton() {
        super();
    }

    @Override
    protected Skin<?> createDefaultSkin() {
        return new AutoTooltipSlideToggleButton.AutoTooltipSlideToggleButtonSkin(this);
    }

    private class AutoTooltipSlideToggleButtonSkin extends JFXToggleButtonSkin {
        public AutoTooltipSlideToggleButtonSkin(JFXToggleButton toggleButton) {
            super(toggleButton);
        }

        @Override
        protected void layoutChildren(double x, double y, double w, double h) {
            super.layoutChildren(x, y, w, h);
            showTooltipIfTruncated(this, getSkinnable());
        }
    }
}
