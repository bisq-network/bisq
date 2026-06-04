package bisq.desktop.components.controls;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.geometry.Pos;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;

/**
 * Drop-in replacement for {@code com.jfoenix.controls.JFXBadge}.
 *
 * Renders a count/text label overlay positioned over a child {@link Node}.
 * Mirrors the jfoenix API used by bisq:
 *   - new JFXBadge(node)
 *   - new JFXBadge(node, Pos)
 *   - setText / textProperty
 *   - setEnabled / enabledProperty
 *   - refreshBadge() (no-op here; layout re-runs on text change automatically)
 */
public class BisqJfxBadge extends StackPane {

    private final Group badge = new Group();
    private final StackPane badgePane = new StackPane();
    private final Label badgeLabel = new Label();
    private final StringProperty text = new SimpleStringProperty("");
    private final BooleanProperty enabled = new SimpleBooleanProperty(true);

    private Node control;
    private Pos position = Pos.TOP_RIGHT;

    public BisqJfxBadge(Node control) {
        this(control, Pos.TOP_RIGHT);
    }

    public BisqJfxBadge(Node control, Pos position) {
        this.control = control;
        this.position = position;
        getStyleClass().add("jfx-badge");
        // Mirror the jfoenix JFXBadge node structure exactly:
        //   jfx-badge (StackPane) > Group > badge-pane (StackPane) > Label
        // The Group is essential: it is non-resizable, so the outer StackPane leaves
        // the badge-pane at its content size and floats it at the corner. Without it
        // the badge-pane is stretched to fill the cell (giant pill instead of a dot)
        // and the ".jfx-badge .badge-pane" CSS no longer reads as intended.
        badgePane.getStyleClass().add("badge-pane");
        badgeLabel.textProperty().bind(text);
        badgePane.getChildren().add(badgeLabel);
        badge.getChildren().add(badgePane);
        getChildren().addAll(control);

        text.addListener((o, oldV, newV) -> refreshBadge());
        enabled.addListener((o, oldV, newV) -> refreshBadge());
        refreshBadge();
    }

    public void refreshBadge() {
        getChildren().remove(badge);
        String t = text.get();
        if (enabled.get() && t != null && !t.isEmpty()) {
            StackPane.setAlignment(badge, position);
            getChildren().add(badge);
        }
    }

    public String getText() { return text.get(); }
    public void setText(String t) { text.set(t); }
    public StringProperty textProperty() { return text; }

    public boolean isEnabled() { return enabled.get(); }
    public void setEnabled(boolean v) { enabled.set(v); }
    public BooleanProperty enabledProperty() { return enabled; }

    public Node getControl() { return control; }

    public void setControl(Node node) {
        this.control = node;
        getChildren().setAll(node);
        refreshBadge();
    }

    public Pos getPosition() { return position; }
    public void setPosition(Pos position) {
        this.position = position;
        refreshBadge();
    }
}
