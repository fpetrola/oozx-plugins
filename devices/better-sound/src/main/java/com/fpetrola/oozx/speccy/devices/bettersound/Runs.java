package com.fpetrola.oozx.speccy.devices.bettersound;

import java.util.function.Consumer;

/**
 * The speaker's edges as runs: stretches where the period holds, or changes at a steady rate. The first step of the
 * ear (PLAN-SONIDO.md, S2), and the only one that sees edges.
 *
 * <p>The edges of the whole speaker are one stream, whoever made them: a beeper plays one thing at a time, and a
 * routine with two OUTs (one for each level, as Manic Miner's tune has) would otherwise be two sounds at half the
 * pitch. A half period goes on a run when it follows the run's half periods at their step (a plain tone, or a glide
 * whose half period grows by a fixed step: JSW's item and arrow), or when it and the half before make up the run's
 * whole period (a pulse whose width changes and whose period does not: the width is timbre, not pitch). Where the
 * width changes, the two halves that straddle it do not add up to a period; the first of them is held until the next
 * one says whether a pulse closed. What fits none of that ends the run at the edge where it stopped fitting.
 *
 * <p>A gap longer than {@link #LONGEST_HALF} is silence, not a half period: a tune that plays a few half periods each
 * frame (JSW's in-game tune) is a run a frame. Runs of fewer than three half periods are reported too, as what is not
 * a tone ({@link Run#tone()}): clicks and the debris between two sounds.
 */
public final class Runs implements Ear.Input {
  /** the longest half period that is still a tone: 109 Hz; lower than any tune the measured games play (S0) */
  public static final int LONGEST_HALF = 16_000;
  /** how far a period may be from where it was going, relative to it, and at least {@link #SLACK} T-states */
  private static final double TOLERANCE = 0.025;
  private static final int SLACK = 4;
  private static final int HALVES = 1, PAIRS = 2;

  /**
   * A run: from the edge at {@code start} to the one at {@code end}, {@code halves} half periods, its first and last
   * whole period in T-states, and the emitter of the edges that make it.
   */
  public record Run(int key, long start, long end, int halves, double firstPeriod, double lastPeriod) {
    /** three half periods or more: a pitch, not a click */
    public boolean tone() {
      return halves >= 3;
    }

    /** its mean whole period */
    public double period() {
      return halves == 0 ? 0 : 2.0 * (end - start) / halves;
    }

    /** the period held: it ended within 4 % of where it started */
    public boolean steady() {
      return tone() && Math.abs(lastPeriod - firstPeriod) <= 0.04 * period();
    }
  }

  /** two emitters that played one run: the two OUTs of one routine */
  public interface Joined {
    void joined(int key, int other);
  }

  private final Consumer<Run> out;
  private final Joined joined;
  /** the last edge seen, and the last one in the run (they differ while a half is held) */
  private long last = Long.MIN_VALUE, runEnd;
  private int lastKey;
  // the run being built
  private long start;
  private int key, halves;
  private int hFirst, h1, h2;       // its first half period and the last two
  private double period;            // its whole period, as the mean of the pairs that made one
  private int pairs;
  private double firstPeriod, lastPeriod;
  /** the run went on by its pairs once: a pulse, judged by them from then on */
  private boolean pulse;
  /** a half period held where a pulse's width changed, and its edge's emitter; -1 when none */
  private int held = -1, heldKey;

  /** what is silence: a gap longer than {@link #LONGEST_HALF}, or than {@code longest} for a voice never silent */
  private final int longest;

  public Runs(Consumer<Run> out, int longest) {
    this(out, (a, b) -> {
    }, longest);
  }

  public Runs(Consumer<Run> out, Joined joined) {
    this(out, joined, LONGEST_HALF);
  }

  private Runs(Consumer<Run> out, Joined joined, int longest) {
    this.out = out;
    this.joined = joined;
    this.longest = longest;
  }

  @Override
  public void edge(long t, int k) {
    if (last == Long.MIN_VALUE || t - last > longest) {
      close();
      begin(t, k);
      last = t;
      return;
    }
    int h = (int) (t - last);
    if (held >= 0) {
      int x = held;
      held = -1;
      if (near(x + h, period)) {
        add(x, heldKey);
        add(h, k);
        pulse = true;
        last = t;
        return;
      }
      // no pulse closed: the run ended before the held half, which starts what comes next
      long at = runEnd;
      int atKey = lastKey;
      close();
      begin(at, atKey);
      add(x, heldKey);
    }
    int by = fits(h);
    if (by == 0) {
      if (halves == 2) {
        // the first half was the gap before the sound, or the debris of the sound before: the run starts after it
        start += hFirst;
        halves = 1;
        hFirst = h1 = h2;
        pairs = 0;
        pulse = false;
      }
      if (halves >= 3 && pairs > 0 && near(h1 + h2, period)) {
        held = h;
        heldKey = k;
        last = t;
        return;
      }
      by = fits(h);
      if (by == 0) {
        long at = runEnd;
        int atKey = lastKey;
        close();
        begin(at, atKey);
      }
    }
    if (by == PAIRS)
      pulse = true;
    add(h, k);
    last = t;
  }

  @Override
  public void frame(long end) {
    if (last != Long.MIN_VALUE && end - last > longest)
      close();
  }

  /** the emitter of the run being played, or -1 if the speaker is quiet */
  public int sounding() {
    return last == Long.MIN_VALUE ? -1 : key;
  }

  /** where the run being played started */
  public long openStart() {
    return start;
  }

  /** the mean whole period of the run being played */
  public double openPeriod() {
    return halves == 0 ? 0 : 2.0 * (runEnd - start) / halves;
  }

  /** how many half periods the run being played has so far, if it is a tone; 0 if not */
  public int openHalves() {
    return last == Long.MIN_VALUE || halves < 3 ? 0 : halves;
  }

  /** whatever is still open */
  public void flush() {
    close();
  }

  private void begin(long t, int k) {
    pulse = false;
    start = t;
    runEnd = t;
    key = k;
    lastKey = k;
    halves = 0;
    pairs = 0;
    held = -1;
  }

  /** how a half period goes on the run: by its half periods ({@link #HALVES}), by its pairs ({@link #PAIRS}), or not (0) */
  private int fits(int h) {
    if (halves == 0)
      return HALVES;
    if (halves == 1)
      return h * 4 >= h2 ? HALVES : 0; // a pulse narrower than a quarter is two sounds, not one
    double step = halves > 2 ? (double) (h2 - hFirst) / (halves - 1) : h2 - h1;
    // a run that went on by its pairs is a pulse: its half periods are its widths, which say nothing of what follows.
    // Without this, a note the interrupt cut (JSW: 3355, 9180, 3355, 3355...) went on as a pulse and then by its half
    // periods, and was a glide from the pair the interrupt made
    if (!pulse && near(h, h2 + step))
      return HALVES;
    return pairs > 0 && near(h + h2, period) ? PAIRS : 0;
  }

  private static boolean near(double x, double expected) {
    return Math.abs(x - expected) <= Math.max(SLACK, TOLERANCE * expected);
  }

  private void add(int h, int k) {
    if (halves == 0) {
      hFirst = h;
      key = k; // the run's first edge may be the last of the sound before: its emitter is the one that goes on
    }
    halves++;
    h1 = h2;
    h2 = h;
    runEnd += h;
    if (halves >= 2) {
      int p = h1 + h2;
      if (pairs == 0 || near(p, period)) {
        period = (period * pairs + p) / (pairs + 1);
        pairs++;
      } else if (near(h2, h1 + (halves > 2 ? (double) (h1 - hFirst) / (halves - 2) : 0)))
        period = p; // a glide: the period follows it
      if (halves == 2)
        firstPeriod = p;
      lastPeriod = p;
    }
    if (k != lastKey) {
      if (halves > 1) // inside the run, not at its first edge
        joined.joined(lastKey, k);
      lastKey = k;
    }
  }

  private void close() {
    if (last == Long.MIN_VALUE)
      return;
    if (held >= 0) { // a held half nothing closed: it was the end of the sound
      held = -1;
    }
    double f = halves >= 2 ? firstPeriod : 2.0 * h2, l = halves >= 2 ? lastPeriod : 2.0 * h2;
    // a pulse is a tone once it has two whole periods: one pair is only two half periods that happen to add up; a
    // narrow one, four (Ping Pong's notes have a dozen, Trantor's hum two or three, and it is a hum of clicks)
    boolean narrow = pulse && Math.min(h1, h2) * 4 < Math.max(h1, h2);
    int n = pulse && halves < (narrow ? 8 : 4) ? Math.min(halves, 2) : halves;
    // and its whole periods only: ended on a pulse's fall, its last width is not a half period (Ping Pong's are 1 %)
    boolean width = pulse && n % 2 == 1;
    out.accept(new Run(key, start, width ? runEnd - h2 : runEnd, width ? n - 1 : n, halves == 0 ? 0 : f,
        halves == 0 ? 0 : l));
    halves = 0;
    pairs = 0;
    last = Long.MIN_VALUE;
  }
}
