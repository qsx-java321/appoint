package db.migration;

import org.flywaydb.core.api.FlywayException;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

import java.sql.PreparedStatement;
import java.sql.ResultSet;

/** 在改变排班语义前阻止仍有有效旧单号源订单的数据库升级。 */
public class V2__reject_active_legacy_appointments extends BaseJavaMigration {
    @Override
    public void migrate(Context context) throws Exception {
        try (PreparedStatement statement = context.getConnection().prepareStatement(
                "SELECT COUNT(*) FROM appointment_orders WHERE status IN ('PENDING_PAYMENT', 'CONFIRMED')");
             ResultSet result = statement.executeQuery()) {
            result.next();
            if (result.getLong(1) > 0) {
                throw new FlywayException("检测到旧单号源有效订单，已停止迁移；请先制定并执行旧订单结算方案");
            }
        }
    }
}
