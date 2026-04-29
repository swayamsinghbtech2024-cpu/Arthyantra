@echo off
REM Use JetBrains JBR Java 21 since system default is JRE 8
set JAVA_HOME=C:\Program Files\JetBrains\PyCharm Community Edition 2024.3.2\jbr
set JAVAC="%JAVA_HOME%\bin\javac.exe"
set JAVA="%JAVA_HOME%\bin\java.exe"
set JAVAFX_LIB=.\javafx-sdk-21.0.5\lib

echo Compiling Backend and JavaFX App...
%JAVAC% --module-path "%JAVAFX_LIB%" --add-modules javafx.controls,javafx.fxml -cp "..\backend" MainApp.java DashboardView.java StrategiesView.java BacktestView.java RiskView.java ..\backend\*.java

if %ERRORLEVEL% equ 0 (
    echo Running JavaFX App...
    %JAVA% --module-path "%JAVAFX_LIB%" --add-modules javafx.controls,javafx.fxml -cp ".;..\backend" MainApp
) else (
    echo Compilation failed.
)
pause
