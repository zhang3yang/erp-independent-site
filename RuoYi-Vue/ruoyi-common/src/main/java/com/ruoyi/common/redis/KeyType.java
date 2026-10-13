package com.ruoyi.common.redis;

/**
 * 主键编码类型
 *
 * @author Gavin R. Wen
 * @since 2018年10月25日上午10:43:52
 */
public enum KeyType {

    Base10("1"), Base16Lower("2"), Base16Upper("3"),
    Base36Lower("4"), Base36Upper("5"), Base62("6");

    private String code;

    private KeyType(String code) {
        this.code = code;
    }

    public String getCode() {
        return code;
    }

    public static String getNameByIndex(String index) {
        for (KeyType c : KeyType.values()) {
            String idx = c.getCode();
            if (idx == null) {
                idx = "";
            }
            if (idx.equals(index)) {
                return c.name();
            }
        }
        return null;
    }
}
