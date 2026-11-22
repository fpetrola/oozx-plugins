package com.fpetrola.oozx.speccy.devices.bettersound;

/**
 * The tuner (PLAN-SONIDO.md, S2): the one shift in cents that brings a game's notes closest to the twelve
 * semitones of A 440. It takes in what neither the ear nor the instrument can know: the clock of the replay, which
 * charges no contention (S0: 0.84-1.00 of the real machine's, by game), and the constant of the game's own routine.
 * The intervals do not need it; a MIDI note does.
 */
public final class Tuning {
  private static final double CLOCK = 3_500_000;
  /** how much of the notes' time falls at each cent of the semitone */
  private final double[] at = new double[100];
  private double total;
  private int shift;
  private boolean dirty;

  /** a note of {@code period} T-states held for {@code length} T-states */
  public void note(double period, long length) {
    double c = centsFromA(period);
    at[(int) Math.floorMod(Math.round(c), 100L)] += length;
    total += length;
    dirty = true;
  }

  /** the shift, in cents, from -49 to 50: the notes sound this much off the grid */
  public int shift() {
    if (dirty) {
      double best = Double.MAX_VALUE;
      for (int s = 0; s < 100; s++) {
        double cost = 0;
        for (int b = 0; b < 100; b++)
          cost += at[b] * Math.abs(Math.floorMod(b - s + 50, 100) - 50);
        if (cost < best) {
          best = cost;
          shift = s > 50 ? s - 100 : s;
        }
      }
      dirty = false;
    }
    return shift;
  }

  /** how far the notes stay from the grid once shifted, in cents: the median, by time */
  public double residue() {
    if (total == 0)
      return 0;
    int s = shift();
    double[] by = new double[51];
    for (int b = 0; b < 100; b++)
      by[Math.abs(Math.floorMod(b - s + 50, 100) - 50)] += at[b];
    double half = total / 2, sum = 0;
    for (int d = 0; d <= 50; d++)
      if ((sum += by[d]) >= half)
        return d;
    return 50;
  }

  /** the MIDI note of a whole period, once shifted */
  public int midi(double period) {
    return (int) Math.round(69 + (centsFromA(period) - shift()) / 100);
  }

  /** how far a whole period is from its MIDI note once shifted, in cents */
  public double cents(double period) {
    double c = centsFromA(period) - shift();
    return c - 100 * Math.round(c / 100);
  }

  static double centsFromA(double period) {
    return 1200 * Math.log(CLOCK / period / 440) / Math.log(2);
  }
}
