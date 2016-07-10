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
import io.bitsquare.gui.main.overlays.popups.Popup;
import io.bitsquare.payment.PaymentAccount;
import io.bitsquare.storage.Storage;
import io.bitsquare.user.Preferences;
import io.bitsquare.user.User;
import javafx.geometry.Orientation;
import javafx.scene.Node;
import javafx.scene.control.ScrollBar;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class GUIUtil {
    private static final Logger log = LoggerFactory.getLogger(GUIUtil.class);

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
            paymentAccountsStorage.queueUpForSave(20);
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
                ArrayList<PaymentAccount> persisted = paymentAccountsStorage.initAndGetPersisted(fileName);
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
}
