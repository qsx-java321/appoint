package db.migration;

import org.flywaydb.core.api.FlywayException;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

import java.sql.PreparedStatement;
import java.sql.ResultSet;

/** 在扩展支付结构前阻止仍可能由旧同步流程推进的活跃支付。 */
public class V4__reject_active_legacy_payments extends BaseJavaMigration {
    @Override
    public void migrate(Context context) throws Exception {
        try (PreparedStatement statement = context.getConnection().prepareStatement("""
                SELECT COUNT(*)
                FROM appointment_orders appointment
                JOIN payment_orders payment ON payment.appointment_order_id = appointment.id
                WHERE appointment.status = 'PENDING_PAYMENT'
                  AND payment.status = 'UNPAID'
                """);
             ResultSet result = statement.executeQuery()) {
            result.next();
            if (result.getLong(1) > 0) {
                throw new FlywayException(
                        "检测到旧活跃支付，已停止支付模块迁移；请在维护窗口内先完成或关闭旧订单");
            }
        }
    }
}
