@echo off
chcp 65001 >nul
set "SRC=%~dp0"
set "DST=%APPDATA%\Blackmagic Design\DaVinci Resolve\Support\Fusion\Templates\Edit\Effects\Iktishafat"
set "HOME_DIR=%USERPROFILE%\Videos\Iktishafat_Vorlage\Kursi_Studio"
if not exist "%DST%" mkdir "%DST%"
if not exist "%HOME_DIR%\SFX" mkdir "%HOME_DIR%\SFX"
if exist "%DST%\Iktishafat_Kursi_Studio.setting" copy /Y "%DST%\Iktishafat_Kursi_Studio.setting" "%DST%\Iktishafat_Kursi_Studio_backup.txt" >nul
copy /Y "%SRC%Iktishafat_Kursi_Studio.setting" "%DST%\" >nul
copy /Y "%SRC%Iktishafat_Kursi_Studio.png" "%DST%\" >nul
copy /Y "%SRC%SFX\*.*" "%HOME_DIR%\SFX\" >nul
copy /Y "%SRC%*.md" "%HOME_DIR%\" >nul
copy /Y "%SRC%*.csv" "%HOME_DIR%\" >nul
copy /Y "%SRC%Kursi_Studio_Vorschau.png" "%HOME_DIR%\" >nul
copy /Y "%SRC%Kursi_Studio.ico" "%HOME_DIR%\" >nul
powershell -NoProfile -Command "$s=(New-Object -ComObject WScript.Shell).CreateShortcut([Environment]::GetFolderPath('Desktop')+'\Iktishafat Kursi Studio.lnk'); $s.TargetPath='%HOME_DIR%'; $s.IconLocation='%HOME_DIR%\Kursi_Studio.ico'; $s.Description='Iktishafat Kursi Studio - DaVinci Vorlage'; $s.Save()"
echo.
echo Fertig: Iktishafat Kursi Studio installiert (DaVinci + Desktop-Verknuepfung).
echo DaVinci neu starten: Schnitt - Effekte - Effekte - Iktishafat
pause
