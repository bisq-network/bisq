package bisq.httpapi.model;

import java.util.List;



import bisq.httpapi.model.validation.NotNullItems;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;
import org.hibernate.validator.constraints.NotEmpty;

public class SeedWordsRestore {

    @Size(min = 1)
    @NotNull
    @NotNullItems
    public List<String> mnemonicCode;

    public String password;

    @Pattern(regexp = "\\d\\d\\d\\d-\\d\\d-\\d\\d", message = "must be a date in format: yyyy-MM-dd")
    @NotEmpty
    public String walletCreationDate;

    public SeedWordsRestore() {
    }

    public SeedWordsRestore(List<String> mnemonicCode, String walletCreationDate) {
        this(mnemonicCode, walletCreationDate, null);
    }

    public SeedWordsRestore(List<String> mnemonicCode, String walletCreationDate, String password) {
        this.mnemonicCode = mnemonicCode;
        this.walletCreationDate = walletCreationDate;
        this.password = password;
    }
}
