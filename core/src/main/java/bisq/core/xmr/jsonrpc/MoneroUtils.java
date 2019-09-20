package bisq.core.xmr.jsonrpc;

import java.math.BigInteger;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.jetbrains.annotations.NotNull;
import org.springframework.util.Assert;

/**
 * Collection of Monero utilities.
 */
public class MoneroUtils {

	// core wallet2 syncs on a fixed intervals
	public static final long WALLET2_REFRESH_INTERVAL = 10000;

	private static final int NUM_MNEMONIC_WORDS = 25;
	private static final int VIEW_KEY_LENGTH = 64;
	private static final char[] ALPHABET = "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz".toCharArray();
	private static final List<Character> CHARS = new ArrayList<Character>();
	static {
		for (char c : ALPHABET) {
			CHARS.add(c);
		}
	}

	/**
	 * Validates a wallet seed.
	 * 
	 * TODO: beef this up
	 * 
	 * @param seed
	 *            is the seed to validate
	 */
	public static void validateSeed(@NotNull String seed) {
		Assert.isTrue(seed.length() == 64);
	}

	/**
	 * Validates the given mnemonic phrase.
	 * 
	 * @param mnemonic
	 *            is the mnemonic to validate
	 * @throws MoneroException
	 *             if the given mnemonic is invalid
	 */
	public static void validateMnemonic(@NotNull String mnemonic) {
		Assert.hasLength(mnemonic, "Mnemonic phrase is empty");
		String[] words = mnemonic.split(" ");
		if (words.length != MoneroUtils.NUM_MNEMONIC_WORDS)
			throw new Error(
					"Mnemonic phrase is " + words.length + " words but must be " + MoneroUtils.NUM_MNEMONIC_WORDS);
	}

	// TODO: improve validation
	public static void validatePrivateViewKey(@NotNull String privateViewKey) {
		Assert.isTrue(privateViewKey.length() == 64);
	}

	// TODO: improve validation
	public static void validatePrivateSpendKey(@NotNull String privateSpendKey) {
		Assert.isTrue(privateSpendKey.length() == 64);
	}

	// TODO: improve validation
	public static void validatePublicViewKey(@NotNull String publicViewKey) {
		Assert.isTrue(publicViewKey.length() == 64);
	}

	// TODO: improve validation
	public static void validatePublicSpendKey(@NotNull String publicSpendKey) {
		Assert.isTrue(publicSpendKey.length() == 64);
	}

	// TODO: improve validation
	public static void validateAddress(@NotNull String address) {
		Assert.hasLength(address);
	}

	// TODO: improve validation
	public static void validatePaymentId(String paymentId) {
		Assert.isTrue(paymentId.length() == 16 || paymentId.length() == 64);
	}

	/**
	 * Validates the given view key.
	 * 
	 * @param viewKey
	 *            is the view key to validate
	 * @throws MoneroException
	 *             if the given view key is invalid
	 */
	public static void validateViewKey(String viewKey) {
		if (viewKey == null)
			throw new MoneroException("View key is null");
		if (viewKey.length() != VIEW_KEY_LENGTH)
			throw new MoneroException("View key is " + viewKey.length() + " characters but must be " + VIEW_KEY_LENGTH);
	}

	/**
	 * Converts the string to a URI. Throws MoneroException if exception.
	 * 
	 * @param endpoint
	 *            is the string to convert to a URI
	 * @return URI is the initialized object from the string endpoint
	 */
	public static URI parseUri(String endpoint) {
		try {
			return new URI(endpoint);
		} catch (Exception e) {
			throw new MoneroException(e);
		}
	}

	public static void validateHex(String str) {
		if (!str.matches("^([0-9A-Fa-f]{2})+$"))
			throw new MoneroException("Invalid hex: " + str);
	}

	public static void validateBase58(String standardAddress) {
		for (char c : standardAddress.toCharArray()) {
			if (!CHARS.contains((Character) c))
				throw new MoneroException("Invalid Base58 " + standardAddress);
		}
	}

	/**
	 * Determines if two payment ids are functionally equal.
	 * 
	 * For example, 03284e41c342f032 and
	 * 03284e41c342f032000000000000000000000000000000000000000000000000 are
	 * considered equal.
	 * 
	 * @param paymentId1
	 *            is a payment id to compare
	 * @param paymentId2
	 *            is a payment id to compare
	 * @return true if the payment ids are equal, false otherwise
	 */
	public static boolean paymentIdsEqual(String paymentId1, String paymentId2) {
		int maxLength = Math.max(paymentId1.length(), paymentId2.length());
		for (int i = 0; i < maxLength; i++) {
			if (i < paymentId1.length() && i < paymentId2.length() && paymentId1.charAt(i) != paymentId2.charAt(i))
				return false;
			if (i >= paymentId1.length() && paymentId2.charAt(i) != '0')
				return false;
			if (i >= paymentId2.length() && paymentId1.charAt(i) != '0')
				return false;
		}
		return true;
	}

	/**
	 * Convenience method to reconcile two values with default configuration by
	 * calling reconcile(val1, val2, null, null, null).
	 * 
	 * @param val1
	 *            is a value to reconcile
	 * @param val2
	 *            is a value to reconcile
	 * @return the reconciled value if reconcilable
	 * @throws Exception
	 *             if the values cannot be reconciled
	 */
	public static <T> T reconcile(T val1, T val2) {
		return reconcile(val1, val2, null, null, null);
	}

	/**
	 * Reconciles two values.
	 * 
	 * @param val1
	 *            is a value to reconcile
	 * @param val2
	 *            is a value to reconcile
	 * @param resolveDefined
	 *            uses defined value if true or null, null if false
	 * @param resolveTrue
	 *            uses true over false if true, false over true if false, must be
	 *            equal if null
	 * @param resolveMax
	 *            uses max over min if true, min over max if false, must be equal if
	 *            null
	 * @returns the reconciled value if reconcilable
	 * @throws Exception
	 *             if the values cannot be reconciled
	 */
	@SuppressWarnings("unchecked")
	public static <T> T reconcile(T val1, T val2, Boolean resolveDefined, Boolean resolveTrue, Boolean resolveMax) {

		// check for same reference
		if (val1 == val2)
			return val1;

		// check for BigInteger equality
		Integer comparison = null; // save comparison for later if applicable
		if (val1 instanceof BigInteger && val2 instanceof BigInteger) {
			comparison = ((BigInteger) val1).compareTo((BigInteger) val2);
			if (comparison == 0)
				return val1;
		}

		// resolve one value null
		if (val1 == null || val2 == null) {
			if (Boolean.FALSE.equals(resolveDefined))
				return null; // use null
			else
				return val1 == null ? val2 : val1; // use defined value
		}

		// resolve different booleans
		if (resolveTrue != null && Boolean.class.isInstance(val1) && Boolean.class.isInstance(val2)) {
			return (T) resolveTrue;
		}

		// resolve different numbers
		if (resolveMax != null) {

			// resolve BigIntegers
			if (val1 instanceof BigInteger && val2 instanceof BigInteger) {
				return resolveMax ? (comparison < 0 ? val2 : val1) : (comparison < 0 ? val1 : val2);
			}

			// resolve integers
			if (val1 instanceof Integer && val2 instanceof Integer) {
				return (T) (Integer) (resolveMax ? Math.max((Integer) val1, (Integer) val2)
						: Math.min((Integer) val1, (Integer) val2));
			}

			// resolve longs
			if (val1 instanceof Long && val2 instanceof Long) {
				return (T) (Long) (resolveMax ? Math.max((Long) val1, (Long) val2)
						: Math.min((Long) val1, (Long) val2));
			}

			throw new RuntimeException("Need to resolve primitives and object versions");
			// // resolve js numbers
			// if (typeof val1 === "number" && typeof val2 === "number") {
			// return config.resolveMax ? Math.max(val1, val2) : Math.min(val1, val2);
			// }
		}

		// assert deep equality
		Assert.isTrue(val1.equals(val2), "Cannot reconcile values " + val1 + " and " + val2 + " with config: [" + resolveDefined + ", "
				+ resolveTrue + ", " + resolveMax + "]");
		return val1;
	}

	/**
	 * Reconciles two int arrays. The arrays must be identical or an exception is
	 * thrown.
	 * 
	 * @param val1
	 * @param val2
	 * @return
	 */
	public static int[] reconcileIntArrays(int[] arr1, int[] arr2) {

		// check for same reference or null
		if (arr1 == arr2)
			return arr1;

		// resolve one value defined
		if (arr1 == null || arr2 == null) {
			return arr1 == null ? arr2 : arr1;
		}

		// assert deep equality
		Assert.isTrue(Arrays.equals(arr1, arr2), "Cannot reconcile arrays");
		return arr1;
	}

	/**
	 * Returns a human-friendly key value line.
	 * 
	 * @param key
	 *            is the key
	 * @param value
	 *            is the value
	 * @param indent
	 *            indents the line
	 * @return the human-friendly key value line
	 */
	public static String kvLine(Object key, Object value, int indent) {
		return kvLine(key, value, indent, true, true);
	}

	/**
	 * Returns a human-friendly key value line.
	 * 
	 * @param key
	 *            is the key
	 * @param value
	 *            is the value
	 * @param indent
	 *            indents the line
	 * @param newline
	 *            specifies if the string should be terminated with a newline or not
	 * @param ignoreUndefined
	 *            specifies if undefined values should return an empty string
	 * @return the human-friendly key value line
	 */
	public static String kvLine(Object key, Object value, int indent, boolean newline, boolean ignoreUndefined) {
		if (value == null && ignoreUndefined)
			return "";
		return GenUtils.getIndent(indent) + key + ": " + value + (newline ? '\n' : "");
	}
}
