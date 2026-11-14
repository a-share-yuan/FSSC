# -*- coding: utf-8 -*-
"""校验 Spring Boot 胖包完整性（防"瘦 jar 事故"）。

背景：2026-09-16 事故——`mvn package` 的 repackage 阶段被中断，留下只含业务类的
瘦包（~2.9MB，缺 BOOT-INF/lib 依赖），启动后懒加载类 CNFE 散发 500。本脚本用
"BOOT-INF/lib 条目数 + 体积 + 关键 class 时间戳"三件套做交付前硬校验。

用法:
    python verify-jar-fat.py                       # 校验默认包
    python verify-jar-fat.py <path/to/x.jar>       # 指定包
退出码: 0 通过 / 1 不合格
"""
import sys, os, zipfile, datetime

JAR = sys.argv[1] if len(sys.argv) > 1 else \
    r'fssc-server/fssc-start/target/fssc-server.jar'

MIN_LIB = 150          # BOOT-INF/lib 条目下限（正常 192）
MIN_SIZE = 60 * 1024 * 1024   # 体积下限 60MB（瘦包 ~2.9MB）

KEY_CLASSES = [
    'com/fssc/workflow/service/WfEngineFacade.class',
    'com/fssc/workflow/job/WfTimeoutJob.class',
    'com/fssc/workflow/job/WfDelegateExpireJob.class',
    'com/fssc/workflow/service/WfDelegateService.class',
    'com/fssc/workflow/controller/WfTaskController.class',
]

if not os.path.exists(JAR):
    print('FAIL: jar 不存在 ->', JAR); sys.exit(1)

st = os.stat(JAR)
size = st.st_size
mtime = datetime.datetime.fromtimestamp(st.st_mtime)
print(f'jar : {JAR}')
print(f'体积: {size:,} B  ({size/1024/1024:.1f} MB)  mtime={mtime:%Y-%m-%d %H:%M:%S}')

try:
    z = zipfile.ZipFile(JAR)
except Exception as e:
    print('FAIL: 不是合法 zip ->', e); sys.exit(1)

names = z.namelist()
libs = [n for n in names if n.startswith('BOOT-INF/lib/')]
classes = [n for n in names if n.startswith('BOOT-INF/classes/')]

ok = True
print(f'BOOT-INF/lib      : {len(libs)} 条目 (要求 >= {MIN_LIB})')
if len(libs) < MIN_LIB: print('  FAIL: 依赖库缺失，疑似瘦包'); ok = False
print(f'BOOT-INF/classes  : {len(classes)} 条目')
if not classes: print('  FAIL: 无业务类'); ok = False
if size < MIN_SIZE: print(f'  FAIL: 体积 < {MIN_SIZE/1024/1024:.0f}MB，疑似瘦包'); ok = False

print('\n关键 class 时间戳:')
for c in KEY_CLASSES:
    full = 'BOOT-INF/classes/' + c
    try:
        info = z.getinfo(full)
        print(f'  {datetime.datetime(*info.date_time):%m-%d %H:%M:%S}  {info.file_size:>8,}B  {c.split("/")[-1]}')
    except KeyError:
        print(f'  MISSING   {c.split("/")[-1]}'); ok = False

# repackage 备份（存在说明 spring-boot:repackage 正常执行过）
orig = JAR + '.original'
print(f'\nrepackage 备份 (.original): {"存在 " + f"{os.stat(orig).st_size:,}B" if os.path.exists(orig) else "不存在"}')

print('\n==> ' + ('PASS  胖包完整，可部署' if ok else 'FAIL  包不合格，禁止启动'))
sys.exit(0 if ok else 1)
