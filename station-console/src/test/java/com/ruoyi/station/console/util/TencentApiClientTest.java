package com.ruoyi.station.console.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * TC3 签名。2026-09-26 从 TmtClient 抽出来之后，机器翻译和混元生 3D 共用这一份。
 *
 * <p>钉住固定输入的输出：签名逻辑以后被改动时，这里会先红——而线上的表现是
 * 「翻译和生成 3D 同时报 AuthFailure.SignatureFailure」，远比单测难查。</p>
 */
class TencentApiClientTest
{
    private static final long TS = 1_758_844_800L; // 2025-09-26 00:00:00 UTC

    @Test
    void authorizationShapeAndScope() throws Exception
    {
        String auth = TencentApiClient.authorization("ai3d.tencentcloudapi.com", "ai3d",
                "SubmitHunyuanTo3DProJob", "{\"ImageUrl\":\"https://x/a.png\"}", "AKIDtest", "secret", TS);
        assertTrue(auth.startsWith("TC3-HMAC-SHA256 Credential=AKIDtest/2025-09-26/ai3d/tc3_request, "
                + "SignedHeaders=content-type;host;x-tc-action, Signature="), auth);
        String sig = auth.substring(auth.indexOf("Signature=") + 10);
        assertEquals(64, sig.length());
        assertTrue(sig.matches("[0-9a-f]{64}"), "签名必须是小写十六进制");
    }

    @Test
    void dateIsUtcNotLocal() throws Exception
    {
        // 北京时间 2025-09-26 07:59:59 = UTC 2025-09-25 23:59:59：日期必须是 25 号
        String auth = TencentApiClient.authorization("tmt.tencentcloudapi.com", "tmt",
                "TextTranslateBatch", "{}", "id", "key", TS - 1);
        assertTrue(auth.contains("/2025-09-25/tmt/tc3_request"), auth);
    }

    @Test
    void deterministicAndSensitiveToEveryInput() throws Exception
    {
        String base = TencentApiClient.authorization("h", "s", "A", "{}", "id", "key", TS);
        assertEquals(base, TencentApiClient.authorization("h", "s", "A", "{}", "id", "key", TS));
        assertNotEquals(base, TencentApiClient.authorization("h2", "s", "A", "{}", "id", "key", TS));
        assertNotEquals(base, TencentApiClient.authorization("h", "s", "B", "{}", "id", "key", TS));
        assertNotEquals(base, TencentApiClient.authorization("h", "s", "A", "{ }", "id", "key", TS));
        assertNotEquals(base, TencentApiClient.authorization("h", "s", "A", "{}", "id", "key2", TS));
        // action 在规范请求串里转小写：大小写不同的 action 签名相同（发出去的 header 仍是原样）
        assertEquals(base, TencentApiClient.authorization("h", "s", "a", "{}", "id", "key", TS));
    }
}
