> 历史验收材料：正文中的时间、命令、路径和“当前”均属于原检查点；当前入口见[项目状态](../../../../../docs/project-status.md)。导航链接已迁移。

# 第04步原测试方法核对

起点963051f，共57个测试类，260个带@Test/@ParameterizedTest的方法（当时简化脚本漏记两个全限定注解，初报258）；到第04步没有删除测试方法。参数化执行用例总数仍由实际Maven报告确定。

唯一业务方法改名：InitialSchemaMigrationTest#terminalPreRefactorPaymentPassesV4WithoutBeingRewritten → terminalPreRefactorPaymentPassesV4AndV5WithoutBeingRewritten；显式目标V5验证legacy字段，其余原断言保留。

原注解方法清单见 original-test-methods.json；原source/helper混合清单继续保留，后续05/07以注解方法和独有断言为准。

07复核补记：当时简化正则漏记两个全限定@Test方法，实际原始方法数为260；执行用例323及无删除结论不受影响，详见[最终方法审计](method-audit.md)。
