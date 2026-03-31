package jp.co.oda32.constant;

public enum CompanyType {
    ADMIN("admin"),
    SHOP("shop"),
    PARTNER("partner");

    private final String value;

    CompanyType(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public static CompanyType purse(String key) {
        for (CompanyType companyType : values()) {
            if (companyType.getValue().equals(key)) {
                return companyType;
            }
        }
        return null;
    }
}
