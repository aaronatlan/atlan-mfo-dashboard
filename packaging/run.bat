@echo off
rem Lanceur alternatif pour le paquet portable Windows (app-image, sans installeur).
rem Invoque directement runtime\bin\javaw.exe (binaire signe par Eclipse Adoptium) au
rem lieu de l'executable natif genere par jpackage (non signe par un certificat payant).
rem Utile si un antivirus d'entreprise ou une politique de controle d'application
rem (AppLocker, EDR) bloque l'executable natif sans bloquer le runtime Java officiel.
rem Voir DEPLOYMENT.md.
for %%f in ("%~dp0app\atlan-mfo-dashboard-*.jar") do "%~dp0runtime\bin\javaw.exe" -Dfile.encoding=UTF-8 -jar "%%f"
