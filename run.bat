@echo off
chcp 65001 >nul
cd /d %~dp0
if not exist bin mkdir bin

rem 优先使用 E:\jdk21，否则用 PATH 中的 javac
set JAVAC=javac
set JAVA=java
if exist "E:\jdk21\bin\javac.exe" (
    set JAVAC=E:\jdk21\bin\javac.exe
    set JAVA=E:\jdk21\bin\java.exe
)

echo 正在编译...
%JAVAC% -encoding UTF-8 -d bin src\*.java src\com\doudizhu\Main.java src\com\doudizhu\engine\*.java src\com\doudizhu\model\*.java src\com\doudizhu\net\*.java src\com\doudizhu\ui\*.java
if errorlevel 1 (
    echo 编译失败！请检查是否已安装 JDK 并配置好 javac。
    pause
    exit /b 1
)
echo 编译成功，启动游戏...
%JAVA% -cp bin Main
pause