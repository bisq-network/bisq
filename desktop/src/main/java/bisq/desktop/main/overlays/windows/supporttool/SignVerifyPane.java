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
import bisq.desktop.components.BisqTextArea;
import bisq.desktop.components.InputTextField;
import bisq.desktop.main.overlays.popups.Popup;

import bisq.core.btc.wallet.WalletsManager;

import bisq.common.config.Config;

import org.bitcoinj.core.Address;
import org.bitcoinj.core.ECKey;
import org.bitcoinj.core.Utils;
import org.bitcoinj.script.Script;

import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.layout.HBox;

import javafx.geometry.Pos;

import java.security.SignatureException;

import lombok.extern.slf4j.Slf4j;

import static bisq.desktop.util.FormBuilder.addInputTextField;

@Slf4j
public class SignVerifyPane extends CommonPane {

    SignVerifyPane(WalletsManager walletsManager) {
        int rowIndexB = 0;
        TextArea messageText = new BisqTextArea();
        messageText.setPromptText("Message");
        messageText.setEditable(true);
        messageText.setWrapText(true);
        messageText.setPrefSize(800, 150);
        add(messageText, 0, ++rowIndexB);
        add(new Label(""), 0, ++rowIndexB);  // spacer
        InputTextField address = addInputTextField(this, ++rowIndexB, "Address");
        add(new Label(""), 0, ++rowIndexB);  // spacer
        TextArea messageSig = new BisqTextArea();
        messageSig.setPromptText("Signature");
        messageSig.setEditable(true);
        messageSig.setWrapText(true);
        messageSig.setPrefSize(800, 65);
        add(messageSig, 0, ++rowIndexB);
        add(new Label(""), 0, ++rowIndexB);  // spacer
        Button buttonSign = new AutoTooltipButton("Sign");
        Button buttonVerify = new AutoTooltipButton("Verify");
        HBox buttonBox = new HBox(12, buttonSign, buttonVerify);
        buttonBox.setAlignment(Pos.BASELINE_CENTER);
        buttonBox.setPrefWidth(800);
        add(buttonBox, 0, ++rowIndexB);

        buttonSign.setOnAction(e -> {
            String walletInfo = walletsManager.getWalletsAsString(true);
            String privKeyHex = findPrivForPubOrAddress(walletInfo, address.getText());
            if (privKeyHex == null) {
                messageSig.setText("");
                new Popup().information("Key not found in wallet").show();
            } else {
                ECKey myPrivateKey = ECKey.fromPrivate(Utils.HEX.decode(privKeyHex));
                String signatureBase64 = myPrivateKey.signMessage(messageText.getText());
                messageSig.setText(signatureBase64);
            }
        });
        buttonVerify.setOnAction(e -> {
            try {
                ECKey key = ECKey.signedMessageToKey(messageText.getText(), messageSig.getText());
                Address address1 = Address.fromKey(Config.baseCurrencyNetworkParameters(), key, Script.ScriptType.P2PKH);
                Address address2 = Address.fromKey(Config.baseCurrencyNetworkParameters(), key, Script.ScriptType.P2WPKH);
                if (address.getText().equalsIgnoreCase(address1.toString()) ||
                        address.getText().equalsIgnoreCase(address2.toString())) {
                    new Popup().information("Signature verified").show();
                } else {
                    new Popup().warning("Wrong signature").show();
                }
            } catch (SignatureException ex) {
                log.warn(ex.toString());
                new Popup().warning("Wrong signature").show();
            }
        });
    }

    @Override
    public String getName() {
        return "Sign/Verify Msg";
    }
}
