package bisq.desktop.components;

import com.sun.javafx.scene.control.skin.RadioButtonSkin;
import javafx.scene.control.RadioButton;
import javafx.scene.control.Skin;

import static bisq.desktop.components.TooltipUtil.showTooltipIfTruncated;

public class AutoTooltipRadioButton extends RadioButton {

    public AutoTooltipRadioButton() {
        super();
    }

    public AutoTooltipRadioButton(String text) {
        super(text);
    }

    @Override
    protected Skin<?> createDefaultSkin() {
        return new AutoTooltipRadioButtonSkin(this);
    }

    private class AutoTooltipRadioButtonSkin extends RadioButtonSkin {
        public AutoTooltipRadioButtonSkin(RadioButton radioButton) {
            super(radioButton);
        }

        @Override
        protected void layoutChildren(double x, double y, double w, double h) {
            super.layoutChildren(x, y, w, h);
            showTooltipIfTruncated(this, getSkinnable());
        }
    }
}
