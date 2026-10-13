package com.ruoyi.common.redis;

/**
 * 生成主键公共方法
 *
 * @author Gavin R. Wen
 * @since 2018年10月25日上午11:05:20
 */
public class KeyCreatorUtil {

    public static String valuetoString(Long value, KeyType type) {
        String rtvvalue = "";
        switch (type) {
            case Base10:
                while (value > 0) {
                    Long chv = (value % 9) + 1;
                    String cch = chv.toString();
                    rtvvalue = cch + rtvvalue;
                    value /= 9;
                }
                break;
            case Base16Lower:
            case Base16Upper:
                while (value > 0) {
                    Long chv = (value % 15) + 1;
                    String cch = "";
                    if (chv <= 9) {
                        cch = chv.toString();
                    } else {
                        cch = new Character((char) ('a' + chv - 10)).toString();
                    }
                    rtvvalue = cch + rtvvalue;
                    value /= 15;
                }
                if (type == KeyType.Base16Upper) {
                    rtvvalue = rtvvalue.toUpperCase();
                }
                break;
            case Base36Lower:
            case Base36Upper:
                while (value > 0) {
                    Long chv = (value % 35) + 1;
                    String cch = "";
                    if (chv <= 9) {
                        cch = chv.toString();
                    } else {
                        cch = new Character((char) ('a' + chv - 10)).toString();
                    }
                    rtvvalue = cch + rtvvalue;
                    value /= 35;
                }
                if (type == KeyType.Base36Upper) {
                    rtvvalue = rtvvalue.toUpperCase();
                }
                break;
            case Base62:
                while (value > 0) {
                    Long chv = (value % 61) + 1;
                    String cch = "";
                    if (chv <= 9) {
                        cch = chv.toString();
                    } else if (chv <= 35) {
                        cch = new Character((char) ('a' + chv - 10)).toString();
                    } else {
                        cch = new Character((char) ('A' + chv - 36)).toString();
                    }
                    rtvvalue = cch + rtvvalue;
                    value /= 61;
                }
                break;
        }
        return rtvvalue;
    }

    /*
     * /// <summary>
    /// 从秒级主键内容中取中时间值
    /// </summary>
    /// <param name="keyvalue">秒级主键值</param>
    /// <param name="type">主键类型</param>
    /// <returns>返回对应的时间值,精确到秒</returns>
     */
    public static String getTimeFromSecKey(String keyvalue, KeyType type) {
        String time = "";
        // 取中间部分
        int pos1 = keyvalue.indexOf("0");
        int pos2 = keyvalue.lastIndexOf("0");
        String timestr = keyvalue.substring(pos1 + 1, pos2);
        int baseno = 9;
        if (type == KeyType.Base10) {
            baseno = 9;
        } else if (type == KeyType.Base16Lower || type == KeyType.Base16Upper) {
            timestr = timestr.toLowerCase();
            baseno = 15;
        } else if (type == KeyType.Base36Lower || type == KeyType.Base36Upper) {
            timestr = timestr.toLowerCase();
            baseno = 35;
        } else {
            baseno = 61;
        }

        long pow = 1;
        int chv = 0;
        char cch = '0';
        Long second = 0L;
        for (int i = timestr.length() - 1; i >= 0; i--) {
            cch = timestr.charAt(i);
            if (cch >= '1' && cch <= '9') {
                chv = cch - '0';
            } else if (cch >= 'a' && cch <= 'z') {
                chv = cch - 'a' + 10;
            } else if (cch >= 'A' && cch <= 'Z') {
                chv = cch - 'A' + 36;
            }
            chv -= 1;
            second += chv * pow;
            pow *= baseno;
        }

        Long iv2000 = DateUtil.fomatDatetime(ConstSelf.BASE_TIME).getTime();
        long tmp = iv2000 + second * 1000;
        time = DateUtil.milltoString(tmp);
        return time;
    }

    public static String getGENIDFromSecKey(String keyvalue, KeyType type) {
        String creategenid = "";
        int pos1 = keyvalue.indexOf("0");
        String genidstr = keyvalue.substring(0, pos1);

        int baseno = 9;
        if (type == KeyType.Base10) {
            baseno = 9;
        } else if (type == KeyType.Base16Lower || type == KeyType.Base16Upper) {
            genidstr = genidstr.toLowerCase();
            baseno = 15;
        } else if (type == KeyType.Base36Lower || type == KeyType.Base36Upper) {
            genidstr = genidstr.toLowerCase();
            baseno = 35;
        } else {
            baseno = 61;
        }

        long pow = 1;
        int chv = 0;
        char cch = '0';
        Long second = 0L;
        for (int i = genidstr.length() - 1; i >= 0; i--) {
            cch = genidstr.charAt(i);
            if (cch >= '1' && cch <= '9') {
                chv = cch - '0';
            } else if (cch >= 'a' && cch <= 'z') {
                chv = cch - 'a' + 10;
            } else if (cch >= 'A' && cch <= 'Z') {
                chv = cch - 'A' + 36;
            }
            chv -= 1;
            second += chv * pow;
            pow *= baseno;
        }

        creategenid = new Long(second).toString();

        return creategenid;
    }

    public static String getCREATEINDEXFromSecKey(String keyvalue, KeyType type) {
        String createindex = "";
        int pos2 = keyvalue.lastIndexOf("0");
        String genidstr = keyvalue.substring(pos2 + 1);

        int baseno = 9;
        if (type == KeyType.Base10) {
            baseno = 9;
        } else if (type == KeyType.Base16Lower || type == KeyType.Base16Upper) {
            genidstr = genidstr.toLowerCase();
            baseno = 15;
        } else if (type == KeyType.Base36Lower || type == KeyType.Base36Upper) {
            genidstr = genidstr.toLowerCase();
            baseno = 35;
        } else {
            baseno = 61;
        }

        long pow = 1;
        int chv = 0;
        char cch = '0';
        Long second = 0L;
        for (int i = genidstr.length() - 1; i >= 0; i--) {
            cch = genidstr.charAt(i);
            if (cch >= '1' && cch <= '9') {
                chv = cch - '0';
            } else if (cch >= 'a' && cch <= 'z') {
                chv = cch - 'a' + 10;
            } else if (cch >= 'A' && cch <= 'Z') {
                chv = cch - 'A' + 36;
            }
            chv -= 1;
            second += chv * pow;
            pow *= baseno;
        }

        createindex = new Long(second).toString();

        return createindex;
    }
}
