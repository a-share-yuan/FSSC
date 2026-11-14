# -*- coding: utf-8 -*-
"""FSSC 冒烟全集 CI 入口：依次跑 5 个 smoke 脚本，汇总耗时与退出码，任一失败整体 exit 1。

仅标准库；子脚本一律用 sys.executable 调起，与解释器环境保持一致。

──────────────────────────────────────────────────────────────────────
环境变量清单（全部可选，缺省 = 本机开发默认值，见 smoke_env.py）：
  FSSC_BASE_URL      后端 API 前缀，默认 http://127.0.0.1:18080/api
  FSSC_MYSQL_BIN     mysql CLI 全路径（Windows 默认 C:\\Program Files\\MySQL\\...\\mysql.exe；Linux CI 用 /usr/bin/mysql）
  FSSC_DB_HOST / FSSC_DB_PORT / FSSC_DB_USER / FSSC_DB_PASS / FSSC_DB_NAME
                     数据库连接，默认 192.168.20.77 / 3306 / root / 123456 / fssc
  FSSC_SMOKE_USER / FSSC_SMOKE_PASS / FSSC_SMOKE_TENANT
                     冒烟登录账号，默认 alice / 123456 / DEMO1

GitHub Actions 接入示例（服务 + MySQL service 起来后跑冒烟）：
  jobs:
    smoke:
      runs-on: ubuntu-latest
      services:
        mysql:
          image: mysql:8.0
          env: { MYSQL_ROOT_PASSWORD: ${{ secrets.FSSC_DB_PASS }}, MYSQL_DATABASE: fssc }
          ports: ['3306:3306']
          options: --health-cmd="mysqladmin ping" --health-interval=10s
      steps:
        - uses: actions/checkout@v4
        - uses: actions/setup-java@v4
          with: { distribution: temurin, java-version: '26' }
        - uses: actions/setup-python@v5
          with: { python-version: '3.11' }
        - name: Build & start server
          run: |
            cd fssc-server && mvn -q -DskipTests package
            nohup java -jar fssc-start/target/fssc-server.jar \
              --spring.profiles.active=dev --server.port=18080 &
            for i in $(seq 1 60); do curl -sf http://127.0.0.1:18080/api/auth/login >/dev/null 2>&1 && break; sleep 2; done
        - name: Smoke suite
          env:
            FSSC_BASE_URL: http://127.0.0.1:18080/api
            FSSC_MYSQL_BIN: /usr/bin/mysql
            FSSC_DB_HOST: 192.168.20.77
            FSSC_DB_USER: root
            FSSC_DB_PASS: ${{ secrets.FSSC_DB_PASS }}
            FSSC_DB_NAME: fssc
          run: python fssc/scripts/run-all-smokes.py

Jenkins pipeline 接入示例：
  stage('Smoke') {
    steps {
      withEnv([
        "FSSC_BASE_URL=http://127.0.0.1:18080/api",
        "FSSC_MYSQL_BIN=/usr/bin/mysql",
        "FSSC_DB_HOST=db.internal", "FSSC_DB_PORT=3306",
        "FSSC_DB_USER=ci", "FSSC_DB_PASS=${CI_DB_PASS}", "FSSC_DB_NAME=fssc"]) {
        sh 'python3 scripts/run-all-smokes.py'   // 非零退出码即 fail stage
      }
    }
  }
──────────────────────────────────────────────────────────────────────
"""
import os
import subprocess
import sys
import time

HERE = os.path.dirname(os.path.abspath(__file__))
# 核心域集成 E2E（T0.2）：五域契约 + 跨域贯通，只读、零数据污染。
# 以子脚本方式纳入，账号走环境变量，缺省用共享会计（覆盖面最广）。
CORE_DOMAINS = "e2e-core-domains.py"
SCRIPTS = [
    "smoke-claim.py",
    "smoke-p3-batch.py",
    "smoke-p0fix.py",
    "smoke-archive-destroy-branch.py",
    "smoke-rc-overdue-job.py",
    "smoke-overseas-ocr.py",
    "smoke-multicompany.py",
    "smoke-security-guard.py",
]

# 这些脚本默认指向 18080 + alice；本机/容器实跑端口与账号不同，
# 需显式覆盖的场景在此列出（env 已由调用方设置时尊重调用方）。
ENV_OVERRIDES = {
    CORE_DOMAINS: {
        # 不设则用 e2e-core-domains.py 自带的 8089/Y000185 默认值
    },
}


def main():
    results = []
    plan = [CORE_DOMAINS] + SCRIPTS
    for name in plan:
        path = os.path.join(HERE, name)
        print("=" * 70)
        print("[SMOKE_ALL] >>> %s" % name)
        t0 = time.time()
        env = dict(os.environ)
        env["PYTHONIOENCODING"] = "utf-8"
        env.update(ENV_OVERRIDES.get(name, {}))
        rc = subprocess.run([sys.executable, "-X", "utf8", path], cwd=HERE, env=env).returncode
        dt = time.time() - t0
        status = "PASS" if rc == 0 else "FAIL"
        results.append((name, rc, dt, status))
        print("[SMOKE_ALL] <<< %s %s (exit=%s, %.1fs)" % (name, status, rc, dt))

    passed = sum(1 for r in results if r[3] == "PASS")
    failed = len(results) - passed
    print("=" * 70)
    print("[SMOKE_ALL] summary: total=%d passed=%d failed=%d" % (len(results), passed, failed))
    for name, rc, dt, status in results:
        print("[SMOKE_ALL]   %-36s %-4s exit=%-3s %6.1fs" % (name, status, rc, dt))
    return 1 if failed else 0


if __name__ == "__main__":
    sys.exit(main())
