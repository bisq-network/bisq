package bisq.desktop.main.overlays;

import com.jfoenix.controls.JFXTabPane;

import javafx.scene.layout.Region;

public abstract class TabbedOverlay<T extends TabbedOverlay<T>> extends Overlay<T> {

    protected JFXTabPane tabPane;

    protected void createTabPane() {
        this.tabPane = new JFXTabPane();
        tabPane.setMinWidth(width);
    }

    @Override
    protected Region getRootContainer() {
        return tabPane;
    }
}
