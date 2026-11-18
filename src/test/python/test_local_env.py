import importlib.util
import shutil
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path
from unittest.mock import Mock, patch


SCRIPT = Path(__file__).resolve().parents[3] / 'scripts/local_env.py'
spec = importlib.util.spec_from_file_location('local_env', SCRIPT)
local_env = importlib.util.module_from_spec(spec)
spec.loader.exec_module(local_env)


class LocalEnvironmentTest(unittest.TestCase):
    def test_start_defaults_to_demo_and_respects_an_explicit_environment(self):
        with tempfile.TemporaryDirectory() as state, patch.object(local_env, 'STATE', Path(state)):
            for arguments, expected in [(['start'], 'demo'), (['start', 'test'], 'test')]:
                with patch.object(sys, 'argv', [str(SCRIPT), *arguments]), patch.object(local_env, 'start') as start:
                    local_env.main()
                    start.assert_called_once_with(expected)

    def test_concurrent_start_is_rejected_before_launching_any_application(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            script = root / 'scripts/local_env.py'
            script.parent.mkdir()
            shutil.copyfile(SCRIPT, script)
            with patch.object(local_env, 'STATE', root / '.local-env'), local_env.operation_lock():
                result = subprocess.run([sys.executable, str(script), 'start', 'test'], text=True,
                                        capture_output=True, timeout=5)
                self.assertNotEqual(result.returncode, 0)
                self.assertIn('已有启停或维护操作正在执行', result.stderr)
                self.assertFalse((root / '.local-env/test.json').exists())

    def test_same_profile_from_another_launch_is_rejected(self):
        with patch.object(local_env, 'environment_status', return_value={
                'profile': 'demo', 'ready': True, 'launchId': 'another-process'}):
            with self.assertRaisesRegex(RuntimeError, '其他运行实例'):
                local_env.wait_ready(Mock(poll=lambda: None), local_env.BACKEND_URL, 'demo', 'our-process', '后端', 1)


if __name__ == '__main__':
    unittest.main()
