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

package bisq.desktop.main.overlays.windows;

import bisq.desktop.components.AutoTooltipButton;
import bisq.desktop.main.overlays.Overlay;
import bisq.desktop.main.portfolio.pendingtrades.steps.TradeWizardItem;
import bisq.desktop.main.portfolio.pendingtrades.steps.buyer.BuyerStep1View;
import bisq.desktop.main.portfolio.pendingtrades.steps.buyer.BuyerStep2View;
import bisq.desktop.main.portfolio.pendingtrades.steps.buyer.BuyerStep3View;
import bisq.desktop.util.Layout;

import bisq.core.locale.Res;
import bisq.core.support.dispute.mediation.FileTransferSender;
import bisq.core.support.dispute.mediation.FileTransferSession;
import bisq.core.support.dispute.mediation.MediationManager;

import bisq.common.UserThread;

import com.jfoenix.controls.JFXProgressBar;

import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;

import javafx.geometry.HPos;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;

import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;

import java.io.IOException;

import lombok.extern.slf4j.Slf4j;

import static bisq.desktop.util.FormBuilder.addMultilineLabel;

@Slf4j
public class SendLogFilesWindow extends Overlay<SendLogFilesWindow> implements FileTransferSession.FtpCallback {

    private final String tradeId;
    private final int traderId;
    private final MediationManager mediationManager;
    private Label statusLabel;
    private Button sendButton, stopButton;
    private final DoubleProperty ftpProgress = new SimpleDoubleProperty(-1);
    TradeWizardItem step1, step2, step3;
    private FileTransferSender fileTransferSender;

    public SendLogFilesWindow(String tradeId, int traderId,
                              MediationManager mediationManager) {
        this.tradeId = tradeId;
        this.traderId = traderId;
        this.mediationManager = mediationManager;
        type = Type.Attention;
    }

    public void show() {
        headLine = Res.get("support.sendLogs.title");
        width = 668;
        createGridPane();
        addHeadLine();
        addContent();
        addButtons();
        applyStyles();
        display();
    }

    @Override
    protected void createGridPane() {
        gridPane = new GridPane();
        gridPane.setHgap(5);
        gridPane.setVgap(5);
        gridPane.setPadding(new Insets(64, 64, 64, 64));
        gridPane.setPrefWidth(width);
    }

    void addWizardsToGridPane(TradeWizardItem tradeWizardItem) {
        GridPane.setRowIndex(tradeWizardItem, rowIndex++);
        GridPane.setColumnIndex(tradeWizardItem, 0);
        GridPane.setHalignment(tradeWizardItem, HPos.LEFT);
        gridPane.getChildren().add(tradeWizardItem);
    }

    void addLineSeparatorToGridPane() {
        final Separator separator = new Separator(Orientation.VERTICAL);
        separator.setMinHeight(22);
        GridPane.setMargin(separator, new Insets(0, 0, 0, 13));
        GridPane.setHalignment(separator, HPos.LEFT);
        GridPane.setRowIndex(separator, rowIndex++);
        gridPane.getChildren().add(separator);
    }

    void addRegionToGridPane() {
        final Region region = new Region();
        region.setMinHeight(22);
        GridPane.setMargin(region, new Insets(0, 0, 0, 13));
        GridPane.setRowIndex(region, rowIndex++);
        gridPane.getChildren().add(region);
    }

    private void addContent() {
        this.hideCloseButton = true;

        addMultilineLabel(gridPane, ++rowIndex, Res.get("support.sendLogs.backgroundInfo"), 0);
        addRegionToGridPane();

        step1 = new TradeWizardItem(BuyerStep1View.class, Res.get("support.sendLogs.step1"), "1");
        step2 = new TradeWizardItem(BuyerStep2View.class, Res.get("support.sendLogs.step2"), "2");
        step3 = new TradeWizardItem(BuyerStep3View.class, Res.get("support.sendLogs.step3"), "3");

        addRegionToGridPane();
        addRegionToGridPane();
        addWizardsToGridPane(step1);
        addLineSeparatorToGridPane();
        addWizardsToGridPane(step2);
        addLineSeparatorToGridPane();
        addWizardsToGridPane(step3);
        addRegionToGridPane();

        JFXProgressBar progressBar = new JFXProgressBar();
        progressBar.setMinHeight(19);
        progressBar.setMaxHeight(19);
        progressBar.setPrefWidth(9305);
        progressBar.setVisible(false);
        progressBar.progressProperty().bind(ftpProgress);
        gridPane.add(progressBar, 0, ++rowIndex);

        statusLabel = addMultilineLabel(gridPane, ++rowIndex, "", -Layout.FLOATING_LABEL_DISTANCE);
        statusLabel.getStyleClass().add("sub-info");
        addRegionToGridPane();

        sendButton = new AutoTooltipButton(Res.get("support.sendLogs.send"));
        stopButton = new AutoTooltipButton(Res.get("support.sendLogs.cancel"));
        stopButton.setDisable(true);
        closeButton = new AutoTooltipButton(Res.get("shared.close"));
        sendButton.setOnAction(e -> {
            try {
                progressBar.setVisible(true);
                if (fileTransferSender == null) {
                    setActiveStep(1);
                    statusLabel.setText(Res.get("support.sendLogs.init"));
                    fileTransferSender = mediationManager.initLogUpload(this, tradeId, traderId);
                    UserThread.runAfter(() -> {
                        fileTransferSender.createZipFileToSend();
                        setActiveStep(2);
                        UserThread.runAfter(() -> {
                            setActiveStep(3);
                            try {
                                fileTransferSender.initSend();
                            } catch (IOException ioe) {
                                log.error(ioe.toString());
                                statusLabel.setText(ioe.toString());
                                ioe.printStackTrace();
                            }
                        }, 1);
                    }, 1);
                    sendButton.setDisable(true);
                    stopButton.setDisable(false);
                } else {
                    // resend the latest block in the event of a timeout
                    statusLabel.setText(Res.get("support.sendLogs.retry"));
                    fileTransferSender.retrySend();
                    sendButton.setDisable(true);
                }
            } catch (IOException ex) {
                log.error(ex.toString());
                statusLabel.setText(ex.toString());
                ex.printStackTrace();
            }
        });
        stopButton.setOnAction(e -> {
            if (fileTransferSender != null) {
                fileTransferSender.resetSession();
                statusLabel.setText(Res.get("support.sendLogs.stopped"));
                stopButton.setDisable(true);
            }
        });
        closeButton.setOnAction(e -> {
            hide();
            closeHandlerOptional.ifPresent(Runnable::run);
        });
        HBox hBox = new HBox();
        hBox.setSpacing(10);
        hBox.setAlignment(Pos.CENTER_RIGHT);
        GridPane.setRowIndex(hBox, ++rowIndex);
        GridPane.setColumnSpan(hBox, 2);
        GridPane.setColumnIndex(hBox, 0);
        hBox.getChildren().addAll(sendButton, stopButton, closeButton);
        gridPane.getChildren().add(hBox);
        GridPane.setMargin(hBox, new Insets(10, 0, 0, 0));
    }

    void setActiveStep(int step) {
        if (step < 1) {
            step1.setDisabled();
            step2.setDisabled();
            step3.setDisabled();
        } else if (step == 1) {
            step1.setActive();
        } else if (step == 2) {
            step1.setCompleted();
            step2.setActive();
        } else if (step == 3) {
            step2.setCompleted();
            step3.setActive();
        } else {
            step3.setCompleted();
        }
    }

    @Override
    public void onFtpProgress(double progressPct) {
        if (progressPct > 0.0) {
            statusLabel.setText(String.format(Res.get("support.sendLogs.progress"), progressPct * 100));
            sendButton.setDisable(true);
        }
        ftpProgress.set(progressPct);
    }
    @Override
    public void onFtpComplete(FileTransferSession session) {
        setActiveStep(4);       // all finished
        statusLabel.setText(Res.get("support.sendLogs.finished"));
        stopButton.setDisable(true);
    }
    @Override
    public void onFtpTimeout(String statusMsg, FileTransferSession session) {
        statusLabel.setText(statusMsg + "\r\n" + Res.get("support.sendLogs.command"));
        sendButton.setDisable(false);
    }
}
