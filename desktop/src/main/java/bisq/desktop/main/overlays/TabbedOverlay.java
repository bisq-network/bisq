package bisq.desktop.main.overlays;

import bisq.desktop.components.controls.BisqJfxTabPane;

import javafx.scene.layout.Region;

public abstract class TabbedOverlay<T extends TabbedOverlay<T>> extends Overlay<T> {

    protected BisqJfxTabPane tabPane;

    protected void createTabPane() {
        this.tabPane = new BisqJfxTabPane();
        tabPane.setMinWidth(width);
    }

    @Override
    protected Region getRootContainer() {
        return tabPane;
    }
}
