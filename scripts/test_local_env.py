"""环境脚本单元/Mock测试；只使用临时配置，不连接Docker或常驻资源。"""
import contextlib
import io
import stat
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

import local_env


class LocalEnvironmentScriptTest(unittest.TestCase):
    def setUp(self):
        temporary = tempfile.TemporaryDirectory()
        self.addCleanup(temporary.cleanup)
        self.root = Path(temporary.name)
        self.state = self.root / '.local-env'
        self.mock('ROOT', self.root)
        self.mock('STATE', self.state)
        self.output = io.StringIO()
        output = contextlib.redirect_stdout(self.output)
        output.__enter__()
        self.addCleanup(output.__exit__, None, None, None)
        self.names = ('compose.env', '.env.dev', '.env.test', '.env.demo')
        (self.root / 'compose.env.example').write_text('MYSQL_PASSWORD=compose-test-only\n')
        for name in local_env.PORTS:
            user = 'appoint' if name == 'dev' else 'appoint_' + name
            (self.root / ('.env.' + name + '.example')).write_text(
                f'MYSQL_USER={user}\nMYSQL_PASSWORD=db-test-only-{name}\n'
                f'RABBITMQ_USER=appoint_{name}\nRABBITMQ_PASSWORD=mq-test-only-{name}\n'
                f'JWT_SECRET=jwt-test-only-{name}\n')

    def mock(self, name, *args, **kwargs):
        replacement = patch.object(local_env, name, *args, **kwargs)
        result = replacement.start()
        self.addCleanup(replacement.stop)
        return result

    def init_mocks(self):
        run = self.mock('run', return_value='')

        def mysql(sql):
            if sql.startswith('SHOW GRANTS'):
                user = sql.split("'")[1]
                db = user if user != 'appoint_dev' else 'appoint'
                return f'GRANT ALL PRIVILEGES ON `{db}`.* TO account'
            return ''

        self.mock('mysql', side_effect=mysql)

        def rabbit(*args):
            if args[0] == 'list_user_permissions':
                return '/' + args[1].replace('_', '-') + '\t.*\t.*\t.*'
            return ''

        self.mock('rabbit', side_effect=rabbit)
        return run

    def cutover_mocks(self, tables='', pending='0', queues=''):
        socket = self.mock('socket').socket.return_value.__enter__.return_value
        socket.connect_ex.return_value = 1
        mysql = self.mock('mysql', side_effect=[tables, pending] if tables else [''])
        rabbit = self.mock('rabbit', return_value=queues)
        return socket, mysql, rabbit

    def test_init_restores_all_configurations_from_templates_with_private_permissions(self):
        run = self.init_mocks()
        local_env.init()
        for name in self.names:
            actual = self.root / name
            self.assertEqual(actual.read_bytes(), (self.root / (name + '.example')).read_bytes())
            self.assertEqual(stat.S_IMODE(actual.stat().st_mode), 0o600)
        self.assertEqual(run.call_args_list[0].args[0], local_env.COMPOSE + ['up', '-d', '--wait'])
        self.assertNotIn('db-test-only', self.output.getvalue())
        self.assertNotIn('mq-test-only', self.output.getvalue())

    def test_repeated_init_preserves_existing_configuration_and_uses_existing_password(self):
        run = self.init_mocks()
        local_env.init()
        path = self.root / '.env.test'
        path.write_text(path.read_text().replace('db-test-only-test', 'existing-test-password'))
        before = {name: (self.root / name).read_bytes() for name in self.names}
        local_env.init()
        self.assertEqual(before, {name: (self.root / name).read_bytes() for name in self.names})
        self.assertTrue(any(call.args[1:] == ('existing-test-password\n',)
                            for call in run.call_args_list))

    def test_existing_configuration_does_not_require_a_template(self):
        path = self.root / '.env.dev'
        path.write_text('existing-config\n')
        (self.root / '.env.dev.example').unlink()
        local_env.ensure_config('.env.dev')
        self.assertEqual(path.read_text(), 'existing-config\n')

    def test_missing_template_stops_before_starting_compose(self):
        run = self.mock('run')
        (self.root / '.env.demo.example').unlink()
        with self.assertRaisesRegex(RuntimeError, '缺少配置模板'):
            local_env.init()
        run.assert_not_called()

    def test_concurrent_configuration_creation_preserves_the_winning_file(self):
        def competing_write(path, text):
            path.write_text('created-by-other-process\n')
            raise FileExistsError()

        self.mock('private_write', side_effect=competing_write)
        local_env.ensure_config('.env.dev')
        self.assertEqual((self.root / '.env.dev').read_text(), 'created-by-other-process\n')

    def test_excess_database_privileges_stop_initialization(self):
        self.init_mocks()
        mysql = self.mock('mysql', return_value='GRANT ALL PRIVILEGES ON *.* TO account')
        with self.assertRaisesRegex(RuntimeError, '其他库或全局权限'):
            local_env.init()
        self.assertEqual(mysql.call_count, 1)

    def test_password_mismatch_does_not_attempt_to_change_existing_credentials(self):
        run = self.init_mocks()

        def fail_verification(args, data=None):
            if 'verify' in args:
                raise RuntimeError('凭据不匹配')
            return ''

        run.side_effect = fail_verification
        with self.assertRaisesRegex(RuntimeError, '凭据不匹配'):
            local_env.init()
        sql = [call.args[0] for call in local_env.mysql.call_args_list]
        self.assertFalse(any('ALTER USER' in statement or 'DROP ' in statement for statement in sql))

    def test_maven_start_and_reset_allow_first_time_dependency_downloads(self):
        for name in local_env.PORTS:
            command = local_env.command_for(name)
            self.assertNotIn('-o', command)
            self.assertIn('spring-boot:run', command)
            self.assertIn('--spring.profiles.active=' + name, command[-1])
        reset = local_env.command_for('test', True)
        self.assertNotIn('-o', reset)
        self.assertIn('--appoint.local-environment.reset-only=true', reset[-1])
        self.assertIn('--appoint.messaging.enabled=false', reset[-1])

    def test_empty_database_preflight_checks_old_messages_without_querying_business_tables(self):
        _, mysql, rabbit = self.cutover_mocks()
        local_env.cutover()
        self.assertEqual(mysql.call_count, 1)
        rabbit.assert_called_once_with('list_queues', '-p', '/', 'name', 'messages_ready', 'messages_unacknowledged')
        self.assertTrue((self.state / 'dev-cutover-ready').is_file())

    def test_settled_existing_database_checks_created_payment_attempts(self):
        tables = '\n'.join(('payment_outbox_events', 'payment_orders', 'payment_refunds',
                            'payment_attempts', 'appointment_orders', 'users'))
        _, mysql, _ = self.cutover_mocks(tables=tables)
        local_env.cutover()
        self.assertIn("status IN ('CREATED','PROCESSING')", mysql.call_args_list[1].args[0])
        self.assertTrue((self.state / 'dev-cutover-ready').is_file())

    def test_partial_schema_does_not_receive_a_preflight_marker(self):
        _, _, rabbit = self.cutover_mocks(tables='users\nflyway_schema_history')
        with self.assertRaisesRegex(RuntimeError, '业务表不完整'):
            local_env.cutover()
        rabbit.assert_not_called()
        self.assertFalse(self.state.exists())

    def test_pending_financial_work_blocks_preflight(self):
        tables = '\n'.join(('payment_outbox_events', 'payment_orders', 'payment_refunds',
                            'payment_attempts', 'appointment_orders'))
        _, _, rabbit = self.cutover_mocks(tables=tables, pending='1')
        with self.assertRaisesRegex(RuntimeError, '未完成资金'):
            local_env.cutover()
        rabbit.assert_not_called()
        self.assertFalse(self.state.exists())

    def test_ready_or_unacknowledged_old_messages_block_even_an_empty_database(self):
        for queues in ('old-queue\t1\t0', 'old-queue\t0\t1'):
            with self.subTest(queues=queues):
                self.cutover_mocks(queues=queues)
                with self.assertRaisesRegex(RuntimeError, '旧vhost仍有消息'):
                    local_env.cutover()
                self.assertFalse(self.state.exists())

    def test_occupied_backend_port_blocks_preflight_before_database_access(self):
        socket, mysql, rabbit = self.cutover_mocks()
        socket.connect_ex.return_value = 0
        with self.assertRaisesRegex(RuntimeError, '先停止旧dev'):
            local_env.cutover()
        mysql.assert_not_called()
        rabbit.assert_not_called()

    def test_failed_database_query_does_not_mark_a_database_as_empty(self):
        _, mysql, rabbit = self.cutover_mocks()
        mysql.side_effect = RuntimeError('数据库不可用')
        with self.assertRaisesRegex(RuntimeError, '数据库不可用'):
            local_env.cutover()
        rabbit.assert_not_called()
        self.assertFalse(self.state.exists())


if __name__ == '__main__':
    unittest.main()
