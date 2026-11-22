package com.fpetrola.oozx.speccy.devices.bettersound;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * An emitter's runs as phrases, and each phrase as music or as an effect, by its shape alone (PLAN-SONIDO.md, S2).
 *
 * <p>A phrase is what one emitter played without a pause longer than {@link #GAP}. Measured in S0, the music and the
 * effects of a beeper game are made of the same thing -- a tone held for a frame, then another -- and what tells them
 * apart is where the pitch goes: a tune goes up and down among a few pitches and comes back to them; an effect goes
 * one way (a sweep), or one way and back (an arc), or is over in a moment (a blip). So a phrase is {@link
 * Shape#MUSIC} when its steady tones turn back often; its notes are its steady runs, a run at the pitch of the note
 * before holding that note. Nothing else is a note.
 */
public final class Phrases {
  /**
   * The longest pause inside a phrase: eight frames, 160 ms. JSW's main loop takes 3.1 to 4.5 frames a pass and plays
   * its tune's note once a pass (S2, measured): a pause of four frames is the tune going on.
   */
  public static final long GAP = 8L * 69_888;
  /** a phrase this short, with this few tones, is a blip */
  private static final long BLIP = 3L * 69_888;
  /** two pitches within this many cents are one */
  private static final double SAME = 50;

  public enum Shape {MUSIC, SWEEP_UP, SWEEP_DOWN, ARC, BLIP, CLICK, NOISE, OTHER}

  /** a note: emitter, from its first edge to its last, its whole period in T-states */
  public record Note(int key, long start, long end, double period) {
  }

  /** a phrase: its emitter, its time, its shape, and its notes if it is music */
  public record Phrase(int key, long start, long end, Shape shape, List<Note> notes, int tones, int debris) {
  }

  private final Consumer<Phrase> out;
  private final Map<Integer, Open> open = new HashMap<>();

  private static final class Open {
    final int key;
    long start, end;
    final List<Note> steady = new ArrayList<>(); // the steady runs, the held ones merged
    final List<Double> path = new ArrayList<>(); // the pitch, in cents, run after run (a glide gives two points)
    long steadyHalves, glideHalves, debrisHalves;
    int glides, debris, tones;
    boolean music; // it turned out to be music while still open: once it is, it stays
    double glideCents; // where the glides went, in cents

    Open(int key, long start) {
      this.key = key;
      this.start = start;
    }
  }

  public Phrases(Consumer<Phrase> out) {
    this.out = out;
  }

  /** a run of the speaker, as {@link Runs} reports it */
  public void run(Runs.Run r) {
    Open o = open.get(r.key());
    if (o != null && r.start() - o.end > GAP) {
      close(o);
      o = null;
    }
    if (o == null)
      open.put(r.key(), o = new Open(r.key(), r.start()));
    o.end = Math.max(o.end, r.end());
    if (!r.tone()) {
      o.debris++;
      o.debrisHalves += r.halves();
      return;
    }
    o.tones++;
    if (r.steady()) {
      o.steadyHalves += r.halves();
      double c = cents(r.period());
      Note last = o.steady.isEmpty() ? null : o.steady.get(o.steady.size() - 1);
      boolean added = !(last != null && Math.abs(cents(last.period()) - c) <= SAME / 2);
      if (added)
        o.steady.add(new Note(o.key, r.start(), r.end(), r.period()));
      else
        o.steady.set(o.steady.size() - 1, new Note(o.key, last.start(), r.end(), last.period()));
      o.path.add(c);
      // judged while it plays, at every new note and up to a point: a phrase that is not music by its 128th note is
      // judged when it ends
      if (added && !o.music && o.steady.size() >= 6 && o.steady.size() <= 128)
        o.music = shape(o) == Shape.MUSIC;
    } else {
      o.glides++;
      o.glideHalves += r.halves();
      double from = cents(r.firstPeriod()), to = cents(r.lastPeriod());
      o.glideCents += to - from;
      o.path.add(from);
      o.path.add(to);
    }
  }

  /** the phrase of {@code key} still open has turned out to be music */
  public boolean music(int key) {
    Open o = open.get(key);
    return o != null && o.music;
  }

  /**
   * Time went on: a phrase whose pause is longer than {@link #GAP} is over, but for the emitter's whose run is still
   * sounding ({@code sounding}, -1 for none): a note held for frames is one run, which comes when it ends.
   */
  public void frame(long now, int sounding) {
    frame(now, k -> k == sounding);
  }

  /** the same, with every emitter whose run is still sounding */
  public void frame(long now, java.util.function.IntPredicate sounding) {
    List<Open> over = new ArrayList<>();
    for (Open o : open.values())
      if (!sounding.test(o.key) && now - o.end > GAP)
        over.add(o);
    over.forEach(this::close);
  }

  /** every phrase still open */
  public void flush() {
    new ArrayList<>(open.values()).forEach(this::close);
  }

  /** the pitch of a whole period, in cents from an arbitrary origin: higher is higher */
  static double cents(double period) {
    return -1200 * Math.log(period) / Math.log(2);
  }

  private void close(Open o) {
    open.remove(o.key);
    Shape shape = shape(o);
    out.accept(new Phrase(o.key, o.start, o.end, shape, shape == Shape.MUSIC ? List.copyOf(o.steady) : List.of(),
        o.tones, o.debris));
  }

  private static Shape shape(Open o) {
    if (o.tones == 0)
      return o.debris <= 3 ? Shape.CLICK : Shape.NOISE;
    // noise: random half periods make three-half "tones" now and then, and twice as many scraps; a sound that
    // carries the edge before it carries about one scrap per tone (JSW's effects, measured)
    if (o.debris >= 8 && o.debris > 1.5 * o.tones)
      return Shape.NOISE;
    if (o.glideHalves > o.steadyHalves)
      return o.glideCents >= 0 ? Shape.SWEEP_UP : Shape.SWEEP_DOWN;
    // a ladder: most of the steps between its tones are one and the same step of the period (JSW's fall, +416 T a
    // step, and back and forth at the bottom). A tune does not walk by one step; this goes before the turns, which a
    // ladder that wobbles at its end has enough of to pass for a tune
    double step = ladder(o.steady);
    if (step != 0)
      return step > 0 ? Shape.SWEEP_DOWN : Shape.SWEEP_UP;
    // the steady tones: where the pitch goes between them
    List<Double> levels = new ArrayList<>();
    for (double c : o.path)
      if (levels.isEmpty() || Math.abs(c - levels.get(levels.size() - 1)) > SAME)
        levels.add(c);
    int changes = levels.size() - 1, reversals = 0, distinct = distinct(levels);
    for (int i = 2; i < levels.size(); i++)
      if (Math.signum(levels.get(i) - levels.get(i - 1)) != Math.signum(levels.get(i - 1) - levels.get(i - 2)))
        reversals++;
    if (o.steady.size() >= 6 && distinct >= 3 && reversals >= 0.25 * changes)
      return Shape.MUSIC;
    if (o.end - o.start <= BLIP && o.steady.size() <= 2)
      return Shape.BLIP;
    if (changes == 0)
      return Shape.OTHER;
    if (reversals == 0)
      return levels.get(levels.size() - 1) > levels.get(0) ? Shape.SWEEP_UP : Shape.SWEEP_DOWN;
    return Shape.ARC;
  }

  /** the step of the period that 60 % of the steps between the notes take, or 0 if there is none (or too few) */
  private static double ladder(List<Note> notes) {
    int n = notes.size() - 1;
    if (n < 4)
      return 0;
    double[] d = new double[n];
    for (int i = 0; i < n; i++)
      d[i] = notes.get(i + 1).period() - notes.get(i).period();
    // sorted, the steps near each one are a window of the array: a long phrase is judged in n log n, not n squared
    double[] sorted = d.clone();
    java.util.Arrays.sort(sorted);
    for (int i = 0, lo = 0, hi = 0; i < n; i++) {
      double x = sorted[i], tol = Math.max(8, 0.03 * Math.abs(x));
      while (sorted[lo] < x - tol || Math.signum(sorted[lo]) != Math.signum(x) && lo < i)
        lo++;
      if (hi < i)
        hi = i;
      while (hi + 1 < n && sorted[hi + 1] <= x + tol && Math.signum(sorted[hi + 1]) == Math.signum(x))
        hi++;
      if (hi - lo + 1 >= 0.6 * n)
        return x;
    }
    return 0;
  }

  /** how many pitches the levels hold: sorted, a new one wherever the next is more than {@link #SAME} above */
  private static int distinct(List<Double> levels) {
    if (levels.isEmpty())
      return 0;
    double[] c = new double[levels.size()];
    for (int i = 0; i < c.length; i++)
      c[i] = levels.get(i);
    java.util.Arrays.sort(c);
    int n = 1;
    for (int i = 1; i < c.length; i++)
      if (c[i] - c[i - 1] > SAME)
        n++;
    return n;
  }
}
