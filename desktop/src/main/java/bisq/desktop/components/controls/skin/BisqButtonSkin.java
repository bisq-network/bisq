package bisq.desktop.components.controls.skin;

import bisq.desktop.components.controls.BisqRippler;

import javafx.event.EventHandler;
import javafx.scene.control.Button;
import javafx.scene.control.skin.ButtonSkin;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Pane;
import javafx.scene.shape.Circle;

/**
 * Stock {@link ButtonSkin} + click ripple via {@link BisqRippler}.
 *
 * The overlay {@link Pane} is bound to the button's width/height so the ripple's local
 * coordinate space matches the button's. MouseEvent.getX/Y are already in button-local
 * coordinates, so the ripple lands exactly under the cursor.
 */
public class BisqButtonSkin extends ButtonSkin {

    private final Pane rippleOverlay;
    private Circle current;
    private final EventHandler<MouseEvent> pressHandler;
    private final EventHandler<MouseEvent> releaseHandler;

    public BisqButtonSkin(Button button) {
        super(button);
        rippleOverlay = BisqRippler.makeOverlay(button);
        getChildren().add(rippleOverlay);

        pressHandler = e -> {
            if (!button.isDisabled()) {
                current = BisqRippler.pressAt(rippleOverlay, button, e.getX(), e.getY());
            }
        };
        releaseHandler = e -> {
            if (current != null) {
                BisqRippler.release(rippleOverlay, current);
                current = null;
            }
        };
        button.addEventFilter(MouseEvent.MOUSE_PRESSED, pressHandler);
        button.addEventFilter(MouseEvent.MOUSE_RELEASED, releaseHandler);
    }

    @Override
    public void dispose() {
        Button b = getSkinnable();
        if (b != null) {
            b.removeEventFilter(MouseEvent.MOUSE_PRESSED, pressHandler);
            b.removeEventFilter(MouseEvent.MOUSE_RELEASED, releaseHandler);
        }
        BisqRippler.disposeOverlay(rippleOverlay);
        super.dispose();
    }
}
