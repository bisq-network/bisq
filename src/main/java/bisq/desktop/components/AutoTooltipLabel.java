package bisq.desktop.components;

import com.sun.javafx.scene.control.skin.LabelSkin;
import javafx.scene.control.Label;
import javafx.scene.control.Skin;

import static bisq.desktop.components.TooltipUtil.showTooltipIfTruncated;

public class AutoTooltipLabel extends Label {

    public AutoTooltipLabel() {
        super();
    }

    public AutoTooltipLabel(String text) {
        super(text);
    }

    @Override
    protected Skin<?> createDefaultSkin() {
        return new AutoTooltipLabelSkin(this);
    }

    private class AutoTooltipLabelSkin extends LabelSkin {

        public AutoTooltipLabelSkin(Label label) {
            super(label);
        }

        @Override
        protected void layoutChildren(double x, double y, double w, double h) {
            super.layoutChildren(x, y, w, h);
            showTooltipIfTruncated(this, getSkinnable());
        }
    }
}
