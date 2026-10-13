package com.ruoyi.common.utils;

import java.util.regex.Pattern;
import com.ruoyi.common.exception.ServiceException;

/**
 * 单据上的收件 / 联系电话校验。
 *
 * <p>与 {@link MobileUtils} 分工：那个管「必须是本人手机号」的字段（客户档案手机号、
 * 往来单位联系人手机），这个管单据上填给快递和货代的联系方式。后者按 11 位手机号收
 * 会把三类正常情况全挡在外面：</p>
 * <ul>
 *   <li>平台下单带过来的<b>虚拟号</b>，位数不固定；</li>
 *   <li>客户留的<b>固定电话</b>，带区号，也可能带分机；</li>
 *   <li>一次留<b>好几个号码</b>，怕一个打不通。</li>
 * </ul>
 *
 * <p>规则：只允许数字、加号、横杠、逗号、分号和空格，其中至少有一位数字，
 * 最长 {@value #MAX_LENGTH} 个字符。多个号码用逗号或分号分隔。</p>
 *
 * <p>不校验号段、不校验位数：这里能判的只有「像不像一串电话」，判不了「打不打得通」。
 * 再严一点就会开始误伤，而误伤的代价是单据录不进来。</p>
 *
 * <p>前端同名口径在 {@code RuoYi-Vue3/src/utils/validate.js} 的 isContactPhone，
 * 改一边必须改另一边，否则会出现前端放行、后端拒收的死循环。</p>
 */
public class ContactPhoneUtils
{
    /** 与库里 receivetelephone 列的宽度对齐（varchar(100)） */
    public static final int MAX_LENGTH = 100;

    /** 与前端 CONTACT_PHONE_PATTERN 保持一致 */
    public static final String REGEX = "^[0-9+\\-,; ]+$";

    private static final Pattern PATTERN = Pattern.compile(REGEX);

    private static final Pattern DIGIT = Pattern.compile("\\d");

    private ContactPhoneUtils()
    {
    }

    /** 空值返回 false。要「不填也行」的语义用 {@link #validateOptional} */
    public static boolean isValid(String phone)
    {
        if (phone == null) return false;
        String trimmed = phone.trim();
        if (trimmed.isEmpty() || trimmed.length() > MAX_LENGTH) return false;
        // 光有分隔符不算电话：",,;" 能过字符集，但里面一个数字都没有
        return PATTERN.matcher(trimmed).matches() && DIGIT.matcher(trimmed).find();
    }

    /**
     * 选填字段：不填放行，填了必须像一串电话。
     *
     * @param label 出错时报给用户的字段名，例如「收件电话」
     */
    public static void validateOptional(String phone, String label)
    {
        if (phone == null || phone.trim().isEmpty())
        {
            return;
        }
        assertValid(phone, label);
    }

    /** 必填字段：空值和格式错都拦。 */
    public static void validateRequired(String phone, String label)
    {
        if (phone == null || phone.trim().isEmpty())
        {
            throw new ServiceException(label + "不能为空");
        }
        assertValid(phone, label);
    }

    private static void assertValid(String phone, String label)
    {
        if (phone.trim().length() > MAX_LENGTH)
        {
            throw new ServiceException(label + "太长了，最多 " + MAX_LENGTH + " 个字符");
        }
        if (!isValid(phone))
        {
            throw new ServiceException(label + "只能填数字、加号、横杠、空格，多个号码用逗号或分号分隔");
        }
    }
}
