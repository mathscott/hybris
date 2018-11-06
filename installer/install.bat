@echo off
SET INSTALLER_WORKING_DIR=%~dp0
javac %INSTALLER_WORKING_DIR:~0,-1%\JavaVersionChecker.java
java -classpath "%INSTALLER_WORKING_DIR:~0,-1%" JavaVersionChecker
IF errorlevel 0 (
java -classpath ";%INSTALLER_WORKING_DIR:~0,-1%/libs/commons-cli-1.2.jar;%INSTALLER_WORKING_DIR:~0,-1%/libs/commons-lang-2.6.jar;%INSTALLER_WORKING_DIR:~0,-1%/libs/groovy-all-2.4.10.jar;%INSTALLER_WORKING_DIR:~0,-1%/libs/installer-6.7.0.3.jar" de.hybris.installer.CmdHandler %*
)

