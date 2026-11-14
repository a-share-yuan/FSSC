# -*- coding: utf-8 -*-
"""
FSSC 构建 + 重启 一体化脚本（固化 plain jar 事故防线）。

为什么需要它
------------
打包报 `Unable to rename 'fssc-server.jar' to 'fssc-server.jar.original'`
= **运行中的 JVM 锁住了 jar**。此时若继续，repackage 失败会留下一个
4MB 的瘦 jar（没有 BOOT-INF/lib），下次重启必挂 —— 这就是记忆里的「plain jar 事故」。
记忆里已写铁律「停服打包」，但连续几轮反复踩，根因是**停服与打包被写成了两条命令**。

本脚本把正确顺序固化成唯一入口：
    ① 找到监听 8089 的进程 → ② 停掉 → ③ 端口复核 → ④ mvn package
    → ⑤ **校验 jar 大小**（<20MB 判定为瘦 jar，直接失败）→ ⑥ 后台起服务 → ⑦ 轮询健康

用法
----
python scripts/build_and_restart.py            # 完整流程
python scripts/build_and_restart.py --no-start # 只打包不起服务
python scripts/build_and_restart.py --skip-build  # 只重启（打包已在别处完成）
"""
import subprocess, sys, os, time, io, json
import urllib.request

SERVER_DIR = r'D:\SourceCode\Ashare\fssc\fssc-server'
JAR = os.path.join(SERVER_DIR, 'fssc-start', 'target', 'fssc-server.jar')
JAVA = r'D:\Java\jdk-26.0.1\bin\java.exe'
PORT = 8089
# 正常 fat jar ≈ 88MB；瘦 jar ≈ 4MB。用 20MB 作分界，容错充足。
MIN_JAR_MB = 20

def sh(cmd, **kw):
    return subprocess.run(cmd, capture_output=True, text=True,
                          encoding='utf-8', errors='ignore', **kw)

def find_listener_pids():
    """用 netstat 找监听 8089 的 PID（不依赖 PowerShell）"""
    out = sh(['netstat', '-ano']).stdout
    pids = set()
    for line in out.splitlines():
        parts = line.split()
        if len(parts) >= 5 and parts[0].upper() == 'TCP' and f':{PORT}' in parts[1] \
                and parts[3] == 'LISTENING':
            pids.add(parts[-1])
    return pids

def stop_service():
    pids = find_listener_pids()
    if not pids:
        print('[1/5] 无服务在跑，跳过停服')
        return
    for pid in pids:
        print('[1/5] 停服 PID=%s' % pid)
        sh(['taskkill', '/PID', pid, '/F'])
    for _ in range(15):
        if not find_listener_pids():
            break
        time.sleep(1)
    left = find_listener_pids()
    if left:
        print('!! 端口 %d 仍被占用: %s' % (PORT, left))
        sys.exit(1)
    print('[2/5] 端口 %d 已释放' % PORT)

def build():
    print('[3/5] mvn package ...')
    r = sh(['mvn.cmd', '-o', '-DskipTests', 'package'], cwd=SERVER_DIR)
    out = (r.stdout or '') + (r.stderr or '')
    lines = [l for l in out.splitlines() if 'ERROR' in l or 'BUILD' in l]
    print('\n'.join(lines[:12]))
    if r.returncode != 0:
        print('!! 编译失败，未启动服务')
        sys.exit(1)
    verify_jar()

def verify_jar():
    """jar 大小校验：防瘦 jar 事故的最后一道闸"""
    if not os.path.exists(JAR):
        print('!! jar 不存在: %s' % JAR)
        sys.exit(1)
    mb = os.path.getsize(JAR) / 1024 / 1024
    if mb < MIN_JAR_MB:
        print('!! jar 只有 %.1f MB —— 这是瘦 jar（缺 BOOT-INF/lib），不能用于启动！' % mb)
        print('   根因通常是 repackage 时 JVM 还锁着旧 jar。先停服再打包。')
        sys.exit(1)
    print('[4/5] jar 校验通过: %.2f MB（fat jar 正常）' % mb)

def health(timeout=150):
    deadline = time.time() + timeout
    while time.time() < deadline:
        try:
            with urllib.request.urlopen(
                    'http://127.0.0.1:%d/actuator/health' % PORT, timeout=5) as r:
                if r.status == 200:
                    return True
        except Exception:
            pass
        time.sleep(4)
    return False

def start():
    print('[5/5] 启动服务 ...')
    # 关键：用 Bash run_in_background 起前台阻塞的 java，沙箱里这样进程才会常驻；
    # 在脚本内 subprocess 起会随脚本退出被回收。
    log = io.open(os.path.join(SERVER_DIR, 'logs', 'startup.out'), 'a', encoding='utf-8')
    subprocess.Popen(
        [JAVA, '-jar', JAR, '--spring.profiles.active=dev',
         '--server.port=%d' % PORT],
        cwd=SERVER_DIR, stdout=log, stderr=subprocess.STDOUT,
        creationflags=getattr(subprocess, 'CREATE_NEW_PROCESS_GROUP', 0))
    log.close()
    if health():
        print('     健康检查通过: http://127.0.0.1:%d/actuator/health' % PORT)
        return True
    print('!! 启动超时，请查 logs/startup.out')
    return False

def main():
    args = sys.argv[1:]
    no_start = '--no-start' in args
    skip_build = '--skip-build' in args
    if not skip_build:
        stop_service()
        build()
    else:
        verify_jar()
    if not no_start:
        if not start():
            sys.exit(1)
    print('完成')

if __name__ == '__main__':
    main()
