package bisq.desktop.components.controls;

import javafx.beans.property.BooleanProperty;

/**
 * Marker interface for controls that support a floating prompt label (jfoenix's
 * {@code setLabelFloat(boolean)} feature). Lets the matching skin discover the flag without
 * coupling to a concrete subclass.
 */
public interface LabelFloatable {
    boolean isLabelFloat();

    void setLabelFloat(boolean v);

    BooleanProperty labelFloatProperty();
}
