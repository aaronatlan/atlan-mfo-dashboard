package com.atlan.mfo;

/**
 * Point d'entrée pour le packaging (jpackage / jar exécutable).
 *
 * <p>Une classe distincte de {@link Main} (qui étend {@code Application}) est
 * nécessaire : lancée depuis le classpath, une sous-classe d'{@code Application}
 * comme classe principale déclenche le contrôle « JavaFX runtime components are
 * missing ». Passer par ce lanceur neutre contourne ce contrôle.
 */
public final class Launcher {

    private Launcher() {
    }

    public static void main(String[] args) {
        Main.main(args);
    }
}
