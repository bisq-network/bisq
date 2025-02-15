package bisq.desktop.main.overlays.windows;

import bisq.desktop.components.AutoTooltipButton;
import bisq.desktop.components.InputTextField;
import bisq.desktop.main.overlays.Overlay;
import bisq.desktop.main.overlays.popups.Popup;
import bisq.desktop.util.GUIUtil;
import bisq.desktop.util.Transitions;

import bisq.core.btc.setup.WalletsSetup;
import bisq.core.btc.wallet.BtcWalletService;
import bisq.core.btc.wallet.Restrictions;
import bisq.core.locale.Res;
import bisq.core.offer.OpenOfferManager;
import bisq.core.util.FormattingUtils;
import bisq.core.util.coin.CoinFormatter;

import bisq.network.p2p.P2PService;

import bisq.common.UserThread;

import org.bitcoinj.core.AddressFormatException;
import org.bitcoinj.core.Coin;
import org.bitcoinj.core.InsufficientMoneyException;

import com.google.inject.Inject;

import javax.inject.Named;

import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;

import javafx.geometry.Insets;

import org.bouncycastle.crypto.params.KeyParameter;

import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;

import static bisq.desktop.util.FormBuilder.addInputTextField;
import static bisq.desktop.util.FormBuilder.addMultilineLabel;
import static bisq.desktop.util.FormBuilder.addTopLabelTextField;

public final class BtcEmptyWalletWindow extends Overlay<BtcEmptyWalletWindow> {
    protected static final Logger log = LoggerFactory.getLogger(BtcEmptyWalletWindow.class);

    private final WalletPasswordWindow walletPasswordWindow;
    private final OpenOfferManager openOfferManager;
    private final P2PService p2PService;
    private final WalletsSetup walletsSetup;
    private final BtcWalletService btcWalletService;
    private final CoinFormatter btcFormatter;

    private Button emptyWalletButton;
    private InputTextField addressInputTextField;
    private TextField balanceTextField;

    @Inject
    public BtcEmptyWalletWindow(WalletPasswordWindow walletPasswordWindow,
                                OpenOfferManager openOfferManager,
                                P2PService p2PService,
                                WalletsSetup walletsSetup,
                                BtcWalletService btcWalletService,
                                @Named(FormattingUtils.BTC_FORMATTER_KEY) CoinFormatter btcFormatter) {
        headLine(Res.get("emptyWalletWindow.headline", "BTC"));
        width = 768;
        type = Type.Instruction;

        this.p2PService = p2PService;
        this.walletsSetup = walletsSetup;
        this.btcWalletService = btcWalletService;
        this.btcFormatter = btcFormatter;
        this.walletPasswordWindow = walletPasswordWindow;
        this.openOfferManager = openOfferManager;
    }

    public void show() {
        createGridPane();
        addHeadLine();
        addContent();
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

    private void addContent() {
        addMultilineLabel(gridPane, ++rowIndex, Res.get("emptyWalletWindow.info"), 0);

        Coin totalBalance = btcWalletService.getAvailableBalance();
        balanceTextField = addTopLabelTextField(gridPane, ++rowIndex, Res.get("emptyWalletWindow.balance"),
                btcFormatter.formatCoinWithCode(totalBalance), 10).second;

        addressInputTextField = addInputTextField(gridPane, ++rowIndex, Res.get("emptyWalletWindow.address"));

        closeButton = new AutoTooltipButton(Res.get("shared.cancel"));
        closeButton.setOnAction(e -> {
            hide();
            closeHandlerOptional.ifPresent(Runnable::run);
        });

        emptyWalletButton = new AutoTooltipButton(Res.get("emptyWalletWindow.button"));
        boolean isBalanceSufficient = Restrictions.isAboveDust(totalBalance);
        emptyWalletButton.setDefaultButton(isBalanceSufficient);
        emptyWalletButton.setDisable(!isBalanceSufficient && addressInputTextField.getText().length() > 0);
        emptyWalletButton.setOnAction(e -> {
            if (addressInputTextField.getText().length() > 0 && isBalanceSufficient) {
                if (btcWalletService.isEncrypted()) {
                    walletPasswordWindow
                            .onAesKey(this::doEmptyWallet)
                            .onClose(this::blurAgain)
                            .show();
                } else {
                    doEmptyWallet(null);
                }
            }
        });

        closeButton.setDefaultButton(!isBalanceSufficient);

        HBox hBox = new HBox();
        hBox.setSpacing(10);
        GridPane.setRowIndex(hBox, ++rowIndex);
        hBox.getChildren().addAll(emptyWalletButton, closeButton);
        gridPane.getChildren().add(hBox);
        GridPane.setMargin(hBox, new Insets(10, 0, 0, 0));
    }

    private void doEmptyWallet(@Nullable KeyParameter aesKey) {
        if (GUIUtil.isReadyForTxBroadcastOrShowPopup(p2PService, walletsSetup)) {
            if (!openOfferManager.getObservableList().isEmpty()) {
                UserThread.runAfter(() ->
                        new Popup().warning(Res.get("emptyWalletWindow.openOffers.warn"))
                                .actionButtonText(Res.get("emptyWalletWindow.openOffers.yes"))
                                .onAction(() -> doEmptyWallet2(aesKey))
                                .show(), 300, TimeUnit.MILLISECONDS);
            } else {
                doEmptyWallet2(aesKey);
            }
        }
    }

    private void doEmptyWallet2(@Nullable KeyParameter aesKey) {
        emptyWalletButton.setDisable(true);
        openOfferManager.removeAllOpenOffers(() -> {
            try {
                btcWalletService.emptyBtcWallet(addressInputTextField.getText(),
                        aesKey,
                        () -> {
                            closeButton.updateText(Res.get("shared.close"));
                            balanceTextField.setText(btcFormatter.formatCoinWithCode(btcWalletService.getAvailableBalance()));
                            emptyWalletButton.setDisable(true);
                            log.debug("wallet empty successful");
                            onClose(() -> UserThread.runAfter(() -> new Popup()
                                    .feedback(Res.get("emptyWalletWindow.sent.success"))
                                    .show(), Transitions.DEFAULT_DURATION, TimeUnit.MILLISECONDS));
                            doClose();
                        },
                        (errorMessage) -> {
                            emptyWalletButton.setDisable(false);
                            log.error("wallet empty failed {}", errorMessage);
                        });
            } catch (InsufficientMoneyException | AddressFormatException e1) {
                e1.printStackTrace();
                log.error(e1.getMessage());
                emptyWalletButton.setDisable(false);
            }
        });
    }
}
