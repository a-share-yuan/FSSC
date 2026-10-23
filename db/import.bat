@echo off
rem ==========================================================================
rem  FSSC demo database one-key import (Windows)
rem  Imports db\demo-db\*.sql (323 per-table files) into local MySQL:
rem    - fssc        main db: 316 business/platform tables + Flowable ACT_*
rem                   (315 deployed process definitions included)
rem    - fssc_image  image db: 6 tables (img_* / image_* / ocr_record)
rem  Each file is self-contained (CREATE DATABASE + USE + DROP/CREATE/INSERT),
rem  so the import is idempotent and needs no pre-created databases.
rem  fssc_routines.sql (views) is imported LAST on purpose - it references
rem  business tables and would fail if run first.
rem
rem  Usage:  db\import.bat
rem  Env:    MYSQL_PWD   MySQL password (prompted if not set)
rem ==========================================================================
setlocal enabledelayedexpansion

set "HOST=127.0.0.1"
set "PORT=3306"
set "USER_NAME=root"
set "DIR=%~dp0demo-db"

if not exist "%DIR%" (
  echo ERROR: dump dir not found: %DIR%
  exit /b 1
)
where mysql >nul 2>nul
if errorlevel 1 (
  echo ERROR: mysql client not found in PATH.
  echo        Install MySQL 8.x and add its bin directory to PATH.
  exit /b 1
)
if "%MYSQL_PWD%"=="" set /p "MYSQL_PWD=MySQL password for %USER_NAME%: "

echo Importing into %HOST%:%PORT% as %USER_NAME% ^(tables first, views last^) ...

(for %%F in ("%DIR%\*.sql") do if /I not "%%~nxF"=="fssc_routines.sql" type %%F) | mysql -h %HOST% -P %PORT% -u %USER_NAME% --default-character-set=utf8mb4 || goto :fail
mysql -h %HOST% -P %PORT% -u %USER_NAME% --default-character-set=utf8mb4 < "%DIR%\fssc_routines.sql" || goto :fail

echo Done. Verify with:
echo   mysql -h %HOST% -u %USER_NAME% -e "SELECT COUNT(*) FROM fssc.sys_user;"
exit /b 0

:fail
echo IMPORT FAILED - check MySQL connectivity, credentials and charset settings.
exit /b 1
