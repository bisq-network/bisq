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

package bisq.desktop.util;

import bisq.desktop.app.BisqApp;
import bisq.desktop.components.indicator.TxConfidenceIndicator;
import bisq.desktop.main.overlays.popups.Popup;

import bisq.core.app.BisqEnvironment;
import bisq.core.btc.wallet.WalletsManager;
import bisq.core.btc.wallet.WalletsSetup;
import bisq.core.payment.PaymentAccount;
import bisq.core.payment.PaymentAccountList;
import bisq.core.provider.fee.FeeService;
import bisq.core.user.DontShowAgainLookup;
import bisq.core.user.Preferences;
import bisq.core.user.User;

import bisq.network.p2p.P2PService;

import bisq.common.UserThread;
import bisq.common.app.DevEnv;
import bisq.common.locale.CurrencyUtil;
import bisq.common.locale.Res;
import bisq.common.locale.TradeCurrency;
import bisq.common.proto.persistable.PersistableList;
import bisq.common.proto.persistable.PersistenceProtoResolver;
import bisq.common.storage.FileUtil;
import bisq.common.storage.Storage;
import bisq.common.util.Utilities;

import org.bitcoinj.core.Address;
import org.bitcoinj.core.Coin;
import org.bitcoinj.core.TransactionConfidence;
import org.bitcoinj.uri.BitcoinURI;
import org.bitcoinj.wallet.DeterministicSeed;

import com.googlecode.jcsv.CSVStrategy;
import com.googlecode.jcsv.writer.CSVEntryConverter;
import com.googlecode.jcsv.writer.CSVWriter;
import com.googlecode.jcsv.writer.internal.CSVWriterBuilder;

import com.google.common.base.Charsets;

import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.ScrollBar;
import javafx.scene.control.TextArea;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Region;

import javafx.geometry.Orientation;

import javafx.beans.property.DoubleProperty;

import javafx.util.StringConverter;

import java.nio.file.Paths;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class GUIUtil {
    public final static String SHOW_ALL_FLAG = "SHOW_ALL_FLAG";
    public final static String EDIT_FLAG = "EDIT_FLAG";

    public final static int FIAT_DECIMALS_WITH_ZEROS = 0;
    public final static int FIAT_PRICE_DECIMALS_WITH_ZEROS = 3;
    public final static int ALTCOINS_DECIMALS_WITH_ZEROS = 7;
    public final static int AMOUNT_DECIMALS_WITH_ZEROS = 3;
    public final static int AMOUNT_DECIMALS = 4;
    public final static String RANGE_SEPARATOR = " - ";

    private static FeeService feeService;

    public static void setFeeService(FeeService feeService) {
        GUIUtil.feeService = feeService;
    }

    public static double getScrollbarWidth(Node scrollablePane) {
        Node node = scrollablePane.lookup(".scroll-bar");
        if (node instanceof ScrollBar) {
            final ScrollBar bar = (ScrollBar) node;
            if (bar.getOrientation().equals(Orientation.VERTICAL))
                return bar.getWidth();
        }
        return 0;
    }

    public static void focusWhenAddedToScene(Node node) {
        node.sceneProperty().addListener((observable, oldValue, newValue) -> {
            if (null != newValue) {
                node.requestFocus();
            }
        });
    }

    @SuppressWarnings("PointlessBooleanExpression")
    public static void showFeeInfoBeforeExecute(Runnable runnable) {
        //noinspection UnusedAssignment
        String key = "miningFeeInfo";
        //noinspection ConstantConditions,ConstantConditions
        if (!DevEnv.isDevMode() && DontShowAgainLookup.showAgain(key) && BisqEnvironment.getBaseCurrencyNetwork().isBitcoin()) {
            new Popup<>().attention(Res.get("guiUtil.miningFeeInfo", String.valueOf(GUIUtil.feeService.getTxFeePerByte().value)))
                    .onClose(runnable::run)
                    .useIUnderstandButton()
                    .show();
            DontShowAgainLookup.dontShowAgain(key, true);
        } else {
            runnable.run();
        }
    }

    public static void exportAccounts(ArrayList<PaymentAccount> accounts, String fileName,
                                      Preferences preferences, Stage stage, PersistenceProtoResolver persistenceProtoResolver) {
        if (!accounts.isEmpty()) {
            String directory = getDirectoryFromChooser(preferences, stage);
            if (directory != null && !directory.isEmpty()) {
                Storage<PersistableList<PaymentAccount>> paymentAccountsStorage = new Storage<>(new File(directory), persistenceProtoResolver);
                paymentAccountsStorage.initAndGetPersisted(new PaymentAccountList(accounts), fileName, 100);
                paymentAccountsStorage.queueUpForSave();
                new Popup<>().feedback(Res.get("guiUtil.accountExport.savedToPath", Paths.get(directory, fileName).toAbsolutePath())).show();
            }
        } else {
            new Popup<>().warning(Res.get("guiUtil.accountExport.noAccountSetup")).show();
        }
    }

    public static void importAccounts(User user, String fileName, Preferences preferences, Stage stage,
                                      PersistenceProtoResolver persistenceProtoResolver) {
        FileChooser fileChooser = new FileChooser();
        File initDir = new File(preferences.getDirectoryChooserPath());
        if (initDir.isDirectory()) {
            fileChooser.setInitialDirectory(initDir);
        }
        fileChooser.setTitle(Res.get("guiUtil.accountExport.selectPath", fileName));
        File file = fileChooser.showOpenDialog(stage.getOwner());
        if (file != null) {
            String path = file.getAbsolutePath();
            if (Paths.get(path).getFileName().toString().equals(fileName)) {
                String directory = Paths.get(path).getParent().toString();
                preferences.setDirectoryChooserPath(directory);
                Storage<PaymentAccountList> paymentAccountsStorage = new Storage<>(new File(directory), persistenceProtoResolver);
                PaymentAccountList persisted = paymentAccountsStorage.initAndGetPersistedWithFileName(fileName, 100);
                if (persisted != null) {
                    final StringBuilder msg = new StringBuilder();
                    persisted.getList().stream().forEach(paymentAccount -> {
                        final String id = paymentAccount.getId();
                        if (user.getPaymentAccount(id) == null) {
                            user.addPaymentAccount(paymentAccount);
                            msg.append(Res.get("guiUtil.accountExport.tradingAccount", id));
                        } else {
                            msg.append(Res.get("guiUtil.accountImport.noImport", id));
                        }
                    });
                    new Popup<>().feedback(Res.get("guiUtil.accountImport.imported", path, msg)).show();

                } else {
                    new Popup<>().warning(Res.get("guiUtil.accountImport.noAccountsFound", path, fileName)).show();
                }
            } else {
                log.error("The selected file is not the expected file for import. The expected file name is: " + fileName + ".");
            }
        }
    }


    public static <T> void exportCSV(String fileName, CSVEntryConverter<T> headerConverter,
                                     CSVEntryConverter<T> contentConverter, T emptyItem,
                                     List<T> list, Stage stage) {

        FileChooser fileChooser = new FileChooser();
        fileChooser.setInitialFileName(fileName);
        File file = fileChooser.showSaveDialog(stage);
        if (file != null) {
            try (OutputStreamWriter outputStreamWriter = new OutputStreamWriter(new FileOutputStream(file, false), Charsets.UTF_8)) {
                CSVWriter<T> headerWriter = new CSVWriterBuilder<T>(outputStreamWriter)
                        .strategy(CSVStrategy.UK_DEFAULT)
                        .entryConverter(headerConverter)
                        .build();
                headerWriter.write(emptyItem);

                CSVWriter<T> contentWriter = new CSVWriterBuilder<T>(outputStreamWriter)
                        .strategy(CSVStrategy.UK_DEFAULT)
                        .entryConverter(contentConverter)
                        .build();
                contentWriter.writeAll(list);
            } catch (RuntimeException | IOException e) {
                e.printStackTrace();
                log.error(e.getMessage());
                new Popup<>().error(Res.get("guiUtil.accountExport.exportFailed", e.getMessage()));
            }
        }
    }

    public static String getDirectoryFromChooser(Preferences preferences, Stage stage) {
        DirectoryChooser directoryChooser = new DirectoryChooser();
        File initDir = new File(preferences.getDirectoryChooserPath());
        if (initDir.isDirectory()) {
            directoryChooser.setInitialDirectory(initDir);
        }
        directoryChooser.setTitle(Res.get("guiUtil.accountExport.selectExportPath"));
        File dir = directoryChooser.showDialog(stage);
        if (dir != null) {
            String directory = dir.getAbsolutePath();
            preferences.setDirectoryChooserPath(directory);
            return directory;
        } else {
            return "";
        }
    }

    public static StringConverter<CurrencyListItem> getCurrencyListItemConverter(String postFixSingle, String postFixMulti, Preferences preferences) {
        return new StringConverter<CurrencyListItem>() {
            @Override
            public String toString(CurrencyListItem item) {
                TradeCurrency tradeCurrency = item.tradeCurrency;
                String code = tradeCurrency.getCode();
                switch (code) {
                    case GUIUtil.SHOW_ALL_FLAG:
                        return "▶ " + Res.get("list.currency.showAll");
                    case GUIUtil.EDIT_FLAG:
                        return "▼ " + Res.get("list.currency.editList");
                    default:
                        String displayString = CurrencyUtil.getNameByCode(code) + " (" + code + ")";
                        if (preferences.isSortMarketCurrenciesNumerically()) {
                            final int numTrades = item.numTrades;
                            displayString += " - " + numTrades + " " + (numTrades == 1 ? postFixSingle : postFixMulti);
                        }
                        return tradeCurrency.getDisplayPrefix() + displayString;
                }
            }

            @Override
            public CurrencyListItem fromString(String s) {
                return null;
            }
        };
    }

    public static StringConverter<TradeCurrency> getTradeCurrencyConverter(
            String postFixSingle,
            String postFixMulti,
            Map<String, Integer> offerCounts) {
        return new StringConverter<TradeCurrency>() {
            @Override
            public String toString(TradeCurrency tradeCurrency) {
                String code = tradeCurrency.getCode();
                Optional<Integer> offerCountOptional = Optional.ofNullable(offerCounts.get(code));
                final String displayString;
                if (offerCountOptional.isPresent()) {
                    displayString = CurrencyUtil.getNameAndCode(code)
                            + " - " + offerCountOptional.get() + " " + (offerCountOptional.get() == 1 ? postFixSingle : postFixMulti);
                } else {
                    displayString = CurrencyUtil.getNameAndCode(code);
                }
                // http://boschista.deviantart.com/journal/Cool-ASCII-Symbols-214218618
                if (code.equals(GUIUtil.SHOW_ALL_FLAG))
                    return "▶ " + Res.get("list.currency.showAll");
                else if (code.equals(GUIUtil.EDIT_FLAG))
                    return "▼ " + Res.get("list.currency.editList");
                return tradeCurrency.getDisplayPrefix() + displayString;
            }

            @Override
            public TradeCurrency fromString(String s) {
                return null;
            }
        };
    }

    public static void updateConfidence(TransactionConfidence confidence, Tooltip tooltip, TxConfidenceIndicator txConfidenceIndicator) {
        if (confidence != null) {
            switch (confidence.getConfidenceType()) {
                case UNKNOWN:
                    tooltip.setText(Res.get("confidence.unknown"));
                    txConfidenceIndicator.setProgress(0);
                    break;
                case PENDING:
                    tooltip.setText(Res.get("confidence.seen", confidence.numBroadcastPeers()));
                    txConfidenceIndicator.setProgress(-1.0);
                    break;
                case BUILDING:
                    tooltip.setText(Res.get("confidence.confirmed", confidence.getDepthInBlocks()));
                    txConfidenceIndicator.setProgress(Math.min(1, (double) confidence.getDepthInBlocks() / 6.0));
                    break;
                case DEAD:
                    tooltip.setText(Res.get("confidence.invalid"));
                    txConfidenceIndicator.setProgress(0);
                    break;
            }

            txConfidenceIndicator.setPrefSize(24, 24);
        }
    }


    public static void openWebPage(String target) {
        String key = "warnOpenURLWhenTorEnabled";
        if (DontShowAgainLookup.showAgain(key)) {
            new Popup<>().information(Res.get("guiUtil.openWebBrowser.warning", target))
                    .actionButtonText(Res.get("guiUtil.openWebBrowser.doOpen"))
                    .onAction(() -> {
                        DontShowAgainLookup.dontShowAgain(key, true);
                        doOpenWebPage(target);
                    })
                    .closeButtonText(Res.get("guiUtil.openWebBrowser.copyUrl"))
                    .onClose(() -> Utilities.copyToClipboard(target))
                    .show();
        } else {
            doOpenWebPage(target);
        }
    }

    private static void doOpenWebPage(String target) {
        try {
            Utilities.openURI(new URI(target));
        } catch (Exception e) {
            e.printStackTrace();
            log.error(e.getMessage());
        }
    }

    public static void openMail(String to, String subject, String body) {
        try {
            subject = URLEncoder.encode(subject, "UTF-8").replace("+", "%20");
            body = URLEncoder.encode(body, "UTF-8").replace("+", "%20");
            Utilities.openURI(new URI("mailto:" + to + "?subject=" + subject + "&body=" + body));
        } catch (IOException | URISyntaxException e) {
            log.error("openMail failed " + e.getMessage());
            e.printStackTrace();
        }
    }

    public static String getPercentageOfTradeAmount(Coin fee, Coin tradeAmount, BSFormatter formatter) {
        return " (" + getPercentage(fee, tradeAmount, formatter) +
                " " + Res.get("guiUtil.ofTradeAmount") + ")";
    }

    public static String getPercentage(Coin part, Coin total, BSFormatter formatter) {
        return formatter.formatToPercentWithSymbol((double) part.value / (double) total.value);
    }

    @SuppressWarnings({"UnusedParameters", "SameReturnValue"})
    public static String getPercentageOfTradeAmountForBsq(Coin fee, Coin tradeAmount, BSFormatter formatter) {
        // TODO convert to BTC with market price
        return "";
       /* return " (" + formatter.formatToPercentWithSymbol((double) fee.value / (double) tradeAmount.value) +
                " " + Res.get("guiUtil.ofTradeAmount") + ")";*/
    }

    public static <T> T getParentOfType(Node node, Class<T> t) {
        Node parent = node.getParent();

        while (parent != null) {
            if (parent.getClass().isAssignableFrom(t)) {
                break;
            } else {
                parent = parent.getParent();
            }
        }

        //noinspection unchecked
        return parent != null ? (T) parent : null;
    }

    public static void showClearXchangeWarning() {
        String key = "confirmClearXchangeRequirements";
        final String currencyName = BisqEnvironment.getBaseCurrencyNetwork().getCurrencyName();
        new Popup<>().information(Res.get("payment.clearXchange.info", currencyName, currencyName))
                .width(900)
                .closeButtonText(Res.get("shared.iConfirm"))
                .dontShowAgainId(key)
                .show();
    }

    public static void fillAvailableHeight(Pane container, Region component, DoubleProperty initialOccupiedHeight) {
        UserThread.runAfter(() -> {

            double available;
            if (container.getParent() instanceof Pane)
                available = ((Pane) container.getParent()).getHeight();
            else
                available = container.getHeight();

            if (initialOccupiedHeight.get() == -1 && component.getHeight() > 0) {
                initialOccupiedHeight.set(available - component.getHeight());
            }
            component.setPrefHeight(available - initialOccupiedHeight.get());
        }, 100, TimeUnit.MILLISECONDS);
    }

    public static String getBitcoinURI(String address, Coin amount, String label) {
        return address != null ?
                BitcoinURI.convertToBitcoinURI(Address.fromBase58(BisqEnvironment.getParameters(),
                        address), amount, label, null) :
                "";
    }

    public static boolean isReadyForTxBroadcast(P2PService p2PService, WalletsSetup walletsSetup) {
        return p2PService.isBootstrapped() &&
                walletsSetup.isDownloadComplete() &&
                walletsSetup.hasSufficientPeersForBroadcast();
    }

    public static void showNotReadyForTxBroadcastPopups(P2PService p2PService, WalletsSetup walletsSetup) {
        if (!p2PService.isBootstrapped())
            new Popup<>().information(Res.get("popup.warning.notFullyConnected")).show();
        else if (!walletsSetup.hasSufficientPeersForBroadcast())
            new Popup<>().information(Res.get("popup.warning.notSufficientConnectionsToBtcNetwork", walletsSetup.getMinBroadcastConnections())).show();
        else if (!walletsSetup.isDownloadComplete())
            new Popup<>().information(Res.get("popup.warning.downloadNotComplete")).show();
    }

    public static void requestFocus(Node node) {
        UserThread.execute(node::requestFocus);
    }

    public static void reSyncSPVChain(WalletsSetup walletsSetup, Preferences preferences) {
        try {
            new Popup<>().feedback(Res.get("settings.net.reSyncSPVSuccess"))
                    .useShutDownButton()
                    .actionButtonText(Res.get("shared.shutDown"))
                    .onAction(() -> {
                        preferences.setResyncSpvRequested(true);
                        UserThread.runAfter(BisqApp.shutDownHandler::run, 100, TimeUnit.MILLISECONDS);
                    })
                    .hideCloseButton()
                    .show();
        } catch (Throwable t) {
            new Popup<>().error(Res.get("settings.net.reSyncSPVFailed", t)).show();
        }
    }

    public static void restoreSeedWords(DeterministicSeed seed, WalletsManager walletsManager, File storageDir) {
        try {
            FileUtil.renameFile(new File(storageDir, "AddressEntryList"), new File(storageDir, "AddressEntryList_wallet_restore_" + System.currentTimeMillis()));
        } catch (Throwable t) {
            new Popup<>().error(Res.get("error.deleteAddressEntryListFailed", t)).show();
        }
        walletsManager.restoreSeedWords(
                seed,
                () -> UserThread.execute(() -> {
                    log.info("Wallets restored with seed words");
                    new Popup<>().feedback(Res.get("seed.restore.success"))
                            .useShutDownButton()
                            .show();
                }),
                throwable -> UserThread.execute(() -> {
                    log.error(throwable.toString());
                    new Popup<>().error(Res.get("seed.restore.error", Res.get("shared.errorMessageInline", throwable)))
                            .show();
                }));
    }

    public static void showSelectableTextModal(String title, String text) {
        TextArea textArea = new TextArea();
        textArea.setText(text);
        textArea.setEditable(false);
        textArea.setWrapText(true);
        textArea.setPrefSize(800, 600);

        Scene scene = new Scene(textArea);
        Stage stage = new Stage();
        if (null != title) {
            stage.setTitle(title);
        }
        stage.setScene(scene);
        stage.initModality(Modality.NONE);
        stage.initStyle(StageStyle.UTILITY);
        stage.show();
    }

    public static StringConverter<PaymentAccount> getPaymentAccountsComboBoxStringConverter() {
        return new StringConverter<PaymentAccount>() {
            @Override
            public String toString(PaymentAccount paymentAccount) {
                if (paymentAccount.hasMultipleCurrencies()) {
                    return paymentAccount.getAccountName() + " (" + Res.get(paymentAccount.getPaymentMethod().getId()) + ")";
                } else {
                    TradeCurrency singleTradeCurrency = paymentAccount.getSingleTradeCurrency();
                    String prefix = singleTradeCurrency != null ? singleTradeCurrency.getCode() + ", " : "";
                    return paymentAccount.getAccountName() + " (" + prefix +
                            Res.get(paymentAccount.getPaymentMethod().getId()) + ")";
                }
            }

            @Override
            public PaymentAccount fromString(String s) {
                return null;
            }
        };
    }
}
