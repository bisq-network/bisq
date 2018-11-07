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

package bisq.relay;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

import java.util.Scanner;
import java.util.concurrent.ExecutionException;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;



import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.Notification;
import com.turo.pushy.apns.ApnsClient;
import com.turo.pushy.apns.ApnsClientBuilder;
import com.turo.pushy.apns.PushNotificationResponse;
import com.turo.pushy.apns.util.ApnsPayloadBuilder;
import com.turo.pushy.apns.util.SimpleApnsPushNotification;
import com.turo.pushy.apns.util.concurrent.PushNotificationFuture;

class RelayService {
    private static final Logger log = LoggerFactory.getLogger(RelayMain.class);
    private static final String ANDROID_DATABASE_URL = "https://bisqnotifications.firebaseio.com";
    // Used in Bisq app to check for success state. We won't want a code dependency just for that string so we keep it
    // duplicated in core and here. Must not be changed.
    private static final String SUCCESS = "success";

    private final String appleBundleId;

    private ApnsClient productionApnsClient;
    private ApnsClient devApnsClient; // used for iOS development in XCode

    RelayService(String appleCertPwPath, String appleCertPath, String appleBundleId, String androidCertPath) {
        this.appleBundleId = appleBundleId;

        setupForAndroid(androidCertPath);
        setupForApple(appleCertPwPath, appleCertPath);
    }

    private void setupForAndroid(String androidCertPath) {
        try {
            InputStream androidCertStream = new FileInputStream(androidCertPath);
            FirebaseOptions options = new FirebaseOptions.Builder()
                .setCredentials(GoogleCredentials.fromStream(androidCertStream))
                .setDatabaseUrl(ANDROID_DATABASE_URL)
                .build();
            FirebaseApp.initializeApp(options);
        } catch (IOException e) {
            log.error(e.toString());
            e.printStackTrace();
        }
    }

    private void setupForApple(String appleCertPwPath, String appleCertPath) {
        try {
            InputStream certInputStream = new FileInputStream(appleCertPwPath);
            Scanner scanner = new Scanner(certInputStream);
            String password = scanner.next();
            productionApnsClient = new ApnsClientBuilder()
                .setApnsServer(ApnsClientBuilder.PRODUCTION_APNS_HOST)
                .setClientCredentials(new File(appleCertPath), password)
                .build();
            devApnsClient = new ApnsClientBuilder()
                .setApnsServer(ApnsClientBuilder.DEVELOPMENT_APNS_HOST)
                .setClientCredentials(new File(appleCertPath), password)
                .build();
        } catch (IOException e) {
            log.error(e.toString());
            e.printStackTrace();
        }
    }

    String sendAppleMessage(boolean isProduction, boolean isContentAvailable, String apsTokenHex, String encryptedMessage, boolean useSound) {
        ApnsPayloadBuilder payloadBuilder = new ApnsPayloadBuilder();
        if (useSound)
            payloadBuilder.setSoundFileName("default");
        payloadBuilder.setAlertBody("Bisq notification");
        payloadBuilder.setContentAvailable(isContentAvailable);
        payloadBuilder.addCustomProperty("encrypted", encryptedMessage);
        final String payload = payloadBuilder.buildWithDefaultMaximumLength();
        log.info("payload " + payload);
        SimpleApnsPushNotification simpleApnsPushNotification = new SimpleApnsPushNotification(apsTokenHex, appleBundleId, payload);

        ApnsClient apnsClient = isProduction ? productionApnsClient : devApnsClient;
        PushNotificationFuture<SimpleApnsPushNotification, PushNotificationResponse<SimpleApnsPushNotification>>
            notificationFuture = apnsClient.sendNotification(simpleApnsPushNotification);
        try {
            PushNotificationResponse<SimpleApnsPushNotification> pushNotificationResponse = notificationFuture.get();
            if (pushNotificationResponse.isAccepted()) {
                log.info("Push notification accepted by APNs gateway.");
                return SUCCESS;
            } else {
                String msg1 = "Notification rejected by the APNs gateway: " +
                    pushNotificationResponse.getRejectionReason();
                String msg2 = "";
                if (pushNotificationResponse.getTokenInvalidationTimestamp() != null)
                    msg2 = " and the token is invalid as of " +
                        pushNotificationResponse.getTokenInvalidationTimestamp();

                log.info(msg1 + msg2);
                return "Error: " + msg1 + msg2;
            }
        } catch (InterruptedException | ExecutionException e) {
            log.error(e.toString());
            e.printStackTrace();
            return "Error: " + e.toString();
        }
    }

    String sendAndroidMessage(String apsTokenHex, String encryptedMessage, boolean useSound) {
        Message.Builder messageBuilder = Message.builder();
        Notification notification = new Notification("Bisq", "Notification");
        messageBuilder.setNotification(notification);
        messageBuilder.putData("encrypted", encryptedMessage);
        messageBuilder.setToken(apsTokenHex);
        if (useSound)
            messageBuilder.putData("sound", "default");
        Message message = messageBuilder.build();
        try {
            FirebaseMessaging firebaseMessaging = FirebaseMessaging.getInstance();
            firebaseMessaging.send(message);
            return SUCCESS;
        } catch (FirebaseMessagingException e) {
            log.error(e.toString());
            e.printStackTrace();
            return "Error: " + e.toString();
        }
    }
}
