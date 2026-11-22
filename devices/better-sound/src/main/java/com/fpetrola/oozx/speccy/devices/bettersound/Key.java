package com.fpetrola.oozx.speccy.devices.bettersound;

import java.util.Optional;

/**
 * The key of a tune, by Krumhansl and Schmuckler: how long each of the twelve notes sounds, correlated with the
 * Krumhansl-Kessler profile of each of the 24 keys (PLAN-SONIDO.md, D4). A tune whose best correlation is weak --
 * chromatic runs, diminished arpeggios -- has none, and a harmony is not built on it.
 */
public record Key(int tonic, boolean minor) {
  private static final double[] MAJOR = {6.35, 2.23, 3.48, 2.33, 4.38, 4.09, 2.52, 5.19, 2.39, 3.66, 2.29, 2.88};
  private static final double[] MINOR = {6.33, 2.68, 3.52, 5.38, 2.60, 3.53, 2.54, 4.75, 3.98, 2.69, 3.34, 3.17};
  /**
   * the weakest correlation taken for a key, measured on the music of JSW, Manic Miner and Dynamite Dan: the keyed
   * tunes fit at 0.92 (JSW's theme, E minor), 0.82 (JSW's tune) and 0.70 (Dan's toccata, D minor); the keyless at 0.64
   * (a tritone repeated), 0.61 (Manic Miner's tune, its fifth held longest, taken for the tonic), 0.56, 0.35 and 0.28
   * (chromatic runs, diminished arpeggios). The margin is 0.06 either side: a refusal costs only the harmony
   */
  static final double MIN_FIT = 0.65;
  private static final String[] NAMES = {"C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B"};

  /** the key of {@code time}, the time each pitch class sounds (C first), if it has one */
  public static Optional<Key> of(double[] time) {
    Key best = null;
    double top = Double.NEGATIVE_INFINITY;
    for (Key k : all()) {
      double r = k.fit(time);
      if (r > top) {
        top = r;
        best = k;
      }
    }
    return top >= MIN_FIT ? Optional.of(best) : Optional.empty();
  }

  /** the best correlation of {@code time} with any key */
  static double best(double[] time) {
    double top = Double.NEGATIVE_INFINITY;
    for (Key k : all())
      top = Math.max(top, k.fit(time));
    return top;
  }

  /** the correlation of {@code time} with this key's profile; 0 where either does not vary */
  double fit(double[] time) {
    double[] p = minor ? MINOR : MAJOR, q = new double[12];
    for (int i = 0; i < 12; i++)
      q[i] = p[Math.floorMod(i - tonic, 12)];
    return correlation(time, q);
  }

  private static Key[] all() {
    Key[] keys = new Key[24];
    for (int i = 0; i < 24; i++)
      keys[i] = new Key(i % 12, i >= 12);
    return keys;
  }

  private static double correlation(double[] x, double[] y) {
    double mx = mean(x), my = mean(y), sxy = 0, sxx = 0, syy = 0;
    for (int i = 0; i < 12; i++) {
      sxy += (x[i] - mx) * (y[i] - my);
      sxx += (x[i] - mx) * (x[i] - mx);
      syy += (y[i] - my) * (y[i] - my);
    }
    return sxx == 0 || syy == 0 ? 0 : sxy / Math.sqrt(sxx * syy);
  }

  private static double mean(double[] x) {
    double s = 0;
    for (double v : x)
      s += v;
    return s / x.length;
  }

  @Override
  public String toString() {
    return NAMES[tonic] + (minor ? " minor" : " major");
  }
}
