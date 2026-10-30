package com.qsx.appoint.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import com.qsx.appoint.appointment.archfixture.AppointmentToPaymentMapperDependency;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThat;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import com.qsx.appoint.appointment.mapper.AppointmentOrderMapper;
import com.qsx.appoint.bookingquery.persistence.BookingQueryMapper;

class ModuleDependencyTest {
    private final JavaClasses productionClasses = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.qsx.appoint");

    @Test
    void paymentDoesNotReachIntoAppointmentInternals() {
        noClasses()
                .that().resideInAPackage("..payment..")
                .should().dependOnClassesThat().resideInAPackage("..appointment..")
                .because("支付写模块只能通过稳定 application interface 或事件与预约协作")
                .check(productionClasses);
    }

    @Test
    void appointmentDoesNotAddNewPaymentPersistenceDependencies() {
        appointmentPersistenceRule().check(productionClasses);
    }

    @Test
    void appointmentPersistenceRuleRejectsANewReverseMapperDependency() {
        JavaClasses violatingFixture = new ClassFileImporter()
                .importClasses(AppointmentToPaymentMapperDependency.class);

        assertThatThrownBy(() -> appointmentPersistenceRule().check(violatingFixture))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("PaymentOrderMapper");
    }

    private ArchRule appointmentPersistenceRule() {
        return noClasses()
                .that().resideInAPackage("..appointment..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "..payment.mapper..", "..payment.entity..", "..payment.service.impl..")
                .because("预约写模块不能跨越支付 persistence 边界");
    }

    @Test
    void appointmentDoesNotAddOtherPaymentDependencies() {
        noClasses()
                .that().resideInAPackage("..appointment..")
                .should().dependOnClassesThat().resideInAPackage("..payment..")
                .because("预约写模块只能经协调模块与支付协作")
                .check(productionClasses);
    }

    @Test
    void appointmentBusinessLayersDoNotDependOnCoordination() {
        noClasses()
                .that().resideInAnyPackage(
                        "..appointment.application..", "..appointment.service..",
                        "..appointment.entity..", "..appointment.mapper..")
                .should().dependOnClassesThat().resideInAPackage("..coordination..")
                .because("协调层可以调用预约 application interface，预约业务层不能反向依赖协调层")
                .check(productionClasses);
    }

    @Test
    void coordinationDoesNotUseWriteModulePersistence() {
        noClasses()
                .that().resideInAPackage("..coordination..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "..appointment.mapper..", "..appointment.entity..",
                        "..payment.mapper..", "..payment.entity..")
                .because("协调模块只依赖两个写模块的 application interface")
                .allowEmptyShould(true)
                .check(productionClasses);
    }

    @Test
    void writeModulesDoNotDependOnBookingQuery() {
        noClasses()
                .that().resideInAnyPackage("..appointment..", "..payment..")
                .should().dependOnClassesThat().resideInAPackage("..bookingquery..")
                .because("写模块不能反向依赖只读组合投影")
                .check(productionClasses);
    }

    @Test
    void crossModuleReadSqlBelongsOnlyToBookingQuery() {
        for (var method : AppointmentOrderMapper.class.getDeclaredMethods()) {
            Select select = method.getAnnotation(Select.class);
            if (select != null) {
                assertThat(String.join(" ", select.value())).doesNotContain("payment_orders");
            }
        }
        for (var method : BookingQueryMapper.class.getDeclaredMethods()) {
            assertThat(method.getAnnotation(Insert.class)).isNull();
            assertThat(method.getAnnotation(Update.class)).isNull();
            assertThat(method.getAnnotation(Delete.class)).isNull();
            assertThat(method.getAnnotation(Select.class)).isNotNull();
        }
    }
}
