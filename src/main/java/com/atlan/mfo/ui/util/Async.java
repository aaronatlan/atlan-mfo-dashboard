package com.atlan.mfo.ui.util;

import javafx.application.Platform;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Exécute les accès base (potentiellement lents : latence réseau vers la base
 * distante, hachage BCrypt) hors du <i>JavaFX Application Thread</i>, pour ne pas
 * figer l'interface. Le résultat — ou l'erreur — est ensuite délivré sur le thread
 * UI via {@link Platform#runLater}.
 *
 * <p>Un unique thread de fond (démon) sérialise les opérations : suffisant pour un
 * poste mono-utilisateur, et évite toute course sur l'état partagé des contrôleurs.
 */
public final class Async {

    private static final ExecutorService EXEC = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "atlan-db");
        t.setDaemon(true);
        return t;
    });

    private Async() {
    }

    /** Tâche renvoyant une valeur ; erreur → dialogue d'erreur standard. */
    public static <T> void run(Supplier<T> work, Consumer<T> onSuccess) {
        run(work, onSuccess, ErrorDialog::show);
    }

    /** Tâche renvoyant une valeur, avec gestion d'erreur personnalisée. */
    public static <T> void run(Supplier<T> work, Consumer<T> onSuccess, Consumer<Throwable> onError) {
        EXEC.execute(() -> {
            try {
                T result = work.get();
                Platform.runLater(() -> onSuccess.accept(result));
            } catch (Throwable ex) {
                Platform.runLater(() -> onError.accept(ex));
            }
        });
    }

    /** Tâche sans valeur de retour (écriture), avec gestion d'erreur personnalisée. */
    public static void run(Runnable work, Runnable onSuccess, Consumer<Throwable> onError) {
        run(() -> {
            work.run();
            return null;
        }, v -> onSuccess.run(), onError);
    }

    /** Arrête le thread de fond (fermeture de l'application). */
    public static void shutdown() {
        EXEC.shutdownNow();
    }
}
