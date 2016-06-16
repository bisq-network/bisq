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

import com.sun.javafx.tk.quantum.QuantumToolkit;
import io.bitsquare.gui.main.overlays.editor.PeerInfoWithTagEditor;
import io.bitsquare.locale.Country;
import io.bitsquare.user.Preferences;
import javafx.geometry.Point2D;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Map;
import java.util.UUID;


public class ImageUtil {
    private static final Logger log = LoggerFactory.getLogger(ImageUtil.class);

    public static final String REMOVE_ICON = "image-remove";

    public static ImageView getImageViewById(String id) {
        ImageView imageView = new ImageView();
        imageView.setId(id);
        return imageView;
    }

    private static Image getImageByUrl(String url) {
        return new Image(ImageUtil.class.getResourceAsStream(url));
    }

    private static ImageView getImageViewByUrl(String url) {
        return new ImageView(getImageByUrl(url));
    }

    public static ImageView getCountryIconImageView(Country country) {
        try {
            return ImageUtil.getImageViewByUrl("/images/countries/" + country.code.toLowerCase() + ".png");
        } catch (Exception e) {
            log.error("Country icon not found URL = /images/countries/" + country.code.toLowerCase() +
                    ".png / country name = " + country.name);
            return null;
        }
    }

    public static boolean isRetina() {
        float maxRenderScale = ((QuantumToolkit) QuantumToolkit.getToolkit()).getMaxRenderScale();
        boolean isRetina = maxRenderScale > 1.9f;
        log.info("isRetina=" + isRetina + " / maxRenderScale=" + maxRenderScale);
        return isRetina;
    }

    public static Node getIdentIcon(String hostName, String tooltipText, int numTrades) {
        if (!hostName.isEmpty()) {
            // for testing locally we use a random hostname to get dif. colors
            if (hostName.startsWith("localhost"))
                hostName = UUID.randomUUID().toString().replace("-", "").substring(0, 16);

            int maxIndices = 15;
            try {
                MessageDigest md = MessageDigest.getInstance("SHA1");
                byte[] bytes = md.digest(hostName.getBytes());
                int intValue = Math.abs(((bytes[0] & 0xFF) << 24) | ((bytes[1] & 0xFF) << 16)
                        | ((bytes[2] & 0xFF) << 8) | (bytes[3] & 0xFF));

                int index = (intValue % maxIndices) + 1;
                double saturation = (intValue % 1000) / 1000d;
                int red = (intValue >> 8) % 256;
                int green = (intValue >> 16) % 256;
                int blue = (intValue >> 24) % 256;

                ImageView iconView = new ImageView();
                iconView.setId("avatar_" + index);
                iconView.setScaleX(intValue % 2 == 0 ? 1d : -1d);
                double size = 26;

                Group group = new Group();
                Map<String, String> peerTagMap = Preferences.INSTANCE.getPeerTagMap();
                String tag;
                if (peerTagMap.containsKey(hostName)) {
                    tag = peerTagMap.get(hostName);
                    Tooltip.install(group, new Tooltip(tooltipText + "\nTag: " + tag));
                } else {
                    tag = "";
                    Tooltip.install(group, new Tooltip(tooltipText));
                }

                Pane numTradesPane = new Pane();
                numTradesPane.relocate(16, 16);
                numTradesPane.setMouseTransparent(true);
                ImageView icon = new ImageView();
                Label label = new Label();
                label.relocate(5, 1);
                label.setId("ident-num-label");
                icon.setLayoutX(0.5);
                numTradesPane.getChildren().addAll(icon, label);

                updatePeerInfoIcon(numTrades, tag, numTradesPane, label, icon);

                Canvas bg = new Canvas(size, size);
                GraphicsContext gc = bg.getGraphicsContext2D();

                Color color = Color.rgb(red, green, blue);
                color = color.deriveColor(1, saturation, 1, 1); // reduce saturation
                gc.setFill(color);
                gc.fillOval(0, 0, size, size);
                bg.setLayoutY(1);
                group.getChildren().addAll(bg, iconView, numTradesPane);

                final String finalHostName = hostName;
                group.setOnMouseClicked(e -> {
                    new PeerInfoWithTagEditor()
                            .hostName(finalHostName)
                            .numTrades(numTrades)
                            .position(group.localToScene(new Point2D(0, 0)))
                            .onSave(newTag -> {
                                Preferences.INSTANCE.setTagForPeer(finalHostName, newTag);
                                if (!newTag.isEmpty())
                                    Tooltip.install(group, new Tooltip(tooltipText + "\nTag: " + newTag));
                                else
                                    Tooltip.install(group, new Tooltip(tooltipText));
                                updatePeerInfoIcon(numTrades, newTag, numTradesPane, label, icon);
                            })
                            .show();
                });

                return group;
            } catch (NoSuchAlgorithmException e) {
                e.printStackTrace();
                log.error(e.toString());
                return null;
            }
        } else {
            return null;
        }
    }

    private static void updatePeerInfoIcon(int numTrades, String tag, Pane numTradesPane, Label label, ImageView icon) {
        if (numTrades == 0 && tag.isEmpty())
            label.setText("");
        else if (numTrades == 0)
            label.setText(tag.substring(0, 1));
        else if (numTrades > 9)
            label.setText("★");
        else
            label.setText(String.valueOf(numTrades));

        numTradesPane.setVisible(!label.getText().isEmpty());
        numTradesPane.setManaged(numTradesPane.isVisible());

        icon.setId(numTrades > 0 ? "image-green_circle" : "image-blue_circle");
    }
}
