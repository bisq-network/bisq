package io.bisq.gui.components;

import com.sun.javafx.scene.control.skin.LabelSkin;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.Skin;
import javafx.scene.control.Tooltip;
import javafx.scene.text.Text;

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
        private final Label truncateToFitLabel;

        public AutoTooltipLabelSkin(Label label) {
            super(label);
            this.truncateToFitLabel = label;
        }

        @Override
        protected void layoutChildren(double x, double y, double w, double h) {
            super.layoutChildren(x, y, w, h);
            for (Node node : getChildren()) {
                if (node instanceof Text) {
                    String displayedText = ((Text) node).getText();
                    if (displayedText.equals(truncateToFitLabel.getText())) {
                        truncateToFitLabel.setTooltip(null);
                    } else {
                        truncateToFitLabel.setTooltip(new Tooltip(truncateToFitLabel.getText()));
                    }
                }
            }
        }
    }
}
