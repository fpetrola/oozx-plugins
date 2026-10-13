package com.fpetrola.proto.soccer;

/** Qué tan bien juega la máquina. Sólo cambia a su equipo: el arquero del humano ataja siempre igual. */
enum Difficulty {
    EASY("Fácil", 0.78f, 0.75f, 15f, 4.5f, 15f, 0.2f, -0.25f, 0.25f, 0.8f, 2f, 14f, 1, 0.25f, 0.45f, 0.85f, 2f),
    NORMAL("Normal", 0.95f, 0.35f, 22f, 1.2f, 23f, 1.2f, 0.3f, 0.12f, 1f, 1f, 3f, 1, 0.15f, 0.3f, 0.55f, 9f),
    HARD("Difícil", 1.03f, 0.22f, 26f, 0.6f, 26f, 1.8f, 0.45f, 0.06f, 1.08f, 0.6f, 0f, 2, 0.08f, 0.18f, 0.2f, 11f);

    final String label;
    /** Velocidad de sus jugadores, por la de los del humano. */
    final float speed;
    /** Cada cuánto vuelve a pensar el que lleva la pelota. */
    final float think;
    /** Desde qué distancia tira al arco, cuánto le puede errar (metros afuera del palo) y con qué fuerza sale. */
    final float shootFrom, shotError, shotSpeed;
    /** Ganas de barrer: por segundo, cerca del que lleva la pelota. */
    final float slides;
    /** Qué tan cerca tiene que llegar a la pelota para sacársela al que la lleva (más es más fácil para él). */
    final float steal;
    /** Su arquero: cuánto tarda en reaccionar, cuánto ataja (factor) y cuánto se equivoca al leer el tiro (factor). */
    final float keeperReaction, keeperSaves, keeperMisread;
    /** Cuánto se equivoca al pasar, en grados. */
    final float passError;
    /** Cuántos salen a apretar al que lleva la pelota. */
    final int pressers;
    /**
     * Defendiendo de cerca: cuánto tarda en darse cuenta de lo que hace el pie del rival (segundos),
     * cuánto se queda jugado a un lado sin volver a mirar, y qué tan seguido se juega cuando ve que
     * la pelota va para un costado (de 0 a 1): lo que lo hace caer en un amague.
     */
    final float reaction, commit, bite;
    /** Con la pelota en su equipo, cuántos metros se van adelante sus delanteros (los volantes, menos de la mitad). */
    final float runs;

    Difficulty(String label, float speed, float think, float shootFrom, float shotError, float shotSpeed, float slides, float steal,
               float keeperReaction, float keeperSaves, float keeperMisread, float passError, int pressers,
               float reaction, float commit, float bite, float runs) {
        this.label = label;
        this.speed = speed;
        this.think = think;
        this.shootFrom = shootFrom;
        this.shotError = shotError;
        this.shotSpeed = shotSpeed;
        this.slides = slides;
        this.steal = steal;
        this.keeperReaction = keeperReaction;
        this.keeperSaves = keeperSaves;
        this.keeperMisread = keeperMisread;
        this.passError = passError;
        this.pressers = pressers;
        this.reaction = reaction;
        this.commit = commit;
        this.bite = bite;
        this.runs = runs;
    }
}
