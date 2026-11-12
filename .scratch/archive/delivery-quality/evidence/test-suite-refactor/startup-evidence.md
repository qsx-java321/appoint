> 历史验收材料：正文中的时间、命令、路径和“当前”均属于原检查点；当前入口见[项目状态](../../../../../docs/project-status.md)。导航链接已迁移。

# 第06步启动与扫描证据

- PaymentGatewayContractTest在@BeforeEach直接新建MockPaymentGateway与PaymentGatewayRegistry；无SpringBootTest、Autowired和ActiveProfiles。7项渠道契约保留。实际报告0.026秒，没有Started PaymentGatewayContractTest日志。
- ModuleDependencyTest使用static final PRODUCTION_CLASSES，只执行一次DO_NOT_INCLUDE_TESTS生产扫描；所有20项规则及负例保留。违规样例仍各自importClasses，不混进生产集合。实际报告2.017秒。
- ./mvnw -o test：340项，零失败/错误/跳过，36.942秒。全套时间受机器、缓存与测试数量影响，不作为固定加速承诺。
- LegacyDevRouteAbsenceTest、LegacyTestRouteAbsenceTest、MockPayProductionProfileTest继续验证真实Spring装配和profile；PaymentAttemptIntegrationTest等继续实际通过Registry调用Mock渠道。
- 首次编译遗漏架构字段迭代处的重命名，修正全部引用后重跑全量通过。初次日志/tmp/test-refactor-06-compile-first.log，不计通过结果。
