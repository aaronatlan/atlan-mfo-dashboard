package com.atlan.mfo.model;

/**
 * Un composant du score (DPI, IRR, Géographie, Timeline, …).
 *
 * <p>Si la métrique n'est pas communiquée, le composant est <b>exclu</b> : il ne
 * compte ni dans {@code Earned} ni dans {@code Possible} (voir §5.1).
 */
public record ScoreComponent(String label, double maxPoints, boolean communicated, double subScore) {

    public static ScoreComponent scored(String label, double maxPoints, double subScore) {
        return new ScoreComponent(label, maxPoints, true, subScore);
    }

    public static ScoreComponent excluded(String label, double maxPoints) {
        return new ScoreComponent(label, maxPoints, false, 0d);
    }
}
