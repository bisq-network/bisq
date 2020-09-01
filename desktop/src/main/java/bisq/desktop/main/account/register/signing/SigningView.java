/*
 * This file is part of Bisq.
 *
 * Bisq is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * Bisq is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Bisq. If not, see <http://www.gnu.org/licenses/>.
 */

package bisq.desktop.main.account.register.signing;


import bisq.desktop.common.view.ActivatableView;
import bisq.desktop.common.view.FxmlView;
import bisq.desktop.main.overlays.windows.SignPaymentAccountsWindow;
import bisq.desktop.main.overlays.windows.SignSpecificWitnessWindow;
import bisq.desktop.main.overlays.windows.SignUnsignedPubKeysWindow;

import bisq.common.util.Utilities;

import javax.inject.Inject;

import javafx.scene.Scene;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.AnchorPane;

import javafx.event.EventHandler;

@FxmlView
public class SigningView extends ActivatableView<AnchorPane, Void> {

    private final SignPaymentAccountsWindow signPaymentAccountsWindow;
    private final SignSpecificWitnessWindow signSpecificWitnessWindow;
    private final SignUnsignedPubKeysWindow signUnsignedPubKeysWindow;
    private EventHandler<KeyEvent> keyEventEventHandler;
    private Scene scene;

    @Inject
    public SigningView(SignPaymentAccountsWindow signPaymentAccountsWindow,
                       SignSpecificWitnessWindow signSpecificWitnessWindow,
                       SignUnsignedPubKeysWindow signUnsignedPubKeysWindow) {
        this.signPaymentAccountsWindow = signPaymentAccountsWindow;
        this.signSpecificWitnessWindow = signSpecificWitnessWindow;
        this.signUnsignedPubKeysWindow = signUnsignedPubKeysWindow;
        keyEventEventHandler = this::handleKeyPressed;
    }

    @Override
    protected void activate() {
        scene = root.getScene();
        if (scene != null)
            scene.addEventHandler(KeyEvent.KEY_RELEASED, keyEventEventHandler);
    }

    @Override
    protected void deactivate() {
        if (scene != null)
            scene.removeEventHandler(KeyEvent.KEY_RELEASED, keyEventEventHandler);
    }

    protected void handleKeyPressed(KeyEvent event) {
        if (Utilities.isAltOrCtrlPressed(KeyCode.S, event)) {
            signPaymentAccountsWindow.show();
        } else if (Utilities.isAltOrCtrlPressed(KeyCode.P, event)) {
            signSpecificWitnessWindow.show();
        } else if (Utilities.isAltOrCtrlPressed(KeyCode.O, event)) {
            signUnsignedPubKeysWindow.show();
        }
    }
}
