package com.ruoyi.common.sms;

/**
 * Business SMS verification operations shared by controllers and services.
 */
public interface SmsVerificationService
{
    void sendEmployeeAddCode(String phonenumber, String platcode);

    void verifyEmployeeAddCode(String phonenumber, String code);

    void sendTenantAdminAccountCode(String phonenumber, String platcode);

    void verifyTenantAdminAccountCode(String phonenumber, String code);
}
