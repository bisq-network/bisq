package bisq.httpapi.model;

import java.util.List;



import bisq.httpapi.model.validation.NotNullItems;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Pattern;
import org.hibernate.validator.constraints.NotEmpty;

public class SeedWords {

    @NotNull
    @NotNullItems
    public List<String> mnemonicCode;

    @Pattern(regexp = "\\d\\d\\d\\d-\\d\\d-\\d\\d")
    @NotEmpty
    public String walletCreationDate;

    public SeedWords() {
    }

    public SeedWords(List<String> mnemonicCode, String walletCreationDate) {
        this.mnemonicCode = mnemonicCode;
        this.walletCreationDate = walletCreationDate;
    }
}
