@echo off
chcp 65001 >nul
cd /d %~dp0

rem 优先使用 E:\jdk21，否则用 PATH 中的 java
set JAVAC=javac
set JAVAW=javaw
if exist "E:\jdk21\bin\javac.exe" (
    set JAVAC=E:\jdk21\bin\javac.exe
    set JAVAW=E:\jdk21\bin\javaw.exe
)

rem 若没有编译产物则先编译一次
if not exist "bin\Main.class" (
    echo 首次运行，正在编译...
    %JAVAC% -encoding UTF-8 -d bin src\*.java src\com\doudizhu\Main.java src\com\doudizhu\engine\*.java src\com\doudizhu\model\*.java src\com\doudizhu\net\*.java src\com\doudizhu\ui\*.java
    if errorlevel 1 (
        echo 编译失败！
        pause
        exit /b 1
    )
)

rem javaw 启动图形界面，不残留命令行窗口
start "" %JAVAW% -cp bin Main
exit