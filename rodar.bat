@echo off

REM Caminho para o projeto SIRATECH
set SIRATECH=%USERPROFILE%\Desktop\Windows\Programas\siratech

REM Caminho absoluto para o banco de dados do SIRATECH
set DB_PATH=%SIRATECH%\siratech.db

REM Copia o hibernate.cfg.xml para a pasta out
xcopy /y resources\hibernate.cfg.xml out\

REM Compila o simulador
javac -cp ".;lib/*;%SIRATECH%\out;%SIRATECH%\lib\*" -d out backend\*.java frontend\*.java

REM Roda — passa o caminho do banco como propriedade do sistema
java -DDB_PATH="%DB_PATH%" -cp ".;out;%SIRATECH%\lib\*;%SIRATECH%\out" frontend.SimuladorFrame

pause
