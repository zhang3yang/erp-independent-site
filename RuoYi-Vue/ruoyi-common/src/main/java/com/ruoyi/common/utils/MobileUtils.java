package com.ruoyi.common.utils;

import java.util.regex.Pattern;
import com.ruoyi.common.exception.ServiceException;

/**
 * 手机号校验。
 *
 * <p>全项目的手机号与电话字段统一走这里：11 位大陆手机号，1 开头、第二位 3-9。
 * 前端的同名口径在 {@code RuoYi-Vue3/src/utils/validate.js} 的 MOBILE_PATTERN，
 * 改一边必须改另一边，否则会出现前端放行、后端拒收的死循环。</p>
 *
 * <p>为什么后端也要拦：前端规则只挡住页面上的手工输入，导入、接口直调、
 * 存量数据改单都绕得过去。</p>
 */
public class MobileUtils
{
    /** 与前端 MOBILE_PATTERN 保持一致 */
    public static final String REGEX = "^1[3-9]\\d{9}$";

    private static final Pattern PATTERN = Pattern.compile(REGEX);

    private MobileUtils()
    {
    }

    /** 空值返回 false。要「不填也行」的语义用 {@link #validateOptional} */
    public static boolean isValid(String phone)
    {
        return phone != null && PATTERN.matcher(phone.trim()).matches();
    }

    /**
     * 选填字段：不填放行，填了必须是 11 位手机号。
     *
     * @param label 出错时报给用户的字段名，例如「客户手机号」
     */
    public static void validateOptional(String phone, String label)
    {
        if (phone == null || phone.trim().isEmpty())
        {
            return;
        }
        if (!isValid(phone))
        {
            throw new ServiceException(label + "必须是 11 位手机号");
        }
    }

    /** 必填字段：空值和格式错都拦。 */
    public static void validateRequired(String phone, String label)
    {
        if (phone == null || phone.trim().isEmpty())
        {
            throw new ServiceException(label + "不能为空");
        }
        if (!isValid(phone))
        {
            throw new ServiceException(label + "必须是 11 位手机号");
        }
    }
}
