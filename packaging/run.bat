@echo off
rem Lanceur alternatif pour le paquet portable Windows (app-image, sans installeur).
rem Invoque directement runtime\bin\java.exe (binaire signe par Eclipse Adoptium) au
rem lieu de l'executable natif genere par jpackage (non signe par un certificat payant).
rem Utile si un antivirus d'entreprise ou une politique de controle d'application
rem (AppLocker, EDR) bloque l'executable natif sans bloquer le runtime Java officiel.
rem
rem java.exe (pas javaw.exe) : garde une console attachee pour voir toute erreur de
rem demarrage au lieu d'une fenetre qui s'ouvre et se referme sans rien afficher.
rem Verifie explicitement app\ et runtime\ avant de lancer : ce fichier doit rester
rem A L'INTERIEUR du dossier decompresse, a cote de ces deux dossiers - le deplacer
rem seul (ex. sur le Bureau) fait echouer le lancement silencieusement sinon.
rem Voir DEPLOYMENT.md.

set "APPDIR=%~dp0app"
set "JAVAEXE=%~dp0runtime\bin\java.exe"

if not exist "%JAVAEXE%" (
  echo ERREUR : "%JAVAEXE%" est introuvable.
  echo Ce fichier run.bat doit rester A L'INTERIEUR du dossier decompresse,
  echo juste a cote des dossiers "app" et "runtime" - ne le deplacez pas seul.
  goto :end
)

set "JARFILE="
for %%f in ("%APPDIR%\atlan-mfo-dashboard-*.jar") do set "JARFILE=%%f"

if not defined JARFILE (
  echo ERREUR : aucun fichier atlan-mfo-dashboard-*.jar trouve dans "%APPDIR%".
  echo Ce fichier run.bat doit rester A L'INTERIEUR du dossier decompresse,
  echo juste a cote des dossiers "app" et "runtime" - ne le deplacez pas seul.
  goto :end
)

echo Lancement de %JARFILE% ...
"%JAVAEXE%" -Dfile.encoding=UTF-8 -jar "%JARFILE%"

:end
echo.
echo ----------------------------------------------------------------
echo Si l'application ne s'est pas ouverte, le message d'erreur ci-dessus
echo explique pourquoi. Fermeture automatique dans 30 secondes.
echo ----------------------------------------------------------------
timeout /t 30
