# -*- coding: utf-8 -*-
"""FSSC 冒烟脚本统一环境参数入口（仅标准库，无第三方依赖）。

所有冒烟脚本的 BASE_URL / MySQL CLI 路径 / 库凭据 / 登录账号一律从这里读取，
环境变量未设置时使用与历史硬编码一致的默认值，保证本机零配置可直接跑。

支持的环境变量（默认值 = 原脚本硬编码值）：
  FSSC_BASE_URL    后端 API 前缀          默认 http://127.0.0.1:18080/api
  FSSC_MYSQL_BIN   mysql.exe 全路径       默认 C:\\Program Files\\MySQL\\MySQL Server 8.0\\bin\\mysql.exe
  FSSC_DB_HOST     数据库主机             默认 192.168.20.77
  FSSC_DB_PORT     数据库端口             默认 3306（指定非空时 mysql 参数追加 -P）
  FSSC_DB_USER     数据库用户             默认 root
  FSSC_DB_PASS     数据库密码             默认 123456
  FSSC_DB_NAME     数据库名               默认 fssc
  FSSC_SMOKE_USER  冒烟登录账号           默认 alice
  FSSC_SMOKE_PASS  冒烟登录密码           默认 123456
  FSSC_SMOKE_TENANT冒烟租户编码           默认 DEMO1
"""
import os
import subprocess

_DEFAULTS = {
    "FSSC_BASE_URL": "http://127.0.0.1:18080/api",
    "FSSC_MYSQL_BIN": r"C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe",
    "FSSC_DB_HOST": "192.168.20.77",
    "FSSC_DB_PORT": "3306",
    "FSSC_DB_USER": "root",
    "FSSC_DB_PASS": "123456",
    "FSSC_DB_NAME": "fssc",
    "FSSC_SMOKE_USER": "alice",
    "FSSC_SMOKE_PASS": "123456",
    "FSSC_SMOKE_TENANT": "DEMO1",
}


def _env(name):
    v = os.environ.get(name)
    return v if v not in (None, "") else _DEFAULTS[name]


def base_url():
    """后端 API 前缀（不含具体路径）。"""
    return _env("FSSC_BASE_URL")


def mysql_bin():
    """mysql CLI 可执行文件全路径。"""
    return _env("FSSC_MYSQL_BIN")


def db_name():
    return _env("FSSC_DB_NAME")


def mysql_args():
    """mysql CLI 连接参数（不含 -e 语句本体）。FSSC_DB_PORT 指定时追加 -P。"""
    args = [mysql_bin(),
            "-h" + _env("FSSC_DB_HOST"),
            "-u" + _env("FSSC_DB_USER"),
            "-p" + _env("FSSC_DB_PASS")]
    port = _env("FSSC_DB_PORT")
    if port:
        args.append("-P" + port)
    args.append(_env("FSSC_DB_NAME"))
    args.append("--default-character-set=utf8mb4")
    return args


def login_payload():
    """登录请求体（与 /auth/login 契约保持一致）。"""
    return {"username": _env("FSSC_SMOKE_USER"),
            "password": _env("FSSC_SMOKE_PASS"),
            "tenantCode": _env("FSSC_SMOKE_TENANT"),
            "subjectType": "TENANT"}


def run_sql(sql, fetch=False):
    """经 mysql CLI 执行 SQL；fetch=True 返回 stdout（utf-8，容忍告警噪声），否则返回 None。"""
    cmd = mysql_args() + ["-e", sql]
    p = subprocess.run(cmd, capture_output=True, text=True,
                       encoding="utf-8", errors="ignore")
    return p.stdout if fetch else None
