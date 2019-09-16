package bisq.core.xmr.wallet;

public interface TxProofHandler {
	
	/**
	 * 
	 * @param txId
	 * @param message
	 * @param signature
	 */
	void update(String txId, String message, String signature);

    /**
     * 
     */
	void playAnimation();

	/**
	 * 
	 */
	void stopAnimation();

	/**
	 * 
	 * @param resourceMessage
	 */
	void popupErrorWindow(String resourceMessage);
}
