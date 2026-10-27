package com.qsx.appoint.patient.service;

import com.qsx.appoint.common.BusinessException;
import com.qsx.appoint.common.ErrorCode;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.ResolverStyle;
import java.util.Locale;

/** 居民身份证号码的规范化、校验与脱敏规则。 */
public final class ResidentIdCard {
    private static final int[] WEIGHTS = {7, 9, 10, 5, 8, 4, 2, 1, 6, 3, 7, 9, 10, 5, 8, 4, 2};
    private static final char[] CHECK_CODES = {'1', '0', 'X', '9', '8', '7', '6', '5', '4', '3', '2'};
    private static final DateTimeFormatter BIRTHDAY = DateTimeFormatter.ofPattern("uuuuMMdd")
            .withResolverStyle(ResolverStyle.STRICT);

    private ResidentIdCard() { }

    public static String normalize(String raw) {
        if (raw == null) throw invalid();
        String value = raw.trim().toUpperCase(Locale.ROOT);
        if (!value.matches("\\d{17}[0-9X]") || value.startsWith("000000")
                || "000".equals(value.substring(14, 17))) {
            throw invalid();
        }
        try {
            LocalDate.parse(value.substring(6, 14), BIRTHDAY);
        } catch (DateTimeException exception) {
            throw invalid();
        }
        int sum = 0;
        for (int i = 0; i < 17; i++) {
            sum += (value.charAt(i) - '0') * WEIGHTS[i];
        }
        if (value.charAt(17) != CHECK_CODES[sum % 11]) throw invalid();
        return value;
    }

    public static String mask(String normalized) {
        return normalized.substring(0, 6) + "********" + normalized.substring(14);
    }

    private static BusinessException invalid() {
        return new BusinessException(ErrorCode.VALIDATION_ERROR);
    }
}
