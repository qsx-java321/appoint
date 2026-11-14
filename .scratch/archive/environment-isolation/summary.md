# dev、test、demo环境改造

目标：三环境共用Compose中的真实MySQL、Redis、RabbitMQ，配置和数据独立；dev保留数据，test支持显式重置与业务调时，demo每次后端启动恢复初始状态。

是否完成：已完成。
