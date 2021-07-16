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

package bisq.core.notifications;

import bisq.core.user.Preferences;

import bisq.network.http.HttpClient;

import bisq.common.UserThread;
import bisq.common.app.Version;
import bisq.common.config.Config;
import bisq.common.util.Utilities;

import com.google.gson.Gson;

import com.google.inject.Inject;
import com.google.inject.Singleton;

import javax.inject.Named;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;

import org.apache.commons.codec.binary.Hex;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;

import java.util.UUID;
import java.util.function.Consumer;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import org.jetbrains.annotations.NotNull;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

@Slf4j
@Singleton
public class MobileNotificationService {
    // Used in Relay app to response of a success state. We won't want a code dependency just for that string so we keep it
    // duplicated in relay and here. Must not be changed.
    private static final String SUCCESS = "success";
    private static final String DEV_URL_LOCALHOST = "http://localhost:8080/";
    private static final String DEV_URL = "http://172.105.9.31:8080/";
    private static final String URL = "http://bisqpushv56wo32w2dv7xacvvcrnow6gud5vkvftwsgevczmspzvqgad.onion/";
    private static final String BISQ_MESSAGE_IOS_MAGIC = "BisqMessageiOS";
    private static final String BISQ_MESSAGE_ANDROID_MAGIC = "BisqMessageAndroid";

    private final Preferences preferences;
    private final MobileMessageEncryption mobileMessageEncryption;
    private final MobileNotificationValidator mobileNotificationValidator;
    private final HttpClient httpClient;

    private final ListeningExecutorService executorService = Utilities.getListeningExecutorService(
            "MobileNotificationService", 10, 15, 10 * 60);
    @Getter
    private final MobileModel mobileModel;

    @Getter
    private boolean setupConfirmationSent;
    @Getter
    private BooleanProperty useSoundProperty = new SimpleBooleanProperty();
    @Getter
    private BooleanProperty useTradeNotificationsProperty = new SimpleBooleanProperty();
    @Getter
    private BooleanProperty useMarketNotificationsProperty = new SimpleBooleanProperty();
    @Getter
    private BooleanProperty usePriceNotificationsProperty = new SimpleBooleanProperty();

    @Inject
    public MobileNotificationService(Preferences preferences,
                                     MobileMessageEncryption mobileMessageEncryption,
                                     MobileNotificationValidator mobileNotificationValidator,
                                     MobileModel mobileModel,
                                     HttpClient httpClient,
                                     @Named(Config.USE_LOCALHOST_FOR_P2P) boolean useLocalHost) {
        this.preferences = preferences;
        this.mobileMessageEncryption = mobileMessageEncryption;
        this.mobileNotificationValidator = mobileNotificationValidator;
        this.httpClient = httpClient;
        this.mobileModel = mobileModel;

        // httpClient.setBaseUrl(useLocalHost ? DEV_URL_LOCALHOST : URL);

        httpClient.setBaseUrl(useLocalHost ? DEV_URL : URL);
        httpClient.setIgnoreSocks5Proxy(false);
    }

    public void onAllServicesInitialized() {
        String keyAndToken = preferences.getPhoneKeyAndToken();
        if (mobileNotificationValidator.isValid(keyAndToken)) {
            setupConfirmationSent = true;
            mobileModel.applyKeyAndToken(keyAndToken);
            mobileMessageEncryption.setKey(mobileModel.getKey());
        }
        useTradeNotificationsProperty.set(preferences.isUseTradeNotifications());
        useMarketNotificationsProperty.set(preferences.isUseMarketNotifications());
        usePriceNotificationsProperty.set(preferences.isUsePriceNotifications());
        useSoundProperty.set(preferences.isUseSoundForMobileNotifications());
    }

    public boolean sendMessage(MobileMessage message) throws Exception {
        return sendMessage(message, useSoundProperty.get());
    }

    public boolean applyKeyAndToken(String keyAndToken) {
        if (mobileNotificationValidator.isValid(keyAndToken)) {
            mobileModel.applyKeyAndToken(keyAndToken);
            mobileMessageEncryption.setKey(mobileModel.getKey());
            preferences.setPhoneKeyAndToken(keyAndToken);
            if (!setupConfirmationSent) {
                try {
                    sendConfirmationMessage();
                    setupConfirmationSent = true;
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            return true;
        } else {
            return false;
        }
    }

    /**
     *
     * @param message           The message to send
     * @param useSound          If a sound should be used on the mobile device.
     * @return Returns true if the message was sent. It does not reflect if the sending was successful.
     *                          The result and error handlers carry that information.
     * @throws Exception
     */
    public boolean sendMessage(MobileMessage message,
                               boolean useSound) throws Exception {
        return sendMessage(message, useSound,
                result -> log.debug("sendMessage result=" + result),
                throwable -> log.error("sendMessage failed. throwable=" + throwable.toString()));
    }

    /**
     *
     * @param message           The message to send
     * @param useSound          If a sound should be used on the mobile device.
     * @param resultHandler     The result of the send operation (sent on a custom thread)
     * @param errorHandler      Carries the throwable if an error occurred at sending (sent on a custom thread)
     * @return Returns true if the message was sent. It does not reflect if the sending was successful.
     *                          The result and error handlers carry that information.
     * @throws Exception
     */
    private boolean sendMessage(MobileMessage message,
                                boolean useSound,
                                Consumer<String> resultHandler,
                                Consumer<Throwable> errorHandler) throws Exception {
        if (mobileModel.getKey() == null)
            return false;

        boolean doSend;
        switch (message.getMobileMessageType()) {
            case SETUP_CONFIRMATION:
                doSend = true;
                break;
            case OFFER:
            case TRADE:
            case DISPUTE:
                doSend = useTradeNotificationsProperty.get();
                break;
            case PRICE:
                doSend = usePriceNotificationsProperty.get();
                break;
            case MARKET:
                doSend = useMarketNotificationsProperty.get();
                break;
            case ERASE:
                doSend = true;
                break;
            default:
                doSend = false;
        }

        if (!doSend)
            return false;

        log.info("Send message: '{}'", message.getMessage());


        log.info("sendMessage message={}", message);
        Gson gson = new Gson();
        String json = gson.toJson(message);
        log.info("json " + json);

        StringBuilder padded = new StringBuilder(json);
        while (padded.length() % 16 != 0) {
            padded.append(" ");
        }
        json = padded.toString();

        // generate 16 random characters for iv
        String uuid = UUID.randomUUID().toString();
        uuid = uuid.replace("-", "");
        String iv = uuid.substring(0, 16);

        String cipher = mobileMessageEncryption.encrypt(json, iv);
        log.info("key = " + mobileModel.getKey());
        log.info("iv = " + iv);
        log.info("encryptedJson = " + cipher);

        doSendMessage(iv, cipher, useSound, resultHandler, errorHandler);
        return true;
    }

    public void sendEraseMessage() throws Exception {
        MobileMessage message = new MobileMessage("",
                "",
                MobileMessageType.ERASE);
        sendMessage(message, false);
    }

    public void reset() {
        mobileModel.reset();
        preferences.setPhoneKeyAndToken(null);
        setupConfirmationSent = false;
    }


    private void sendConfirmationMessage() throws Exception {
        log.info("sendConfirmationMessage");
        MobileMessage message = new MobileMessage("",
                "",
                MobileMessageType.SETUP_CONFIRMATION);
        sendMessage(message, true);
    }

    private void doSendMessage(String iv,
                               String cipher,
                               boolean useSound,
                               Consumer<String> resultHandler,
                               Consumer<Throwable> errorHandler) throws Exception {
        if (httpClient.hasPendingRequest()) {
            log.warn("We have a pending request open. We ignore that request. httpClient {}", httpClient);
            return;
        }

        String msg;
        if (mobileModel.getOs() == null)
            throw new RuntimeException("No mobileModel OS set");

        switch (mobileModel.getOs()) {
            case IOS:
                msg = BISQ_MESSAGE_IOS_MAGIC;
                break;
            case IOS_DEV:
                msg = BISQ_MESSAGE_IOS_MAGIC;
                break;
            case ANDROID:
                msg = BISQ_MESSAGE_ANDROID_MAGIC;
                break;
            case UNDEFINED:
            default:
                throw new RuntimeException("No mobileModel OS set");
        }
        msg += MobileModel.PHONE_SEPARATOR_WRITING + iv + MobileModel.PHONE_SEPARATOR_WRITING + cipher;
        boolean isAndroid = mobileModel.getOs() == MobileModel.OS.ANDROID;
        boolean isProduction = mobileModel.getOs() == MobileModel.OS.IOS;

        checkNotNull(mobileModel.getToken(), "mobileModel.getToken() must not be null");
        String tokenAsHex = Hex.encodeHexString(mobileModel.getToken().getBytes("UTF-8"));
        String msgAsHex = Hex.encodeHexString(msg.getBytes("UTF-8"));
        String param = "relay?" +
                "isAndroid=" + isAndroid +
                "&isProduction=" + isProduction +
                "&isContentAvailable=" + mobileModel.isContentAvailable() +
                "&snd=" + useSound +
                "&token=" + tokenAsHex + "&" +
                "msg=" + msgAsHex;

        log.info("Send: token={}", mobileModel.getToken());
        log.info("Send: msg={}", msg);
        log.info("Send: isAndroid={}\nuseSound={}\ntokenAsHex={}\nmsgAsHex={}",
                isAndroid, useSound, tokenAsHex, msgAsHex);

        String threadName = "sendMobileNotification-" + msgAsHex.substring(0, 5) + "...";
        ListenableFuture<String> future = executorService.submit(() -> {
            Thread.currentThread().setName(threadName);
            String result = httpClient.get(param, "User-Agent",
                    "bisq/" + Version.VERSION + ", uid:" + httpClient.getUid());
            log.info("sendMobileNotification result: " + result);
            checkArgument(result.equals(SUCCESS), "Result was not 'success'. result=" + result);
            return result;
        });

        Futures.addCallback(future, new FutureCallback<>() {
            @Override
            public void onSuccess(String result) {
                UserThread.execute(() -> resultHandler.accept(result));
            }

            @Override
            public void onFailure(@NotNull Throwable throwable) {
                UserThread.execute(() -> errorHandler.accept(throwable));
            }
        }, MoreExecutors.directExecutor());
    }
}
