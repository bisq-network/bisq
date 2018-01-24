package io.bisq.gui.components;

import com.sun.javafx.scene.control.skin.LabelSkin;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.text.Text;

import static io.bisq.gui.components.TooltipUtil.showTooltipIfTruncated;

public class AutoTooltipLabel extends Label {

    public AutoTooltipLabel(){
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
