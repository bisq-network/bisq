/*
 * This file is part of Bitsquare.
 *
 * Bitsquare is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * Bitsquare is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Bitsquare. If not, see <http://www.gnu.org/licenses/>.
 */

package io.bitsquare.gui.util;

import com.google.common.base.Charsets;
import com.googlecode.jcsv.CSVStrategy;
import com.googlecode.jcsv.writer.CSVEntryConverter;
import com.googlecode.jcsv.writer.CSVWriter;
import com.googlecode.jcsv.writer.internal.CSVWriterBuilder;
import io.bitsquare.app.DevFlags;
import io.bitsquare.common.util.Utilities;
import io.bitsquare.gui.main.overlays.popups.Popup;
import io.bitsquare.locale.CryptoCurrency;
import io.bitsquare.locale.CurrencyUtil;
import io.bitsquare.locale.FiatCurrency;
import io.bitsquare.locale.TradeCurrency;
import io.bitsquare.payment.PaymentAccount;
import io.bitsquare.storage.Storage;
import io.bitsquare.user.Preferences;
import io.bitsquare.user.User;
import javafx.collections.ObservableList;
import javafx.geometry.Orientation;
import javafx.scene.Node;
import javafx.scene.control.ScrollBar;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.util.StringConverter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

public class GUIUtil {
    private static final Logger log = LoggerFactory.getLogger(GUIUtil.class);

    public final static String SHOW_ALL_FLAG = "SHOW_ALL_FLAG";
    public final static String EDIT_FLAG = "EDIT_FLAG";

    public static double getScrollbarWidth(Node scrollablePane) {
        Node node = scrollablePane.lookup(".scroll-bar");
        if (node instanceof ScrollBar) {
            final ScrollBar bar = (ScrollBar) node;
            if (bar.getOrientation().equals(Orientation.VERTICAL))
                return bar.getWidth();
        }
        return 0;
    }

    public static void showFeeInfoBeforeExecute(Runnable runnable) {
        String key = "miningFeeInfo";
        if (!DevFlags.DEV_MODE && Preferences.INSTANCE.showAgain(key)) {
            new Popup<>().information("Please be sure that the mining fee used at your external wallet is " +
                    "sufficiently high so that the funding transaction will be accepted by the miners.\n" +
                    "Otherwise the trade transactions cannot be confirmed and a trade would end up in a dispute.\n\n" +
                    "The recommended fee is about 0.0001 - 0.0002 BTC.\n\n" +
                    "You can view typically used fees at: https://tradeblock.com/blockchain")
                    .dontShowAgainId(key, Preferences.INSTANCE)
                    .onClose(runnable::run)
                    .closeButtonText("I understand")
                    .show();
        } else {
            runnable.run();
        }
    }


    public static void exportAccounts(ArrayList<PaymentAccount> accounts, String fileName, Preferences preferences, Stage stage) {
        if (!accounts.isEmpty()) {
            String directory = getDirectoryFormChooser(preferences, stage);
            Storage<ArrayList<PaymentAccount>> paymentAccountsStorage = new Storage<>(new File(directory));
            paymentAccountsStorage.initAndGetPersisted(accounts, fileName);
            paymentAccountsStorage.queueUpForSave();
            new Popup<>().feedback("Payment accounts saved to path:\n" + Paths.get(directory, fileName).toAbsolutePath()).show();
        } else {
            new Popup<>().warning("You don't have payment accounts set up for exporting.").show();
        }
    }

    public static void importAccounts(User user, String fileName, Preferences preferences, Stage stage) {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setInitialDirectory(new File(preferences.getDefaultPath()));
        fileChooser.setTitle("Select path to " + fileName);
        File file = fileChooser.showOpenDialog(stage.getOwner());
        if (file != null) {
            String path = file.getAbsolutePath();
            if (Paths.get(path).getFileName().toString().equals(fileName)) {
                String directory = Paths.get(path).getParent().toString();
                preferences.setDefaultPath(directory);
                Storage<ArrayList<PaymentAccount>> paymentAccountsStorage = new Storage<>(new File(directory));
                ArrayList<PaymentAccount> persisted = paymentAccountsStorage.initAndGetPersistedWithFileName(fileName);
                if (persisted != null) {
                    final StringBuilder msg = new StringBuilder();
                    persisted.stream().forEach(paymentAccount -> {
                        final String id = paymentAccount.getId();
                        if (user.getPaymentAccount(id) == null) {
                            user.addPaymentAccount(paymentAccount);
                            msg.append("Payment account with id ").append(id).append("\n");
                        } else {
                            msg.append("We did not import payment account with id ").append(id).append(" because it exists already.\n");
                        }
                    });
                    new Popup<>().feedback("Payment account imported from path:\n" + path + "\n\nImported accounts:\n" + msg).show();

                } else {
                    new Popup<>().warning("No exported payment accounts has been found at path: " + path + ".\n" + "File name is " + fileName + ".").show();
                }
            } else {
                new Popup<>().warning("The selected file is not the expected file for import. The expected file name is: " + fileName + ".").show();
            }
        }
    }


    public static <T> void exportCSV(String fileName, CSVEntryConverter<T> headerConverter,
                                     CSVEntryConverter<T> contentConverter, T emptyItem,
                                     List<T> list, Stage stage) {

        FileChooser fileChooser = new FileChooser();
        fileChooser.setInitialFileName(fileName);
        File file = fileChooser.showSaveDialog(stage);
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
            new Popup().error("Exporting to CSV failed because of an error.\n" +
                    "Error = " + e.getMessage());
        }
    }

    public static String getDirectoryFormChooser(Preferences preferences, Stage stage) {
        DirectoryChooser directoryChooser = new DirectoryChooser();
        directoryChooser.setInitialDirectory(new File(preferences.getDefaultPath()));
        directoryChooser.setTitle("Select export path");
        File dir = directoryChooser.showDialog(stage);
        if (dir != null) {
            String directory = dir.getAbsolutePath();
            preferences.setDefaultPath(directory);
            return directory;
        } else {
            return "";
        }
    }

    public static StringConverter<CurrencyListItem> getCurrencyListItemConverter(String postFix, Preferences preferences) {
        return new StringConverter<CurrencyListItem>() {
            @Override
            public String toString(CurrencyListItem item) {
                TradeCurrency tradeCurrency = item.tradeCurrency;
                String code = tradeCurrency.getCode();
                // http://boschista.deviantart.com/journal/Cool-ASCII-Symbols-214218618
                if (code.equals(GUIUtil.SHOW_ALL_FLAG))
                    return "▶ Show all";
                else if (code.equals(GUIUtil.EDIT_FLAG))
                    return "▼ Edit currency list";
                else {
                    String displayString = CurrencyUtil.getNameByCode(code) + " (" + code + ")";
                    if (preferences.getSortMarketCurrenciesNumerically())
                        displayString += " - " + item.numTrades + " " + postFix;
                    if (tradeCurrency instanceof FiatCurrency)
                        return "★ " + displayString;
                    else if (tradeCurrency instanceof CryptoCurrency) {
                        return "✦ " + displayString;
                    } else
                        return "-";
                }
            }

            @Override
            public CurrencyListItem fromString(String s) {
                return null;
            }
        };
    }

    public static void fillCurrencyListItems(List<TradeCurrency> tradeCurrencyList, ObservableList<CurrencyListItem> currencyListItems, @Nullable CurrencyListItem showAllCurrencyListItem, Preferences preferences) {
        Set<TradeCurrency> tradeCurrencySet = new HashSet<>();
        Map<String, Integer> tradesPerCurrencyMap = new HashMap<>();
        tradeCurrencyList.stream().forEach(tradeCurrency -> {
            tradeCurrencySet.add(tradeCurrency);
            String code = tradeCurrency.getCode();
            if (tradesPerCurrencyMap.containsKey(code))
                tradesPerCurrencyMap.put(code, tradesPerCurrencyMap.get(code) + 1);
            else
                tradesPerCurrencyMap.put(code, 1);
        });

        List<CurrencyListItem> list = tradeCurrencySet.stream()
                .filter(e -> CurrencyUtil.isFiatCurrency(e.getCode()))
                .map(e -> new CurrencyListItem(e, tradesPerCurrencyMap.get(e.getCode())))
                .collect(Collectors.toList());
        List<CurrencyListItem> cryptoList = tradeCurrencySet.stream()
                .filter(e -> CurrencyUtil.isCryptoCurrency(e.getCode()))
                .map(e -> new CurrencyListItem(e, tradesPerCurrencyMap.get(e.getCode())))
                .collect(Collectors.toList());

        if (preferences.getSortMarketCurrenciesNumerically()) {
            list.sort((o1, o2) -> new Integer(o2.numTrades).compareTo(o1.numTrades));
            cryptoList.sort((o1, o2) -> new Integer(o2.numTrades).compareTo(o1.numTrades));
        } else {
            list.sort((o1, o2) -> o1.tradeCurrency.compareTo(o2.tradeCurrency));
            cryptoList.sort((o1, o2) -> o1.tradeCurrency.compareTo(o2.tradeCurrency));
        }

        list.addAll(cryptoList);

        if (showAllCurrencyListItem != null)
            list.add(0, showAllCurrencyListItem);

        currencyListItems.setAll(list);
    }

    public static void openWebPage(String target) {
        String key = "warnOpenURLWhenTorEnabled";
        final Preferences preferences = Preferences.INSTANCE;
        if (preferences.getUseTorForHttpRequests() && preferences.showAgain(key)) {
            new Popup<>().information("You have Tor enabled for Http requests and are going to open a web page " +
                    "in your system web browser.\n" +
                    "Do you want to open the web page now?\n\n" +
                    "If you are not using the \"Tor Browser\" as your default system web browser you " +
                    "will connect to the web page in clear net.\n\n" +
                    "URL: \"" + target)
                    .actionButtonText("Open the web page and don't ask again")
                    .onAction(() -> {
                        preferences.dontShowAgain(key, true);
                        doOpenWebPage(target);
                    })
                    .closeButtonText("Copy URL and cancel")
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
}
