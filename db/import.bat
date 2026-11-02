@echo off
rem SPDX-License-Identifier: AGPL-3.0-or-later
rem Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)
rem
rem Import the full database dump under db\Dump<date>\ (one file per table).
rem
rem Usage:
rem   db\import.bat
rem   db\import.bat -h 10.0.0.5 -P 3307 -u root
rem   set MYSQL_PWD=yourpassword ^&^& db\import.bat
rem
rem Every .sql carries CREATE DATABASE IF NOT EXISTS + USE + FOREIGN_KEY_CHECKS=0
rem and DROP TABLE IF EXISTS + CREATE + INSERT, so no manual CREATE DATABASE is
rem needed, the order of files does not matter, and the script is repeatable.
rem Both databases (fssc and fssc_image) are created during the import.

setlocal enabledelayedexpansion

set "HOST=127.0.0.1"
set "PORT=3306"
set "DBUSER=root"
set "DUMPDIR="
set "ROOT=%~dp0.."

:parse
if "%~1"=="" goto :parsed
if /i "%~1"=="-h" goto :arg_h
if /i "%~1"=="-P" goto :arg_P
if /i "%~1"=="-u" goto :arg_u
if /i "%~1"=="-d" goto :arg_d
echo Unknown option: %~1
goto :usage

:arg_h
set "HOST=%~2"
shift
shift
goto :parse

:arg_P
set "PORT=%~2"
shift
shift
goto :parse

:arg_u
set "DBUSER=%~2"
shift
shift
goto :parse

:arg_d
set "DUMPDIR=%~2"
shift
shift
goto :parse

:parsed
if defined DUMPDIR goto :have_dump
for /f "delims=" %%d in ('dir /b /ad /o-n "%ROOT%\db\Dump*" 2^>nul') do (
  set "DUMPDIR=%ROOT%\db\%%d"
  goto :have_dump
)
goto :no_dump

:have_dump
if not exist "%DUMPDIR%" goto :no_dump

where mysql >nul 2>nul
if errorlevel 1 goto :no_mysql

set /a TOTAL=0
for %%f in ("%DUMPDIR%\*.sql") do set /a TOTAL+=1
if %TOTAL%==0 goto :no_sql

echo dump dir : %DUMPDIR%
echo target   : %DBUSER%@%HOST%:%PORT%
echo files    : %TOTAL%
echo.

if defined MYSQL_PWD goto :have_pwd
echo MYSQL_PWD is not set. The mysql client will ask for the password once.
echo To skip the prompt:  set MYSQL_PWD=yourpassword ^&^& db\import.bat
echo.

:have_pwd
mysql --default-character-set=utf8mb4 -h %HOST% -P %PORT% -u %DBUSER% -e "SELECT 1" >nul 2>nul
if errorlevel 1 goto :no_conn

set /a N=0
for %%f in ("%DUMPDIR%\*.sql") do (
  set /a N+=1
  set "CUR=%%~nxf"
  echo [!N!/%TOTAL%] !CUR!
  mysql --default-character-set=utf8mb4 -h %HOST% -P %PORT% -u %DBUSER% < "%%~ff"
  if errorlevel 1 goto :fail
)

echo.
echo Import finished: %TOTAL% files.
echo Next: create .env from .env.example, then start the backend (readme.txt step 2 and 3).
goto :end

:fail
echo.
echo Import failed at: %CUR%
echo Check: file truncated? MySQL 8 client? charset utf8mb4?
goto :end

:no_dump
echo Cannot find a dump directory. Expected db\Dump^<date^>\ in the repository root.
goto :end

:no_sql
echo No .sql file found under: %DUMPDIR%
goto :end

:no_mysql
echo The mysql client was not found in PATH. Install the MySQL 8 client first.
goto :end

:no_conn
echo Cannot connect to MySQL, or the account/password is wrong: %DBUSER%@%HOST%:%PORT%
echo Check: is MySQL running, is the port right, is MYSQL_PWD correct?
goto :end

:usage
echo Usage: db\import.bat [-h host] [-P port] [-u user] [-d dumpdir]
echo Example: db\import.bat -h 127.0.0.1 -P 3306 -u root

:end
endlocal
