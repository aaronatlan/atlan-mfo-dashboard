@echo off
rem Lanceur de l'outil d'import ponctuel du pipeline (Excel -> base de donnees).
rem Reutilise le runtime Java deja embarque par l'installation existante de
rem Patrimium MFO Dashboard sur ce poste (pas besoin d'installer Java a part).
rem
rem Usage :
rem   import.bat <fichier.xlsx> <identifiant_utilisateur> [dossier_installation_app]
rem
rem Le 3e argument est optionnel : s'il est omis, ce script cherche automatiquement
rem l'installation dans les emplacements habituels (Program Files, Bureau). S'il ne
rem la trouve pas, indiquez son chemin vous-meme en 3e argument, par exemple :
rem   import.bat fichier.xlsx admin "C:\Program Files\Patrimium MFO Dashboard"
rem
rem La configuration base (config.properties) est celle deja utilisee par
rem l'application installee (%%USERPROFILE%%\.atlan-mfo\config.properties) -
rem rien a reconfigurer.

set "HERE=%~dp0"
set "XLSX=%~1"
set "USERNAME_ARG=%~2"
set "APPDIR=%~3"

if "%XLSX%"=="" (
  echo ERREUR : fichier Excel manquant.
  echo Usage : import.bat "chemin\vers\fichier.xlsx" identifiant_utilisateur
  goto :end
)
if "%USERNAME_ARG%"=="" (
  echo ERREUR : identifiant utilisateur manquant.
  echo Usage : import.bat "chemin\vers\fichier.xlsx" identifiant_utilisateur
  goto :end
)

if not "%APPDIR%"=="" (
  set "JAVAEXE=%APPDIR%\runtime\bin\java.exe"
  goto :checkjava
)

rem Emplacements habituels : installeur .msi (Program Files) ou paquet portable
rem (Bureau) - voir DEPLOYMENT.md §5 et le paquet portable de secours (run.bat).
set "CANDIDATE1=%ProgramFiles%\Patrimium MFO Dashboard\runtime\bin\java.exe"
set "CANDIDATE2=%ProgramFiles(x86)%\Patrimium MFO Dashboard\runtime\bin\java.exe"
set "CANDIDATE3=%USERPROFILE%\Desktop\Patrimium MFO Dashboard\runtime\bin\java.exe"

if exist "%CANDIDATE1%" set "JAVAEXE=%CANDIDATE1%"
if not defined JAVAEXE if exist "%CANDIDATE2%" set "JAVAEXE=%CANDIDATE2%"
if not defined JAVAEXE if exist "%CANDIDATE3%" set "JAVAEXE=%CANDIDATE3%"

:checkjava
if not defined JAVAEXE (
  echo ERREUR : impossible de trouver le runtime Java de Patrimium MFO Dashboard.
  echo Emplacements essayes :
  echo   %CANDIDATE1%
  echo   %CANDIDATE2%
  echo   %CANDIDATE3%
  echo Si l'application est installee ailleurs, relancez en indiquant son dossier
  echo d'installation en 3e argument, par exemple :
  echo   import.bat "%XLSX%" %USERNAME_ARG% "C:\chemin\vers\Patrimium MFO Dashboard"
  goto :end
)
if not exist "%JAVAEXE%" (
  echo ERREUR : "%JAVAEXE%" est introuvable.
  goto :end
)

echo Runtime Java : %JAVAEXE%
echo Import de "%XLSX%" pour l'utilisateur %USERNAME_ARG% ...
"%JAVAEXE%" -Dfile.encoding=UTF-8 -jar "%HERE%pipeline-import-tool.jar" "%XLSX%" "%USERNAME_ARG%"

:end
echo.
echo ----------------------------------------------------------------
echo Fermeture automatique dans 30 secondes.
echo ----------------------------------------------------------------
timeout /t 30
