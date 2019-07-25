package bisq.asset;

import java.io.IOException;
import java.util.Properties;

public class I18n extends Properties {

	/**
	 * 
	 */
	private static final long serialVersionUID = -1840156948793172676L;
	
	public static I18n DISPLAY_STRINGS = new I18n();

	static {
		try {
			DISPLAY_STRINGS.load(I18n.class.getResourceAsStream("/resources/i18n/displayStrings.properties"));
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
}
