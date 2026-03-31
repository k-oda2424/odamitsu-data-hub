package jp.co.oda32.constant;

public enum Flag {
    YES("1"),
    NO("0");

    private final String value;

    Flag(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }
}
