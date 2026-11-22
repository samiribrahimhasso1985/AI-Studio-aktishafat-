@echo off
REM Creates the release signing key for IKTISHAFAT Teleprompter (Windows).
REM Needs keytool from Android Studio's JDK, e.g.:
REM   set PATH="C:\Program Files\Android\Android Studio\jbr\bin";%PATH%
REM Keep release.jks and the passwords safe: updates must be signed with the same key.
cd /d "%~dp0\.."
keytool -genkeypair -v -keystore release.jks -alias iktishafat -keyalg RSA -keysize 4096 -validity 10000
echo.
echo Done. Now copy keystore.properties.example to keystore.properties and enter your passwords.
echo For GitHub Actions, convert the key to text with:
echo   certutil -encode release.jks release.b64
echo and paste the content (without the BEGIN/END lines) into the secret IKT_KEYSTORE_BASE64.
pause
