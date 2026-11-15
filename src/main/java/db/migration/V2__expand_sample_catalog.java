package db.migration;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Locale;

/** 在现行V1上追加样例目录，保留原有档案、编号、绑定及预约引用。 */
public class V2__expand_sample_catalog extends BaseJavaMigration {
    @Override
    public void migrate(Context context) throws SQLException {
        Connection connection = context.getConnection();
        long firstDermatology = department(connection, 1, "皮肤科");
        long secondHospital = insert(connection, "INSERT INTO hospitals(name,address) VALUES (?,?)",
                "示例市第二人民医院", "示例市健康路 20 号");
        long secondInternal = department(connection, secondHospital, "内科");
        long secondOrthopedics = department(connection, secondHospital, "骨科");
        long secondDermatology = department(connection, secondHospital, "皮肤科");

        // V1的医生1、2分别属于第一医院内科、骨科，不移动或覆盖原档案。
        doctor(connection, 3, 1, "王医生", "副主任医师", "擅长消化系统常见疾病诊治。");
        doctor(connection, 4, 1, "赵医生", "主治医师", "擅长呼吸系统常见疾病诊治。");
        doctor(connection, 5, 2, "刘医生", "主任医师", "擅长关节疾病诊治。");
        doctor(connection, 6, 2, "陈医生", "主治医师", "擅长运动损伤诊治。");
        doctor(connection, 7, firstDermatology, "杨医生", "主任医师", "擅长常见皮肤疾病诊治。");
        doctor(connection, 8, firstDermatology, "周医生", "副主任医师", "擅长过敏性皮肤疾病诊治。");
        doctor(connection, 9, firstDermatology, "吴医生", "主治医师", "擅长皮肤感染诊治。");
        doctor(connection, 10, secondInternal, "徐医生", "主任医师", "擅长常见内科疾病诊治。");
        doctor(connection, 11, secondInternal, "孙医生", "副主任医师", "擅长心血管常见疾病诊治。");
        doctor(connection, 12, secondInternal, "胡医生", "主治医师", "擅长内分泌常见疾病诊治。");
        doctor(connection, 13, secondOrthopedics, "朱医生", "主任医师", "擅长脊柱疾病诊治。");
        doctor(connection, 14, secondOrthopedics, "高医生", "副主任医师", "擅长骨科常见疾病诊治。");
        doctor(connection, 15, secondOrthopedics, "林医生", "主治医师", "擅长骨科康复诊疗。");
        doctor(connection, 16, secondDermatology, "何医生", "主任医师", "擅长常见皮肤疾病诊治。");
        doctor(connection, 17, secondDermatology, "郭医生", "副主任医师", "擅长慢性皮肤疾病诊治。");
        doctor(connection, 18, secondDermatology, "马医生", "主治医师", "擅长痤疮及湿疹诊治。");
    }

    private long department(Connection connection, long hospitalId, String name) throws SQLException {
        return insert(connection, "INSERT INTO departments(hospital_id,name) VALUES (?,?)", hospitalId, name);
    }

    private void doctor(Connection connection, int ordinal, long departmentId, String name,
                        String title, String introduction) throws SQLException {
        long number = insert(connection, "INSERT INTO doctor_number_allocations(allocation_token) VALUES (?)",
                "SEED-DOCTOR-" + ordinal);
        insert(connection, "INSERT INTO doctors(doctor_no,department_id,name,title,introduction) VALUES (?,?,?,?,?)",
                String.format(Locale.ROOT, "DOC%06d", number), departmentId, name, title, introduction);
    }

    private long insert(Connection connection, String sql, Object... values) throws SQLException {
        try (var statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            for (int i = 0; i < values.length; i++) statement.setObject(i + 1, values[i]);
            statement.executeUpdate();
            try (var keys = statement.getGeneratedKeys()) {
                if (!keys.next()) throw new SQLException("样例目录初始化未取得新增记录编号");
                return keys.getLong(1);
            }
        }
    }
}
