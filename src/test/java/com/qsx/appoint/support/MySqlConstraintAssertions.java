package com.qsx.appoint.support;

import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.springframework.jdbc.UncategorizedSQLException;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 当前MySQL驱动把CHECK错误3819映射为未分类异常；验证数据库事实而非接受任意异常。 */
public final class MySqlConstraintAssertions {
    private MySqlConstraintAssertions() { }

    public static void assertCheckViolation(ThrowingCallable operation, String constraint) {
        assertThatThrownBy(operation).isInstanceOfSatisfying(UncategorizedSQLException.class, error -> {
            assertThat(error.getSQLException().getSQLState()).isEqualTo("HY000");
            assertThat(error.getSQLException().getErrorCode()).isEqualTo(3819);
            assertThat(error.getSQLException().getMessage()).contains(constraint);
        });
    }
}
