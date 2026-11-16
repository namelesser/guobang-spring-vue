"""监控回归：所有远程操作均模拟，不触碰生产服务。"""
import contextlib
import io
import subprocess
import unittest
from unittest.mock import patch

import check_and_recover as monitor


def result(code=0, stdout="true\n", stderr=""):
    return subprocess.CompletedProcess([], code, stdout, stderr)


class MonitorTests(unittest.TestCase):
    def test_probe_errors_are_unknown_and_never_restart(self):
        for response in (result(255, "", "连接失败"),
                         result(1, "false\n", "inspect 失败"),
                         result(0, ""), result(0, "unexpected")):
            with self.subTest(response=response), \
                 patch.object(monitor, "ssh", return_value=response), \
                 patch.object(monitor, "restart") as restart, \
                 patch.object(monitor, "public_ok", return_value=True) as http:
                self.assertIsNone(monitor.container_ok("guobang-backend"))
                with contextlib.redirect_stdout(io.StringIO()) as output:
                    monitor.main()
                restart.assert_not_called()
                http.assert_called_once()
                self.assertIn("未知", output.getvalue())

    def test_connection_failures_retry_at_most_three_times(self):
        for failure in (result(255, "", "连接失败"),
                        subprocess.TimeoutExpired("ssh", 20)):
            with self.subTest(failure=failure), \
                 patch.object(monitor, "run") as run, \
                 patch("time.sleep"), \
                 patch.object(monitor, "restart") as restart, \
                 patch.object(monitor, "public_ok", return_value=True) as http:
                if isinstance(failure, Exception):
                    run.side_effect = failure
                else:
                    run.return_value = failure
                with contextlib.redirect_stdout(io.StringIO()) as output:
                    monitor.main()
                self.assertEqual(run.call_count, 6)
                restart.assert_not_called()
                http.assert_called_once()
                self.assertIn("未知", output.getvalue())

    def test_transient_connection_failure_recovers(self):
        with patch.object(monitor, "run", side_effect=[result(255), result()]) as run, \
             patch("time.sleep"):
            self.assertIs(monitor.container_ok("guobang-backend"), True)
            self.assertEqual(run.call_count, 2)

    def test_inspect_error_is_not_retried(self):
        with patch.object(monitor, "run", return_value=result(1)) as run:
            self.assertIsNone(monitor.container_ok("guobang-backend"))
            run.assert_called_once()

    def test_unknown_alert_keeps_bounded_diagnostics(self):
        with patch.object(monitor, "ssh", return_value=result(255, "", "reset " + "x" * 500)), \
             patch.object(monitor, "restart") as restart, \
             patch.object(monitor, "public_ok", return_value=True), \
             contextlib.redirect_stdout(io.StringIO()) as output:
            monitor.main()
        self.assertIn("exitcode=255", output.getvalue())
        self.assertIn("reset", output.getvalue())
        self.assertNotIn("x" * 301, output.getvalue())
        restart.assert_not_called()

    def test_only_explicit_false_triggers_restart(self):
        for value, expected in (("true\n", 0), ("false\n", 2)):
            with self.subTest(value=value), \
                 patch.object(monitor, "ssh", return_value=result(0, value)), \
                 patch.object(monitor, "restart", return_value=True) as restart, \
                 patch.object(monitor, "public_ok", return_value=True), \
                 contextlib.redirect_stdout(io.StringIO()) as output:
                monitor.main()
                self.assertEqual(restart.call_count, expected)
                if not expected:
                    self.assertEqual(output.getvalue(), "")

    def test_restart_reads_back_state(self):
        for readback, expected in ((result(), True), (result(0, "false"), False),
                                   (result(1), False), (result(255), False)):
            with self.subTest(readback=readback), \
                 patch.object(monitor, "run", side_effect=[result(0, ""), readback,
                                                          readback, readback]) as run, \
                 patch("time.sleep"):
                self.assertIs(monitor.restart("guobang-backend"), expected)
                commands = [call.args[-1] for call in run.call_args_list]
                self.assertTrue(any(cmd.startswith("docker inspect") for cmd in commands))
                self.assertEqual(sum(cmd.startswith("docker restart") for cmd in commands), 1)

    def test_restart_connection_failure_is_not_retried(self):
        for failure in (result(255), subprocess.TimeoutExpired("ssh", 20)):
            with self.subTest(failure=failure), patch.object(monitor, "run") as run, \
                 patch("time.sleep"):
                if isinstance(failure, Exception):
                    run.side_effect = failure
                else:
                    run.return_value = failure
                self.assertIs(monitor.restart("guobang-backend"), False)
                run.assert_called_once()

    def test_http_failure_is_reported_even_when_probe_unknown(self):
        with patch.object(monitor, "ssh", return_value=result(255)), \
             patch.object(monitor, "public_ok", return_value=False), \
             contextlib.redirect_stdout(io.StringIO()) as output:
            monitor.main()
        self.assertIn("站点不可用", output.getvalue())


if __name__ == "__main__":
    unittest.main()
