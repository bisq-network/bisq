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

package bisq.desktop.main.overlays.windows.supporttool;

import bisq.desktop.components.AutoTooltipButton;
import bisq.desktop.main.overlays.Overlay;

import bisq.core.btc.setup.WalletsSetup;
import bisq.core.btc.wallet.TradeWalletService;
import bisq.core.btc.wallet.WalletsManager;
import bisq.core.support.dispute.mediation.MediationManager;

import bisq.network.p2p.P2PService;

import javax.inject.Inject;

import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;

import javafx.geometry.Insets;

import javafx.collections.ObservableList;

import java.util.ArrayList;
import java.util.List;

public class SupportToolWindow extends Overlay<SupportToolWindow> {
    public interface MasterCallback {     // for when a tab needs to call us back
        void hideAllPanes();
    }

    private final TradeWalletService tradeWalletService;
    private final P2PService p2PService;
    private final MediationManager mediationManager;
    private final WalletsSetup walletsSetup;
    private final WalletsManager walletsManager;

    // one gridpane for each "tab"
    List<CommonPane> subPanes = new ArrayList<>();
    InputsPane inputsGridPane;  // special, as its needed by other panes

    @Inject
    public SupportToolWindow(TradeWalletService tradeWalletService,
                             P2PService p2PService,
                             MediationManager mediationManager,
                             WalletsSetup walletsSetup,
                             WalletsManager walletsManager) {
        this.tradeWalletService = tradeWalletService;
        this.p2PService = p2PService;
        this.mediationManager = mediationManager;
        this.walletsSetup = walletsSetup;
        this.walletsManager = walletsManager;
        type = Type.Attention;
    }

    public void show() {
        if (headLine == null) {
            headLine = "Support Tool"; // We dont translate here as it is for dev only purpose
        }

        width = 1068;
        createGridPane();
        addHeadLine();
        addContent();
        addButtons();
        applyStyles();
        display();
    }

    @Override
    protected void setupKeyHandler(Scene scene) {
        if (!hideCloseButton) {
            scene.setOnKeyPressed(e -> {
                if (e.getCode() == KeyCode.ESCAPE) {
                    e.consume();
                    doClose();
                }
            });
        }
    }

    @Override
    protected void cleanup() {
        for (CommonPane pane : subPanes) {
            pane.cleanup();
        }
        super.cleanup();
    }

    @Override
    protected void createGridPane() {
        gridPane = new GridPane();
        gridPane.setHgap(15);
        gridPane.setVgap(15);
        gridPane.setPadding(new Insets(64, 64, 64, 64));
        gridPane.setPrefWidth(width);
        ColumnConstraints columnConstraints1 = new ColumnConstraints();
        ColumnConstraints columnConstraints2 = new ColumnConstraints();
        columnConstraints1.setPercentWidth(20);
        columnConstraints2.setPercentWidth(80);
        gridPane.getColumnConstraints().addAll(columnConstraints1, columnConstraints2);
    }

    private void addContent() {
        rowIndex = 1;
        this.disableActionButton = true;
        MasterCallback parentCallback = this::hideAllPanes;
        inputsGridPane = new InputsPane();
        subPanes.add(inputsGridPane);
        subPanes.add(new ImportPane(mediationManager, inputsGridPane, parentCallback));
        subPanes.add(new ExportPane(inputsGridPane));
        subPanes.add(new SignPane(walletsManager, tradeWalletService, inputsGridPane));
        subPanes.add(new BuildPane(tradeWalletService, p2PService, walletsSetup, inputsGridPane));
        subPanes.add(new SignVerifyPane(walletsManager));
        for (CommonPane pane : subPanes) {
            gridPane.add(pane, 1, rowIndex);    // all panes share the same position
        }
        addLeftPanelButtons();

        // Notes:
        // Open with alt+g
        // Priv key is only visible if pw protection is removed (wallet details data (alt+j))
        // Take missing buyerPubKeyAsHex and sellerPubKeyAsHex from contract data!
        // Lookup sellerPrivateKeyAsHex associated with sellerPubKeyAsHex (or buyers) in wallet details data
        // sellerPubKeys/buyerPubKeys are auto generated if used the fields below
    }

    private void addLeftPanelButtons() {
        VBox vBox = new VBox(10);
        for (int i=0; i<subPanes.size(); i++) {
            Button button = new AutoTooltipButton(subPanes.get(i).getName());
            button.setUserData(i);
            button.setStyle("-fx-pref-width: 200; -fx-padding: 0 0 0 0;");
            button.setOnAction(e -> activatePane((int) button.getUserData(), vBox.getChildren()));
            vBox.getChildren().add(button);
        }
        gridPane.add(vBox, 0, rowIndex);
        activatePane(0, vBox.getChildren());
    }

    private void activatePane(int paneIdx, ObservableList<Node> buttons) {
        hideAllPanes();
        buttons.forEach(button -> button.getStyleClass().remove("action-button"));
        buttons.get(paneIdx).getStyleClass().add("action-button");
        subPanes.get(paneIdx).activate();
    }

    private void hideAllPanes() {
        for (CommonPane pane : subPanes) {
            pane.setVisible(false);
        }
    }
}
