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

/** 类内只扫描一次生产类；违规样例分别导入，不能污染正向规则的被测集合。 */
class ModuleDependencyTest {
    private static final JavaClasses PRODUCTION_CLASSES = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.qsx.appoint");

    @Test
    void businessApplicationDoesNotDependOnRabbitTransport() {
        noClasses().that().resideInAnyPackage("..payment.application..","..payment.domain..",
                "..coordination.application..","..coordination.domain..")
                .should().dependOnClassesThat().resideInAnyPackage("org.springframework.amqp..","com.rabbitmq..")
                .check(PRODUCTION_CLASSES);
        noClasses().that().resideInAPackage("..payment.contract.event..")
                .should().dependOnClassesThat().resideInAnyPackage("org.springframework..","com.fasterxml.jackson..",
                        "..mapper..","..entity..","..coordination..")
                .check(PRODUCTION_CLASSES);
        noClasses().that().resideInAPackage("..common.messaging..")
                .should().dependOnClassesThat().resideInAnyPackage("..payment..","..appointment..","..coordination..")
                .check(PRODUCTION_CLASSES);
    }

    @Test
    void messagingDependencyRulesRejectViolations() {
        var business = new ClassFileImporter().importClasses(com.qsx.appoint.payment.application.archfixture.BusinessToRabbit.class);
        assertThatThrownBy(() -> noClasses().that().resideInAnyPackage("..payment.application..","..coordination.application..")
                .should().dependOnClassesThat().resideInAnyPackage("org.springframework.amqp..","com.rabbitmq..")
                .check(business)).isInstanceOf(AssertionError.class);
        var contract = new ClassFileImporter().importClasses(com.qsx.appoint.payment.contract.event.archfixture.ContractToJackson.class);
        assertThatThrownBy(() -> noClasses().that().resideInAPackage("..payment.contract.event..")
                .should().dependOnClassesThat().resideInAnyPackage("org.springframework..","com.fasterxml.jackson..","..entity..","..mapper..","..coordination..")
                .check(contract)).isInstanceOf(AssertionError.class);
        var common = new ClassFileImporter().importClasses(com.qsx.appoint.common.messaging.archfixture.CommonToPayment.class);
        assertThatThrownBy(() -> noClasses().that().resideInAPackage("..common.messaging..")
                .should().dependOnClassesThat().resideInAnyPackage("..payment..","..appointment..","..coordination..")
                .check(common)).isInstanceOf(AssertionError.class);
    }

    @Test
    void inputEntityLeakIsRejectedIncludingRecordMembers() {
        assertThatThrownBy(() -> assertPublicReturnTypes(
                com.qsx.appoint.payment.application.archfixture.LeakingInputContract.class))
                .isInstanceOf(AssertionError.class);
    }

    @Test
    void paymentDoesNotReachIntoAppointmentInternals() {
        noClasses()
                .that().resideInAPackage("..payment..")
                .should().dependOnClassesThat().resideInAPackage("..appointment..")
                .because("支付写模块只能通过稳定 application interface 或事件与预约协作")
                .check(PRODUCTION_CLASSES);
    }

    @Test
    void appointmentDoesNotAddNewPaymentPersistenceDependencies() {
        appointmentPersistenceRule().check(PRODUCTION_CLASSES);
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
                .check(PRODUCTION_CLASSES);
    }

    @Test
    void appointmentBusinessLayersDoNotDependOnCoordination() {
        noClasses()
                .that().resideInAnyPackage(
                        "..appointment.application..", "..appointment.service..",
                        "..appointment.entity..", "..appointment.mapper..")
                .should().dependOnClassesThat().resideInAPackage("..coordination..")
                .because("协调层可以调用预约 application interface，预约业务层不能反向依赖协调层")
                .check(PRODUCTION_CLASSES);
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
                .check(PRODUCTION_CLASSES);
    }

    @Test
    void writeModulesDoNotDependOnBookingQuery() {
        noClasses()
                .that().resideInAnyPackage("..appointment..", "..payment..")
                .should().dependOnClassesThat().resideInAPackage("..bookingquery..")
                .because("写模块不能反向依赖只读组合投影")
                .check(PRODUCTION_CLASSES);
    }

    @Test
    void crossModuleReadSqlBelongsOnlyToBookingQuery() {
        for (var method : AppointmentOrderMapper.class.getDeclaredMethods()) {
            Select select = method.getAnnotation(Select.class);
            if (select != null) {
                assertThat(String.join(" ", select.value())).doesNotContain("payment_orders");
            }
        }

    }
    @Test
    void coordinationOnlyUsesWriteModuleInterfacesAndFacts() {
        implementationRule().check(PRODUCTION_CLASSES);
    }

    @Test
    void implementationRuleRejectsAConcreteWriteDependency() {
        var fixture = new ClassFileImporter().importClasses(
                com.qsx.appoint.coordination.archfixture.CoordinationToImplementationDependency.class,
                com.qsx.appoint.payment.application.archfixture.ForbiddenPaymentImplementation.class,
                com.qsx.appoint.payment.application.archfixture.ConcretePaymentWriter.class);
        assertThatThrownBy(() -> implementationRule().check(fixture)).isInstanceOf(AssertionError.class)
                .hasMessageContaining("ForbiddenPaymentImplementation").hasMessageContaining("ConcretePaymentWriter");
    }

    private ArchRule implementationRule() {
        var implementation = new com.tngtech.archunit.base.DescribedPredicate<com.tngtech.archunit.core.domain.JavaClass>("write application implementation") {
            @Override public boolean test(com.tngtech.archunit.core.domain.JavaClass type) {
                String name = type.getPackageName();
                boolean writeApplication = name.startsWith("com.qsx.appoint.appointment.application")
                        || name.startsWith("com.qsx.appoint.payment.application");
                if (!writeApplication) return false;
                try {
                    Class<?> reflected = Class.forName(type.getName(),false,getClass().getClassLoader());
                    if (reflected.isInterface() || reflected.isRecord() || reflected.isEnum()) return false;
                    return java.lang.reflect.Modifier.isPublic(reflected.getModifiers())
                            || type.isAnnotatedWith(org.springframework.stereotype.Service.class)
                            || type.isAnnotatedWith(org.springframework.stereotype.Component.class)
                            || type.getSimpleName().endsWith("Impl") || type.getSimpleName().startsWith("Default");
                } catch (ClassNotFoundException exception) { throw new IllegalStateException(exception); }
            }
        };
        return noClasses().that().resideInAPackage("..coordination..")
                .should().dependOnClassesThat(implementation).allowEmptyShould(true);
    }

    @Test
    void publicApplicationReturnsDoNotExposePersistenceEntities() throws Exception {
        for (var imported : PRODUCTION_CLASSES) {
            if (imported.getPackageName().contains(".application")) {
                var type = Class.forName(imported.getName(),false,getClass().getClassLoader());
                assertPublicReturnTypes(type);
            }
        }
    }

    @Test
    void entityReturnRuleRejectsDirectAndGenericLeaks() throws Exception {
        var type = com.qsx.appoint.payment.application.archfixture.LeakingPaymentContract.class;
        for (var method : type.getMethods()) {
            assertThat(containsEntity(method.getGenericReturnType(),new java.util.HashSet<>())).as(method.toString()).isTrue();
        }
        assertThatThrownBy(() -> assertPublicReturnTypes(type)).isInstanceOf(AssertionError.class);
    }

    private void assertPublicReturnTypes(Class<?> type) {
        if (!java.lang.reflect.Modifier.isPublic(type.getModifiers())) return;
        for (var method : type.getMethods()) {
            if (java.lang.reflect.Modifier.isPublic(method.getModifiers())) {
                if (type.isInterface()) {
                    for (var parameter : method.getGenericParameterTypes()) {
                        assertThat(containsEntity(parameter,new java.util.HashSet<>()))
                                .as("application入参不得泄漏Entity: %s",method).isFalse();
                    }
                }
                assertThat(containsEntity(method.getGenericReturnType(),new java.util.HashSet<>()))
                        .as("application返回值不得泄漏Entity: %s",method).isFalse();
            }
        }
    }

    private boolean containsEntity(java.lang.reflect.Type type, java.util.Set<java.lang.reflect.Type> visited) {
        if (!visited.add(type)) return false;
        if (type instanceof Class<?> concrete) {
            if (concrete.isRecord() && concrete.getName().startsWith("com.qsx.appoint.")) {
                for (var member : concrete.getRecordComponents()) {
                    if (containsEntity(member.getGenericType(),visited)) return true;
                }
            }
            return concrete.getPackageName().matches("com\\.qsx\\.appoint\\.(appointment|payment)\\.entity.*")
                    || (concrete.isArray() && containsEntity(concrete.getComponentType(),visited));
        }
        if (type instanceof java.lang.reflect.ParameterizedType generic) {
            if (containsEntity(generic.getRawType(),visited)) return true;
            for (var argument : generic.getActualTypeArguments()) if (containsEntity(argument,visited)) return true;
        }
        if (type instanceof java.lang.reflect.GenericArrayType array) return containsEntity(array.getGenericComponentType(),visited);
        if (type instanceof java.lang.reflect.WildcardType wildcard) {
            for (var bound : wildcard.getUpperBounds()) if (containsEntity(bound,visited)) return true;
            for (var bound : wildcard.getLowerBounds()) if (containsEntity(bound,visited)) return true;
        }
        if (type instanceof java.lang.reflect.TypeVariable<?> variable) {
            for (var bound : variable.getBounds()) if (containsEntity(bound,visited)) return true;
        }
        return false;
    }

    @Test
    void controllersDoNotAccessMappers() { controllerRule().check(PRODUCTION_CLASSES); }

    @Test
    void controllerRuleRejectsDirectMapperAccess() {
        var fixture = new ClassFileImporter().importClasses(
                com.qsx.appoint.appointment.controller.archfixture.ControllerToMapperDependency.class);
        assertThatThrownBy(() -> controllerRule().check(fixture)).isInstanceOf(AssertionError.class)
                .hasMessageContaining("AppointmentOrderMapper");
    }

    private ArchRule controllerRule() {
        return noClasses().that().areAnnotatedWith(org.springframework.web.bind.annotation.RestController.class)
                .should().dependOnClassesThat().resideInAnyPackage("..mapper..", "..persistence..")
                .allowEmptyShould(true);
    }

    @Test
    void queryMapperHasOnlyNonLockingStaticReadMethodsIncludingInheritedSurface() {
        assertReadOnly(BookingQueryMapper.class);
        assertReadOnly(com.qsx.appoint.bookingquery.persistence.DoctorVisitQueryMapper.class);
    }

    @Test
    void queryRuleRejectsInheritedAnnotatedDisguisedAndLockingWrites() {
        for (Class<?> fixture : com.qsx.appoint.architecture.fixture.WritableQueryFixtures.class.getDeclaredClasses()) {
            assertThatThrownBy(() -> assertReadOnly(fixture)).as(fixture.getName()).isInstanceOf(AssertionError.class);
        }
    }

    @Test
    void visitBusinessLayersOnlyUseStableExternalInterfaces() {
        noClasses().that().resideInAnyPackage("..visit.application..", "..visit.domain..", "..visit.entity..", "..visit.mapper..")
                .should().dependOnClassesThat().resideInAnyPackage("..bookingquery..", "..appointment.mapper..",
                        "..appointment.entity..", "..payment..", "..catalog.mapper..", "..catalog.entity..",
                        "..user.mapper..", "..user.entity..")
                .check(PRODUCTION_CLASSES);
        noClasses().that().resideInAnyPackage("..appointment..", "..payment..")
                .should().dependOnClassesThat().resideInAnyPackage("..visit.application..", "..visit.entity..", "..visit.mapper..")
                .check(PRODUCTION_CLASSES);
    }

    private void assertReadOnly(Class<?> mapper) {
        assertThat(com.baomidou.mybatisplus.core.mapper.BaseMapper.class.isAssignableFrom(mapper)).isFalse();
        for (var method : mapper.getMethods()) {
            var select = method.getAnnotation(Select.class);
            assertThat(select).as("只读Mapper所有公开方法必须声明静态SELECT: %s",method).isNotNull();
            for (Class<? extends java.lang.annotation.Annotation> write : java.util.List.of(Insert.class,Update.class,Delete.class,
                    org.apache.ibatis.annotations.InsertProvider.class,org.apache.ibatis.annotations.UpdateProvider.class,
                    org.apache.ibatis.annotations.DeleteProvider.class)) {
                assertThat(method.getAnnotation(write)).isNull();
            }
            String sql = String.join(" ",select.value()).toUpperCase(java.util.Locale.ROOT);
            assertThat(sql).doesNotMatch("(?s).*\\b(INSERT|UPDATE|DELETE|REPLACE|MERGE|CALL)\\b.*");
            assertThat(sql).doesNotMatch("(?s).*\\b(FOR\\s+SHARE|LOCK\\s+IN\\s+SHARE\\s+MODE)\\b.*");
        }
    }

}
