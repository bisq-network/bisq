package bisq.desktop.components.controls;

import bisq.desktop.components.controls.skin.BisqProgressBarSkin;

import javafx.scene.control.ProgressBar;
import javafx.scene.control.Skin;

/** Drop-in replacement for {@code com.jfoenix.controls.JFXProgressBar}. */
public class BisqJfxProgressBar extends ProgressBar {

    public BisqJfxProgressBar() {
        super();
        getStyleClass().add("jfx-progress-bar");
    }

    public BisqJfxProgressBar(double progress) {
        super(progress);
        getStyleClass().add("jfx-progress-bar");
    }

    @Override
    protected Skin<?> createDefaultSkin() {
        return new BisqProgressBarSkin(this);
    }

    // ----- Secondary-progress stub (jfoenix API used by SeparatedPhaseBars) ---------------------
    // Stock ProgressBar has no secondary track. The visual portion is omitted for now; we keep
    // the property so callers continue to compile until the skin renders a second bar.
    private final javafx.beans.property.DoubleProperty secondaryProgress =
            new javafx.beans.property.SimpleDoubleProperty(0);

    public final double getSecondaryProgress() { return secondaryProgress.get(); }
    public final void setSecondaryProgress(double v) { secondaryProgress.set(v); }
    public final javafx.beans.property.DoubleProperty secondaryProgressProperty() { return secondaryProgress; }
}
