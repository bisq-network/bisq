package bisq.desktop.main.overlays.windows;

import bisq.desktop.components.AutoTooltipButton;
import bisq.desktop.main.overlays.Overlay;

import bisq.core.btc.wallet.BsqWalletService;
import bisq.core.locale.Res;
import bisq.core.util.coin.BsqFormatter;

import com.google.inject.Inject;

import javafx.scene.Scene;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;

import javafx.geometry.Insets;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static bisq.desktop.util.FormBuilder.addTopLabelTextField;

public final class BsqEmptyWalletWindow extends Overlay<BsqEmptyWalletWindow> {
    protected static final Logger log = LoggerFactory.getLogger(BtcEmptyWalletWindow.class);

    private final BsqWalletService bsqWalletService;
    private final BsqFormatter bsqFormatter;

    @Inject
    public BsqEmptyWalletWindow(BsqWalletService bsqWalletService, BsqFormatter bsqFormatter) {
        headLine(Res.get("emptyWalletWindow.headline", "BSQ"));
        width = 768;
        type = Type.Instruction;
        this.bsqWalletService = bsqWalletService;
        this.bsqFormatter = bsqFormatter;
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
        gridPane.getColumnConstraints().remove(1);

        addTopLabelTextField(gridPane, ++rowIndex, Res.get("emptyWalletWindow.balance"),
                bsqFormatter.formatCoinWithCode(bsqWalletService.getAvailableBalance()), 10);

        addTopLabelTextField(gridPane, ++rowIndex, Res.get("emptyWalletWindow.bsq.btcBalance"),
                bsqFormatter.formatBTCWithCode(bsqWalletService.getAvailableNonBsqBalance().value), 10);

        closeButton = new AutoTooltipButton(Res.get("shared.cancel"));
        closeButton.setOnAction(e -> {
            hide();
            closeHandlerOptional.ifPresent(Runnable::run);
        });


        closeButton.setDefaultButton(true);
        closeButton.updateText(Res.get("shared.close"));

        HBox hBox = new HBox();
        hBox.setSpacing(10);
        GridPane.setRowIndex(hBox, ++rowIndex);

        hBox.getChildren().addAll(closeButton);

        gridPane.getChildren().add(hBox);
        GridPane.setMargin(hBox, new Insets(10, 0, 0, 0));
    }
}
