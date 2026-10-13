package com.ruoyi.common.utils.sign;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

/**
 * AES-GCM 对称加解密。用来把商家自填的对象存储 SecretKey 加密后落库。
 *
 * <h3>为什么是 GCM 而不是 CBC/ECB</h3>
 * <p>GCM 自带认证标签（AEAD）：密文被改过一个字节，解密就会抛异常而不是返回一段乱码。
 * ECB 连相同明文都会产生相同密文（能看出哪两个商家填了同一个密钥），CBC 不带 MAC，
 * 密文可以被逐字节篡改而解密方察觉不到。存密钥这种场景不该用后两者。</p>
 *
 * <h3>IV 每次随机</h3>
 * <p>GCM 的 IV 绝不能在同一把密钥下重复——重复一次就会同时泄漏两条明文的异或，
 * 并且可能算出认证子密钥。所以这里每次加密都取新的随机 IV，拼在密文前面一起存。
 * IV 不是秘密，公开存放是设计如此。</p>
 *
 * <h3>密文格式</h3>
 * <pre>Base64( IV(12字节) || 密文 || 认证标签(16字节) )</pre>
 * <p>定长前缀 + 变长尾巴，解密时按位置切开即可，不需要额外的分隔符或长度字段。</p>
 */
public final class AesUtils
{
    /** GCM 推荐的 IV 长度。12 字节是规范推荐值，用别的长度会触发额外的派生步骤，没有好处 */
    private static final int IV_LENGTH = 12;

    /** 认证标签长度（位）。128 位是最长也是默认值，截短只会削弱完整性保证 */
    private static final int TAG_LENGTH_BIT = 128;

    private static final String TRANSFORMATION = "AES/GCM/NoPadding";

    private static final SecureRandom RANDOM = new SecureRandom();

    private AesUtils()
    {
    }

    /**
     * 加密。{@code secret} 是主密钥（口令），长度不限——内部用 SHA-256 派生成 256 位 AES 密钥。
     *
     * @return Base64 密文；{@code plain} 为 null 或空串时原样返回，不加密空值
     */
    public static String encrypt(String plain, String secret)
    {
        if (plain == null || plain.isEmpty())
        {
            return plain;
        }
        try
        {
            byte[] iv = new byte[IV_LENGTH];
            RANDOM.nextBytes(iv);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, deriveKey(secret), new GCMParameterSpec(TAG_LENGTH_BIT, iv));
            byte[] encrypted = cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8));

            byte[] result = new byte[iv.length + encrypted.length];
            System.arraycopy(iv, 0, result, 0, iv.length);
            System.arraycopy(encrypted, 0, result, iv.length, encrypted.length);
            return Base64.getEncoder().encodeToString(result);
        }
        catch (Exception e)
        {
            // 不把 e.getMessage() 带出去：异常信息里可能出现算法/密钥长度等细节
            throw new IllegalStateException("加密失败", e);
        }
    }

    /**
     * 解密。密文被篡改、主密钥不对、或者存的根本不是本方法产出的串，都会抛
     * {@link IllegalStateException}——调用方应当把它当成「这条配置不可用」，而不是当成空值放行。
     */
    public static String decrypt(String encrypted, String secret)
    {
        if (encrypted == null || encrypted.isEmpty())
        {
            return encrypted;
        }
        try
        {
            byte[] raw = Base64.getDecoder().decode(encrypted);
            if (raw.length <= IV_LENGTH)
            {
                throw new IllegalArgumentException("密文长度不足");
            }
            byte[] iv = Arrays.copyOfRange(raw, 0, IV_LENGTH);
            byte[] body = Arrays.copyOfRange(raw, IV_LENGTH, raw.length);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, deriveKey(secret), new GCMParameterSpec(TAG_LENGTH_BIT, iv));
            return new String(cipher.doFinal(body), StandardCharsets.UTF_8);
        }
        catch (Exception e)
        {
            throw new IllegalStateException("解密失败，密文可能已损坏或主密钥已变更", e);
        }
    }

    /**
     * 口令派生密钥。
     *
     * <p>这里用一次 SHA-256 而不是 PBKDF2/scrypt：主密钥是运维配置的高熵随机串，
     * 不是人记得住的弱口令，慢哈希防的是离线爆破，对高熵输入没有意义，
     * 反而会让每次加解密都多花几百毫秒。如果将来允许人工设置弱口令，这里要换成 PBKDF2。</p>
     */
    private static SecretKeySpec deriveKey(String secret) throws Exception
    {
        if (secret == null || secret.trim().isEmpty())
        {
            throw new IllegalArgumentException("主密钥未配置");
        }
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        return new SecretKeySpec(digest.digest(secret.getBytes(StandardCharsets.UTF_8)), "AES");
    }
}
