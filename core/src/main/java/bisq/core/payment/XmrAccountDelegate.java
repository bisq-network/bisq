package bisq.core.payment;

import bisq.core.xmr.knaccc.monero.address.WalletAddress;

import java.util.Map;

import lombok.Getter;
import lombok.experimental.Delegate;
import lombok.extern.slf4j.Slf4j;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Delegate for AssetAccount with convenient methods for managing the map entries and creating subAccounts.
 */
@Slf4j
public class XmrAccountDelegate {
    public static final String USE_XMR_SUB_ADDRESSES = "UseXMmrSubAddresses";
    private static final String KEY_MAIN_ADDRESS = "XmrMainAddress";
    private static final String KEY_PRIVATE_VIEW_KEY = "XmrPrivateViewKey";
    private static final String KEY_ACCOUNT_INDEX = "XmrAccountIndex";
    private static final String KEY_SUB_ADDRESS_INDEX = "XmrSubAddressIndex";
    private static final String KEY_SUB_ADDRESS = "XmrSubAddress";
    private static final String KEY_TRADE_ID = "TradeId";

    public static boolean isUsingSubAddresses(PaymentAccount paymentAccount) {
        return paymentAccount.extraData != null &&
                paymentAccount.extraData.getOrDefault(USE_XMR_SUB_ADDRESSES, "0").equals("1");
    }

    public static long getSubAddressIndexAsLong(PaymentAccount paymentAccount) {
        checkNotNull(paymentAccount.extraData, "paymentAccount.extraData must not be null");
        // We let it throw in case the value is not a number
        try {
            return Long.parseLong(paymentAccount.extraData.get(KEY_SUB_ADDRESS_INDEX));
        } catch (Throwable t) {
            log.error("Could not parse value " + paymentAccount.extraData.get(KEY_SUB_ADDRESS_INDEX + " to long value."), t);
            throw new RuntimeException(t);
        }
    }

    @Getter
    @Delegate
    private final AssetAccount account;

    public XmrAccountDelegate(AssetAccount account) {
        this.account = account;
    }

    public void createAndSetNewSubAddress() {
        long accountIndex = Long.parseLong(getAccountIndex());
        long subAddressIndex = Long.parseLong(getSubAddressIndex());
        // If both subAddressIndex and accountIndex would be 0 it would be the main address
        // and the walletAddress.getSubaddressBase58 call would return an error.
        checkArgument(subAddressIndex >= 0 && accountIndex >= 0 && (subAddressIndex + accountIndex > 0),
                "accountIndex and/or subAddressIndex are invalid");
        String privateViewKey = getPrivateViewKey();
        String mainAddress = getMainAddress();
        if (mainAddress.isEmpty() || privateViewKey.isEmpty()) {
            return;
        }
        try {
            WalletAddress walletAddress = new WalletAddress(mainAddress);
            long ts = System.currentTimeMillis();
            String subAddress = walletAddress.getSubaddressBase58(privateViewKey, accountIndex, subAddressIndex);
            log.info("Created new subAddress {}. Took {} ms.", subAddress, System.currentTimeMillis() - ts);
            setSubAddress(subAddress);
        } catch (WalletAddress.InvalidWalletAddressException e) {
            log.error("WalletAddress.getSubaddressBase58  failed", e);
            throw new RuntimeException(e);
        }
    }

    public void reset() {
        getMap().remove(USE_XMR_SUB_ADDRESSES);
        getMap().remove(KEY_MAIN_ADDRESS);
        getMap().remove(KEY_PRIVATE_VIEW_KEY);
        getMap().remove(KEY_ACCOUNT_INDEX);
        getMap().remove(KEY_SUB_ADDRESS_INDEX);
        getMap().remove(KEY_SUB_ADDRESS);
        getMap().remove(KEY_TRADE_ID);

        account.setAddress("");
    }

    public boolean isUsingSubAddresses() {
        return XmrAccountDelegate.isUsingSubAddresses(account);
    }

    public void setIsUsingSubAddresses(boolean value) {
        getMap().put(USE_XMR_SUB_ADDRESSES, value ? "1" : "0");
    }

    public String getSubAddress() {
        return getMap().getOrDefault(KEY_SUB_ADDRESS, "");
    }

    public void setSubAddress(String subAddress) {
        getMap().put(KEY_SUB_ADDRESS, subAddress);
        account.setAddress(subAddress);
    }

    // Unique ID for subAccount used as key in our global subAccount map.
    public String getSubAccountId() {
        return getMainAddress() + getAccountIndex();
    }

    public String getMainAddress() {
        return getMap().getOrDefault(KEY_MAIN_ADDRESS, "");
    }

    public void setMainAddress(String mainAddress) {
        getMap().put(KEY_MAIN_ADDRESS, mainAddress);
    }

    public String getPrivateViewKey() {
        return getMap().getOrDefault(KEY_PRIVATE_VIEW_KEY, "");
    }

    public void setPrivateViewKey(String privateViewKey) {
        getMap().put(KEY_PRIVATE_VIEW_KEY, privateViewKey);
    }

    public String getAccountIndex() {
        return getMap().getOrDefault(KEY_ACCOUNT_INDEX, "");
    }

    public void setAccountIndex(String newValue) {
        getMap().put(KEY_ACCOUNT_INDEX, newValue);
    }

    public String getSubAddressIndex() {
        return getMap().getOrDefault(KEY_SUB_ADDRESS_INDEX, "");
    }

    public long getSubAddressIndexAsLong() {
        return XmrAccountDelegate.getSubAddressIndexAsLong(account);
    }

    public void setSubAddressIndex(String subAddressIndex) {
        getMap().put(KEY_SUB_ADDRESS_INDEX, subAddressIndex);
    }

    public String getTradeId() {
        return getMap().getOrDefault(KEY_TRADE_ID, "");
    }

    public void setTradeId(String tradeId) {
        getMap().put(KEY_TRADE_ID, tradeId);
    }

    private Map<String, String> getMap() {
        return account.getOrCreateExtraData();
    }
}
