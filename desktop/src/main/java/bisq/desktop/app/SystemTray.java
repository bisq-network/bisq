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

package bisq.desktop.app;

import bisq.desktop.util.GUIUtil;
import bisq.desktop.util.ImageUtil;

import bisq.core.exceptions.BisqException;
import bisq.core.locale.Res;

import bisq.common.UserThread;
import bisq.common.util.Utilities;

import javafx.application.Platform;

import javafx.stage.Stage;

import java.awt.AWTException;
import java.awt.Image;
import java.awt.MenuItem;
import java.awt.PopupMenu;
import java.awt.TrayIcon;
import java.awt.image.BufferedImage;

import javax.imageio.ImageIO;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * There is no JavaFX support yet, so we need to use AWT.
 */
public class SystemTray {
    private static final Logger log = LoggerFactory.getLogger(SystemTray.class);

    private static final String ICON_HI_RES = "/images/system_tray_icon@2x.png";
    private static final String ICON_LO_RES = "/images/system_tray_icon.png";
    private static final String ICON_WINDOWS_LO_RES = "/images/system_tray_icon_windows.png";
    private static final String ICON_WINDOWS_HI_RES = "/images/system_tray_icon_windows@2x.png";
    private static final String ICON_LINUX = "/images/system_tray_icon_linux.png";


    private static final String SHOW_WINDOW_LABEL = Res.get("systemTray.show");
    private static final String HIDE_WINDOW_LABEL = Res.get("systemTray.hide");

    private final Stage stage;
    private final Runnable onExit;
    private final MenuItem toggleShowHideItem = new MenuItem(HIDE_WINDOW_LABEL);

    public static void create(Stage stage, Runnable onExit) {
        new SystemTray(stage, onExit);
    }

    private SystemTray(Stage stage, Runnable onExit) {
        this.stage = stage;
        this.onExit = onExit;
        init();
    }

    private void init() {
        if (!java.awt.SystemTray.isSupported()) {
            log.error("System tray is not supported.");
            return;
        }

        // prevent exiting the app when the last window gets closed
        // For now we allow to close the app by closing the window.
        // Later we only let it close via the system trays exit.
        Platform.setImplicitExit(false);

        MenuItem aboutItem = new MenuItem(Res.get("systemTray.info"));
        MenuItem exitItem = new MenuItem(Res.get("systemTray.exit"));

        PopupMenu popupMenu = new PopupMenu();
        popupMenu.add(aboutItem);
        popupMenu.addSeparator();
        popupMenu.add(toggleShowHideItem);
        popupMenu.addSeparator();
        popupMenu.add(exitItem);

        String path;
        if (Utilities.isOSX())
            path = ImageUtil.isRetina() ? ICON_HI_RES : ICON_LO_RES;
        else if (Utilities.isWindows())
            path = ImageUtil.isRetina() ? ICON_WINDOWS_HI_RES : ICON_WINDOWS_LO_RES;
        else
            path = ICON_LINUX;

        try {
            BufferedImage trayIconImage = ImageIO.read(getClass().getResource(path));
            TrayIcon trayIcon = new TrayIcon(trayIconImage);
            // On Windows and Linux the icon needs to be resized
            // On macOS we get the correct size from the provided image
            if (!Utilities.isOSX()) {
                if (ImageUtil.isRetina()) {
                    // Using auto sizing provides better results with high resolution
                    trayIcon.setImageAutoSize(true);
                } else {
                    // Using scaling provides better results with low resolution
                    trayIcon = new TrayIcon(trayIconImage.getScaledInstance(trayIcon.getSize().width, -1, Image.SCALE_SMOOTH));
                }
            }

            trayIcon.setPopupMenu(popupMenu);
            trayIcon.setToolTip(Res.get("systemTray.tooltip"));

            java.awt.SystemTray.getSystemTray().add(trayIcon);
        } catch (AWTException e1) {
            log.error("Icon could not be added to system tray.", e1);
        } catch (IOException e2) {
            throw new BisqException(e2);
        }

        toggleShowHideItem.addActionListener(e -> {
            if (stage.isShowing()) {
                toggleShowHideItem.setLabel(SHOW_WINDOW_LABEL);
                UserThread.execute(stage::hide);
            } else {
                toggleShowHideItem.setLabel(HIDE_WINDOW_LABEL);
                UserThread.execute(stage::show);
            }
        });

        aboutItem.addActionListener(e -> {
            try {
                UserThread.execute(() -> GUIUtil.openWebPage("https://bisq.network"));
            } catch (Exception e1) {
                e1.printStackTrace();
            }
        });
        exitItem.addActionListener(e -> UserThread.execute(onExit::run));
    }

    public void hideStage() {
        stage.hide();
        toggleShowHideItem.setLabel(SHOW_WINDOW_LABEL);
    }

}
