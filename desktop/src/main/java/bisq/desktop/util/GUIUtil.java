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
import bisq.desktop.components.AutoTooltipLabel;
import bisq.desktop.components.BisqTextArea;
import bisq.desktop.components.indicator.TxConfidenceIndicator;
import bisq.desktop.main.overlays.popups.Popup;

import bisq.core.app.BisqEnvironment;
import bisq.core.btc.setup.WalletsSetup;
import bisq.core.btc.wallet.WalletsManager;
import bisq.core.locale.Country;
import bisq.core.locale.CountryUtil;
import bisq.core.locale.CurrencyUtil;
import bisq.core.locale.Res;
import bisq.core.locale.TradeCurrency;
import bisq.core.payment.PaymentAccount;
import bisq.core.payment.PaymentAccountList;
import bisq.core.payment.payload.PaymentMethod;
import bisq.core.provider.fee.FeeService;
import bisq.core.user.DontShowAgainLookup;
import bisq.core.user.Preferences;
import bisq.core.user.User;
import bisq.core.util.BSFormatter;
import bisq.core.util.BsqFormatter;
import bisq.core.util.CoinUtil;

import bisq.network.p2p.P2PService;

import bisq.common.UserThread;
import bisq.common.app.DevEnv;
import bisq.common.proto.persistable.PersistableList;
import bisq.common.proto.persistable.PersistenceProtoResolver;
import bisq.common.storage.FileUtil;
import bisq.common.storage.Storage;
import bisq.common.util.Tuple2;
import bisq.common.util.Tuple3;
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

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;

import com.google.common.base.Charsets;

import org.apache.commons.lang3.StringUtils;

import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.ScrollBar;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Region;

import javafx.geometry.Orientation;

import javafx.beans.property.DoubleProperty;

import javafx.collections.FXCollections;

import javafx.util.Callback;
import javafx.util.StringConverter;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;

import java.nio.file.Paths;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import lombok.extern.slf4j.Slf4j;

import org.jetbrains.annotations.NotNull;

import static bisq.desktop.util.FormBuilder.addTopLabelComboBoxComboBox;
import static com.google.common.base.Preconditions.checkArgument;

@Slf4j
public class GUIUtil {
    public final static String SHOW_ALL_FLAG = "list.currency.showAll"; // Used for accessing the i18n resource
    public final static String EDIT_FLAG = "list.currency.editList"; // Used for accessing the i18n resource

    public final static int FIAT_DECIMALS_WITH_ZEROS = 0;
    public final static int FIAT_PRICE_DECIMALS_WITH_ZEROS = 3;
    public final static int ALTCOINS_DECIMALS_WITH_ZEROS = 7;
    public final static int AMOUNT_DECIMALS_WITH_ZEROS = 3;
    public final static int AMOUNT_DECIMALS = 4;

    private static FeeService feeService;
    private static Preferences preferences;

    public static void setFeeService(FeeService feeService) {
        GUIUtil.feeService = feeService;
    }

    public static void setPreferences(Preferences preferences) {
        GUIUtil.preferences = preferences;
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
        if (!DevEnv.isDevMode() && DontShowAgainLookup.showAgain(key)) {
            new Popup<>().attention(Res.get("guiUtil.miningFeeInfo", String.valueOf(GUIUtil.feeService.getTxFeePerByte().value)))
                    .onClose(runnable)
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
                    final HashSet<PaymentAccount> paymentAccounts = new HashSet<>();
                    persisted.getList().forEach(paymentAccount -> {
                        final String id = paymentAccount.getId();
                        if (user.getPaymentAccount(id) == null) {
                            paymentAccounts.add(paymentAccount);
                            msg.append(Res.get("guiUtil.accountExport.tradingAccount", id));
                        } else {
                            msg.append(Res.get("guiUtil.accountImport.noImport", id));
                        }
                    });
                    user.addImportedPaymentAccounts(paymentAccounts);
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

    public static void exportJSON(String fileName, JsonElement data, Stage stage) {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setInitialFileName(fileName);
        File file = fileChooser.showSaveDialog(stage);
        if (file != null) {
            try (OutputStreamWriter outputStreamWriter = new OutputStreamWriter(new FileOutputStream(file, false), Charsets.UTF_8)) {
                Gson gson = new GsonBuilder().setPrettyPrinting().create();
                outputStreamWriter.write(gson.toJson(data));
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

    public static ListCell<CurrencyListItem> getCurrencyListItemButtonCell(String postFixSingle, String postFixMulti,
                                                                           Preferences preferences) {
        return new ListCell<>() {

            @Override
            protected void updateItem(CurrencyListItem item, boolean empty) {
                super.updateItem(item, empty);

                if (item != null && !empty) {
                    String code = item.tradeCurrency.getCode();

                    AnchorPane pane = new AnchorPane();
                    Label currency = new AutoTooltipLabel(code + " - " + item.tradeCurrency.getName());
                    currency.getStyleClass().add("currency-label-selected");
                    AnchorPane.setLeftAnchor(currency, 0.0);
                    pane.getChildren().add(currency);

                    switch (code) {
                        case GUIUtil.SHOW_ALL_FLAG:
                            currency.setText(Res.get("list.currency.showAll"));
                            break;
                        case GUIUtil.EDIT_FLAG:
                            currency.setText(Res.get("list.currency.editList"));
                            break;
                        default:
                            if (preferences.isSortMarketCurrenciesNumerically()) {
                                Label numberOfOffers = new AutoTooltipLabel(item.numTrades + " " +
                                        (item.numTrades == 1 ? postFixSingle : postFixMulti));
                                numberOfOffers.getStyleClass().add("offer-label-small");
                                AnchorPane.setRightAnchor(numberOfOffers, 0.0);
                                AnchorPane.setBottomAnchor(numberOfOffers, 2.0);
                                pane.getChildren().add(numberOfOffers);
                            }
                    }

                    setGraphic(pane);
                    setText("");
                } else {
                    setGraphic(null);
                    setText("");
                }
            }
        };
    }

    public static Callback<ListView<CurrencyListItem>, ListCell<CurrencyListItem>> getCurrencyListItemCellFactory(String postFixSingle, String postFixMulti,
                                                                                                                  Preferences preferences) {
        return p -> new ListCell<>() {
            @Override
            protected void updateItem(CurrencyListItem item, boolean empty) {
                super.updateItem(item, empty);

                if (item != null && !empty) {

                    String code = item.tradeCurrency.getCode();

                    HBox box = new HBox();
                    box.setSpacing(20);
                    Label currencyType = new AutoTooltipLabel(
                            CurrencyUtil.isFiatCurrency(code) ? Res.get("shared.fiat") : Res.get("shared.crypto"));

                    currencyType.getStyleClass().add("currency-label-small");
                    Label currency = new AutoTooltipLabel(code);
                    currency.getStyleClass().add("currency-label");
                    Label offers = new AutoTooltipLabel(item.tradeCurrency.getName());
                    offers.getStyleClass().add("currency-label");

                    box.getChildren().addAll(currencyType, currency, offers);

                    switch (code) {
                        case GUIUtil.SHOW_ALL_FLAG:
                            currencyType.setText(Res.get("shared.all"));
                            currency.setText(Res.get("list.currency.showAll"));
                            break;
                        case GUIUtil.EDIT_FLAG:
                            currencyType.setText(Res.get("shared.edit"));
                            currency.setText(Res.get("list.currency.editList"));
                            break;
                        default:
                            if (preferences.isSortMarketCurrenciesNumerically()) {
                                offers.setText(offers.getText() + " (" + item.numTrades + " " +
                                        (item.numTrades == 1 ? postFixSingle : postFixMulti) + ")");
                            }
                    }

                    setGraphic(box);

                } else {
                    setGraphic(null);
                }
            }
        };
    }

    public static ListCell<TradeCurrency> getTradeCurrencyButtonCell(String postFixSingle,
                                                                     String postFixMulti,
                                                                     Map<String, Integer> offerCounts) {
        return new ListCell<>() {

            @Override
            protected void updateItem(TradeCurrency item, boolean empty) {
                super.updateItem(item, empty);

                if (item != null && !empty) {
                    String code = item.getCode();

                    AnchorPane pane = new AnchorPane();
                    Label currency = new AutoTooltipLabel(code + " - " + item.getName());
                    currency.getStyleClass().add("currency-label-selected");
                    AnchorPane.setLeftAnchor(currency, 0.0);
                    pane.getChildren().add(currency);

                    Optional<Integer> offerCountOptional = Optional.ofNullable(offerCounts.get(code));

                    switch (code) {
                        case GUIUtil.SHOW_ALL_FLAG:
                            currency.setText(Res.get("list.currency.showAll"));
                            break;
                        case GUIUtil.EDIT_FLAG:
                            currency.setText(Res.get("list.currency.editList"));
                            break;
                        default:
                            if (offerCountOptional.isPresent()) {
                                Label numberOfOffers = new AutoTooltipLabel(offerCountOptional.get() + " " +
                                        (offerCountOptional.get() == 1 ? postFixSingle : postFixMulti));
                                numberOfOffers.getStyleClass().add("offer-label-small");
                                AnchorPane.setRightAnchor(numberOfOffers, 0.0);
                                AnchorPane.setBottomAnchor(numberOfOffers, 2.0);
                                pane.getChildren().add(numberOfOffers);
                            }
                    }

                    setGraphic(pane);
                    setText("");
                } else {
                    setGraphic(null);
                    setText("");
                }
            }
        };
    }

    public static StringConverter<TradeCurrency> getTradeCurrencyConverter(String postFixSingle,
                                                                           String postFixMulti,
                                                                           Map<String, Integer> offerCounts) {
        return new StringConverter<>() {
            @Override
            public String toString(TradeCurrency tradeCurrency) {
                String code = tradeCurrency.getCode();
                Optional<Integer> offerCountOptional = Optional.ofNullable(offerCounts.get(code));
                final String displayString;
                displayString = offerCountOptional
                        .map(offerCount -> CurrencyUtil.getNameAndCode(code)
                                + " - " + offerCount + " " + (offerCount == 1 ? postFixSingle : postFixMulti))
                        .orElseGet(() -> CurrencyUtil.getNameAndCode(code));
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

    public static Callback<ListView<TradeCurrency>, ListCell<TradeCurrency>> getTradeCurrencyCellFactory(String postFixSingle,
                                                                                                         String postFixMulti,
                                                                                                         Map<String, Integer> offerCounts) {
        return p -> new ListCell<>() {
            @Override
            protected void updateItem(TradeCurrency item, boolean empty) {
                super.updateItem(item, empty);

                if (item != null && !empty) {

                    String code = item.getCode();

                    HBox box = new HBox();
                    box.setSpacing(20);
                    Label currencyType = new AutoTooltipLabel(
                            CurrencyUtil.isFiatCurrency(item.getCode()) ? Res.get("shared.fiat") : Res.get("shared.crypto"));

                    currencyType.getStyleClass().add("currency-label-small");
                    Label currency = new AutoTooltipLabel(item.getCode());
                    currency.getStyleClass().add("currency-label");
                    Label offers = new AutoTooltipLabel(item.getName());
                    offers.getStyleClass().add("currency-label");

                    box.getChildren().addAll(currencyType, currency, offers);

                    Optional<Integer> offerCountOptional = Optional.ofNullable(offerCounts.get(code));

                    switch (code) {
                        case GUIUtil.SHOW_ALL_FLAG:
                            currencyType.setText(Res.get("shared.all"));
                            currency.setText(Res.get("list.currency.showAll"));
                            break;
                        case GUIUtil.EDIT_FLAG:
                            currencyType.setText(Res.get("shared.edit"));
                            currency.setText(Res.get("list.currency.editList"));
                            break;
                        default:
                            offerCountOptional.ifPresent(numOffer -> offers.setText(offers.getText() + " (" + numOffer + " " +
                                    (numOffer == 1 ? postFixSingle : postFixMulti) + ")"));
                    }

                    setGraphic(box);

                } else {
                    setGraphic(null);
                }
            }
        };
    }

    public static ListCell<PaymentMethod> getPaymentMethodButtonCell() {
        return new ListCell<>() {

            @Override
            protected void updateItem(PaymentMethod item, boolean empty) {
                super.updateItem(item, empty);

                if (item != null && !empty) {
                    String id = item.getId();

                    this.getStyleClass().add("currency-label-selected");

                    if (id.equals(GUIUtil.SHOW_ALL_FLAG)) {
                        setText(Res.get("list.currency.showAll"));
                    } else {
                        setText(Res.get(id));
                    }
                } else {
                    setText("");
                }
            }
        };
    }

    public static Callback<ListView<PaymentMethod>, ListCell<PaymentMethod>> getPaymentMethodCellFactory() {
        return p -> new ListCell<>() {
            @Override
            protected void updateItem(PaymentMethod method, boolean empty) {
                super.updateItem(method, empty);

                if (method != null && !empty) {
                    String id = method.getId();

                    HBox box = new HBox();
                    box.setSpacing(20);
                    Label paymentType = new AutoTooltipLabel(
                            method.isAsset() ? Res.get("shared.crypto") : Res.get("shared.fiat"));

                    paymentType.getStyleClass().add("currency-label-small");
                    Label paymentMethod = new AutoTooltipLabel(Res.get(id));
                    paymentMethod.getStyleClass().add("currency-label");
                    box.getChildren().addAll(paymentType, paymentMethod);

                    if (id.equals(GUIUtil.SHOW_ALL_FLAG)) {
                        paymentType.setText(Res.get("shared.all"));
                        paymentMethod.setText(Res.get("list.currency.showAll"));
                    }

                    setGraphic(box);

                } else {
                    setGraphic(null);
                }
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
        openWebPage(target, true);
    }

    public static void openWebPage(String target, boolean useReferrer) {
        if (useReferrer && target.contains("bisq.network")) {
            // add utm parameters
            target = appendURI(target, "utm_source=desktop-client&utm_medium=in-app-link&utm_campaign=language_" +
                    preferences.getUserLanguage());
        }

        String key = "warnOpenURLWhenTorEnabled";
        if (DontShowAgainLookup.showAgain(key)) {
            final String finalTarget = target;
            new Popup<>().information(Res.get("guiUtil.openWebBrowser.warning", target))
                    .actionButtonText(Res.get("guiUtil.openWebBrowser.doOpen"))
                    .onAction(() -> {
                        DontShowAgainLookup.dontShowAgain(key, true);
                        doOpenWebPage(finalTarget);
                    })
                    .closeButtonText(Res.get("guiUtil.openWebBrowser.copyUrl"))
                    .onClose(() -> Utilities.copyToClipboard(finalTarget))
                    .show();
        } else {
            doOpenWebPage(target);
        }
    }

    private static String appendURI(String uri, String appendQuery) {
        try {
            final URI oldURI = new URI(uri);

            String newQuery = oldURI.getQuery();

            if (newQuery == null) {
                newQuery = appendQuery;
            } else {
                newQuery += "&" + appendQuery;
            }

            URI newURI = new URI(oldURI.getScheme(), oldURI.getAuthority(), oldURI.getPath(),
                    newQuery, oldURI.getFragment());

            return newURI.toString();
        } catch (URISyntaxException e) {
            e.printStackTrace();
            log.error(e.getMessage());

            return uri;
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
        else
            log.warn("showNotReadyForTxBroadcastPopups called but no case matched. This should never happen if isReadyForTxBroadcast was called before.");
    }

    public static void showWantToBurnBTCPopup(Coin miningFee, Coin amount, BSFormatter btcFormatter) {
        new Popup<>().warning(Res.get("popup.warning.burnBTC", btcFormatter.formatCoinWithCode(miningFee),
                btcFormatter.formatCoinWithCode(amount))).show();
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
                        UserThread.runAfter(BisqApp.getShutDownHandler(), 100, TimeUnit.MILLISECONDS);
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
                    new Popup<>().feedback(Res.get("seed.restore.success")).hideCloseButton().show();
                    BisqApp.getShutDownHandler().run();
                }),
                throwable -> UserThread.execute(() -> {
                    log.error(throwable.toString());
                    new Popup<>().error(Res.get("seed.restore.error", Res.get("shared.errorMessageInline", throwable)))
                            .show();
                }));
    }

    public static void showSelectableTextModal(String title, String text) {
        TextArea textArea = new BisqTextArea();
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

    public static void removeChildrenFromGridPaneRows(GridPane gridPane, int start, int end) {
        Map<Integer, List<Node>> childByRowMap = new HashMap<>();
        gridPane.getChildren().forEach(child -> {
            final Integer rowIndex = GridPane.getRowIndex(child);
            childByRowMap.computeIfAbsent(rowIndex, key -> new ArrayList<>());
            childByRowMap.get(rowIndex).add(child);
        });

        for (int i = Math.min(start, childByRowMap.size()); i < Math.min(end + 1, childByRowMap.size()); i++) {
            List<Node> nodes = childByRowMap.get(i);
            if (nodes != null) {
                nodes.stream()
                        .filter(Objects::nonNull)
                        .filter(node -> gridPane.getChildren().contains(node))
                        .forEach(node -> gridPane.getChildren().remove(node));
            }
        }
    }

    public static void showBsqFeeInfoPopup(Coin fee, Coin miningFee, Coin btcForIssuance, int txSize, BsqFormatter bsqFormatter,
                                           BSFormatter btcFormatter, String type,
                                           Runnable actionHandler) {
        String confirmationMessage;

        if (btcForIssuance != null) {
            confirmationMessage = Res.get("dao.feeTx.issuanceProposal.confirm.details",
                    StringUtils.capitalize(type),
                    bsqFormatter.formatCoinWithCode(fee),
                    bsqFormatter.formatBTCWithCode(btcForIssuance),
                    100,
                    btcFormatter.formatCoinWithCode(miningFee),
                    CoinUtil.getFeePerByte(miningFee, txSize),
                    txSize / 1000d,
                    type);
        } else {
            confirmationMessage = Res.get("dao.feeTx.confirm.details",
                    StringUtils.capitalize(type),
                    bsqFormatter.formatCoinWithCode(fee),
                    btcFormatter.formatCoinWithCode(miningFee),
                    CoinUtil.getFeePerByte(miningFee, txSize),
                    txSize / 1000d,
                    type);
        }
        new Popup<>().headLine(Res.get("dao.feeTx.confirm", type))
                .confirmation(confirmationMessage)
                .actionButtonText(Res.get("shared.yes"))
                .onAction(actionHandler)
                .closeButtonText(Res.get("shared.cancel"))
                .show();
    }

    public static void showBsqFeeInfoPopup(Coin fee, Coin miningFee, int txSize, BsqFormatter bsqFormatter,
                                           BSFormatter btcFormatter, String type,
                                           Runnable actionHandler) {
        showBsqFeeInfoPopup(fee, miningFee, null, txSize, bsqFormatter, btcFormatter, type, actionHandler);
    }

    public static void setFitToRowsForTableView(TableView tableView, int rowHeight, int headerHeight, int minNumRows, int maxNumRows) {
        int size = tableView.getItems().size();
        int minHeight = rowHeight * minNumRows + headerHeight;
        int maxHeight = rowHeight * maxNumRows + headerHeight;
        checkArgument(maxHeight >= minHeight, "maxHeight cannot be smaller as minHeight");
        int height = Math.min(maxHeight, Math.max(minHeight, size * rowHeight + headerHeight));

        tableView.setPrefHeight(-1);
        tableView.setVisible(false);
        // We need to delay the setter to the next render frame as otherwise views don' get updated in some cases
        // Not 100% clear what causes that issue, but seems the requestLayout method is not called otherwise.
        // We still need to set the height immediately, otherwise some views render a incorrect layout.
        tableView.setPrefHeight(height);

        UserThread.execute(() -> {
            tableView.setPrefHeight(height);
            tableView.setVisible(true);
        });
    }

    public static Tuple2<ComboBox<TradeCurrency>, Integer> addRegionCountryTradeCurrencyComboBoxes(GridPane gridPane,
                                                                                                   int gridRow,
                                                                                                   Consumer<Country> onCountrySelectedHandler,
                                                                                                   Consumer<TradeCurrency> onTradeCurrencySelectedHandler) {
        gridRow = addRegionCountry(gridPane, gridRow, onCountrySelectedHandler);

        ComboBox<TradeCurrency> currencyComboBox = FormBuilder.addComboBox(gridPane, ++gridRow,
                Res.get("shared.currency"));
        currencyComboBox.setPromptText(Res.get("list.currency.select"));
        currencyComboBox.setItems(FXCollections.observableArrayList(CurrencyUtil.getAllSortedFiatCurrencies()));

        currencyComboBox.setConverter(new StringConverter<>() {
            @Override
            public String toString(TradeCurrency currency) {
                return currency.getNameAndCode();
            }

            @Override
            public TradeCurrency fromString(String string) {
                return null;
            }
        });
        currencyComboBox.setDisable(true);

        currencyComboBox.setOnAction(e ->
                onTradeCurrencySelectedHandler.accept(currencyComboBox.getSelectionModel().getSelectedItem()));

        return new Tuple2<>(currencyComboBox, gridRow);
    }

    public static int addRegionCountry(GridPane gridPane,
                                       int gridRow,
                                       Consumer<Country> onCountrySelectedHandler) {
        Tuple3<Label, ComboBox<bisq.core.locale.Region>, ComboBox<Country>> tuple3 = addTopLabelComboBoxComboBox(gridPane, ++gridRow, Res.get("payment.country"));

        ComboBox<bisq.core.locale.Region> regionComboBox = tuple3.second;
        regionComboBox.setPromptText(Res.get("payment.select.region"));
        regionComboBox.setConverter(new StringConverter<>() {
            @Override
            public String toString(bisq.core.locale.Region region) {
                return region.name;
            }

            @Override
            public bisq.core.locale.Region fromString(String s) {
                return null;
            }
        });
        regionComboBox.setItems(FXCollections.observableArrayList(CountryUtil.getAllRegions()));

        ComboBox<Country> countryComboBox = tuple3.third;
        countryComboBox.setVisibleRowCount(15);
        countryComboBox.setDisable(true);
        countryComboBox.setPromptText(Res.get("payment.select.country"));
        countryComboBox.setConverter(new StringConverter<>() {
            @Override
            public String toString(Country country) {
                return country.name + " (" + country.code + ")";
            }

            @Override
            public Country fromString(String s) {
                return null;
            }
        });

        regionComboBox.setOnAction(e -> {
            bisq.core.locale.Region selectedItem = regionComboBox.getSelectionModel().getSelectedItem();
            if (selectedItem != null) {
                countryComboBox.setDisable(false);
                countryComboBox.setItems(FXCollections.observableArrayList(CountryUtil.getAllCountriesForRegion(selectedItem)));
            }
        });

        countryComboBox.setOnAction(e ->
                onCountrySelectedHandler.accept(countryComboBox.getSelectionModel().getSelectedItem()));

        return gridRow;
    }

    @NotNull
    public static <T> ListCell<T> getComboBoxButtonCell(String title, ComboBox<T> comboBox) {
        return getComboBoxButtonCell(title, comboBox, true);
    }

    @NotNull
    public static <T> ListCell<T> getComboBoxButtonCell(String title, ComboBox<T> comboBox, Boolean hideOriginalPrompt) {
        return new ListCell<>() {
            @Override
            protected void updateItem(T item, boolean empty) {
                super.updateItem(item, empty);

                // See https://github.com/jfoenixadmin/JFoenix/issues/610
                if (hideOriginalPrompt)
                    this.setVisible(item != null || !empty);

                if (empty || item == null) {
                    setText(title);
                } else {
                    setText(comboBox.getConverter().toString(item));
                }
            }
        };
    }

    public static void openTxInBsqBlockExplorer(String txId, Preferences preferences) {
        if (txId != null)
            GUIUtil.openWebPage(preferences.getBsqBlockChainExplorer().txUrl + txId, false);
    }
}
