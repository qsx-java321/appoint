package com.qsx.appoint.auth.service;

import com.qsx.appoint.auth.config.RegistrationProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class RegistrationMailSender {
    private final JavaMailSender sender;
    private final RegistrationProperties properties;

    public void sendCode(String email, String code) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(properties.from());
        message.setTo(email);
        message.setSubject("[" + properties.namespace() + "] 医院预约注册验证码");
        message.setText("您的注册验证码：" + code + "，有效期" + properties.codeTtl().toMinutes() + "分钟。");
        sender.send(message);
    }
}
