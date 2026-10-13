package com.ruoyi.common.redis;

import java.math.BigDecimal;

public class ConstSelf {

    public static final String SESSION_USER = "session_user";

    public static final BigDecimal DECIMAL_0F2 = new BigDecimal("0.00");

    public static final String RETURN_BOOLEAN_KEY = "Result";

    public static final String RETURN_ERROR_MESSAGE = "ErrorMsg";

    public static final String RETURN_ERROR_CODE = "ErrorCode";

    public static final String RETURN_DATAROWS = "DataRows";

    public static final String RETURN_DATAROW = "DataRow";

    public static final String DYNAMIC_TABLE_NAME = "tblname";

    public static final String DYNAMIC_RECORDCOUNT = "recordCount";

    public static final String DYNAMIC_STARTINDEX = "startIndex";

    public static final String DYNAMIC_CONDITION = "condition";

    public static final String DYNAMIC_QUERYFIELDS = "queryfields";

    public static final String DYNAMIC_SORTORIENT = "SortOrient";

    public static final String DYNAMIC_ORDERCONDITION = "ordercondition";

    public static final String KEY_SEPARATOR = "0";

    public static final int PAGE_SIZE = 20;

    public static final int DECIMAL_PRECISE = 2;

    public static final int DBI_SCOPE_8 = 8;

    public static final int DBT_SCOPE_8 = 8;

    public static final int DBT_SCOPE_12 = 12;

    public static final String STATUS_YES = "1";

    public static final String STATUS_NO = "0";

    public static final String STATUS_YES_CN = "是";

    public static final String STATUS_NO_CN = "否";

    public static final String BASE_DATE = "2000-01-01";

    public static final String BASE_TIME = "2000-01-01 00:00:00";

    public static final String QUERY_START_TIME = "2017-01-01 00:00:00";

    public static final String STATUS_OK = "OK";

    public static final String STATUS_ERROR = "ERROR";

    public static final int VERIFYCODE_EXPIRE_SECS = 15 * 60;

    public static final int BASE_YEAR = 2017;// 订单库起始年份

    public static final String DEFAULT_NEW_PWD = "E10ADC3949BA59ABBE56E057F20F883E";

    // 上下架状态
    public static final String SHELFSTATE_ON = "已上架";

    public static final String SHELFSTATE_OFF = "已下架";

    // 白条状态
    public static final String BLANKSTATE_INUSE = "使用中";

    public static final String BLANKSTATE_APPLYING = "申请中";

    public static final String BLANKSTATE_REFUSED = "已拒绝";

    // PAYTYPE 付款方式 在线支付/会员卡支付/白条支付
    public static final String PAYTYPE_ONLINE = "在线支付";

    public static final String PAYTYPE_VIPCARD = "会员卡支付";

    public static final String PAYTYPE_BLANKCODE = "白条支付";

    public static final String PAYTYPE_ALIPAY = "支付宝";

    // PLATCODE 平台代码
    public static final String PLATCODE_GAS = "gas";

    // ORDERSTATE 订单状态 未支付/已支付/已完成/已取消
    public static final String ORDERSTATE_NOPAY = "未支付";

    public static final String ORDERSTATE_PAYED = "已支付";

    public static final String ORDERSTATE_COMPLETE = "已完成";

    public static final String ORDERSTATE_CANCEL = "已取消";

    // REFUNDSTATE 退款状态 退款中/退款完成/退款拒绝
    public static final String REFUNDSTATE_REFUNDING = "退款中";

    public static final String REFUNDSTATE_REFUNDED = "退款完成";

    public static final String REFUNDSTATE_REFUNDREFUSED = "退款拒绝";
}
