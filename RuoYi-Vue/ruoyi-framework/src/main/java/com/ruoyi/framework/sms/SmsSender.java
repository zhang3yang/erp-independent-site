package com.ruoyi.framework.sms;

/** SMS sending abstraction. */
public interface SmsSender
{
    void sendLoginCode(String phonenumber, String code);

    default void sendLoginCode(String phonenumber, String code, String platcode)
    {
        sendLoginCode(phonenumber, code);
    }
}
