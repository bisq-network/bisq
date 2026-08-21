package bisq.desktop.components.controls;

import bisq.desktop.components.controls.skin.BisqSpinnerSkin;

import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.Skin;

/**
 * Drop-in replacement for {@code com.jfoenix.controls.JFXSpinner}.
 * Stock {@link ProgressIndicator} renders as a dot-ring when indeterminate; we install a
 * Material-style rotating arc skin instead so it matches jfoenix visually.
 */
public class BisqJfxSpinner extends ProgressIndicator {

    public BisqJfxSpinner() {
        super(-1);
        init();
    }

    public BisqJfxSpinner(double progress) {
        super(progress);
        init();
    }

    private void init() {
        getStyleClass().add("jfx-spinner");
    }

    @Override
    protected Skin<?> createDefaultSkin() {
        return new BisqSpinnerSkin(this);
    }
}
