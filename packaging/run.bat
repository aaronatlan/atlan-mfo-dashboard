@echo off
rem Lanceur alternatif pour le paquet portable Windows (app-image, sans installeur).
rem Invoque directement runtime\bin\java.exe (binaire signe par Eclipse Adoptium) au
rem lieu de l'executable natif genere par jpackage (non signe par un certificat payant).
rem Utile si un antivirus d'entreprise ou une politique de controle d'application
rem (AppLocker, EDR) bloque l'executable natif sans bloquer le runtime Java officiel.
rem
rem java.exe (pas javaw.exe) : garde une console attachee pour voir toute erreur de
rem demarrage (ex. configuration manquante) au lieu d'une fenetre qui s'ouvre et se
rem referme sans rien afficher. La pause finale laisse le temps de lire un message
rem d'erreur avant fermeture. Voir DEPLOYMENT.md.
setlocal
for %%f in ("%~dp0app\atlan-mfo-dashboard-*.jar") do (
  "%~dp0runtime\bin\java.exe" -Dfile.encoding=UTF-8 -jar "%%f"
)
echo.
echo ----------------------------------------------------------------
echo Si l'application ne s'est pas ouverte, le message d'erreur ci-dessus
echo explique pourquoi. Fermeture automatique dans 30 secondes.
echo ----------------------------------------------------------------
timeout /t 30
