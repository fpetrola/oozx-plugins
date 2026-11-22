package com.fpetrola.oozx.speccy.devices.bettersound;

import com.fpetrola.oozx.speccy.devices.ay.Ay;
import com.fpetrola.oozx.speccy.modules.sound.AudioOutput;
import com.fpetrola.oozx.speccy.screen.Knob;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * The instrument of PLAN-SONIDO.md, S3 and S4: what the ear hears in the game's speaker, played on an AY -- the
 * emulator's own, with no machine around it -- and the speaker's edges it plays taken from the beeper. The music on
 * channels A and B, with an envelope on every note; the effects on C, each tone as the speaker played it with the
 * instrument of the shape its routine plays most; clicks and noise stay the beeper's (D5). Each channel is an AY of its own at its
 * loudest, and the envelope is laid on its samples, smooth.
 *
 * <p>It is handed frame N+1 to hear and then frame N to mix. Hearing N+1 is what decides N: by then the ear
 * knows whether the emitter of N's edges is playing music, and the runs of N have ended. Those of a music emitter
 * become notes: a run at the pitch of the note sounding holds it, any other starts a note, and a note no run held for
 * {@code sound.ay.hold} is released. A beeper tune that plays a few half periods a frame (JSW's) is held between them:
 * that, and the envelope, is what takes the buzz out of it. The tones of the other emitters are effects: claimed run
 * by run, played at their pitch (a glide with its ramp) and held, as the instrument says, between the bursts.
 *
 * <p>The pitch is the beeper's, exactly: the AY counts at half the CPU's clock and divides by 16, so a whole period of
 * P T-states is the AY's period P / 32. No game's constant, no tuning: what the speaker played is what it plays.
 */
public final class AyStage {
  private static final int CLOCK = 3_500_000;
  /** T-states in a millisecond */
  private static final double MS = CLOCK / 1000.0;
  /** a run within this many cents of the note sounding holds it */
  private static final double SAME = 25;
  /** the volumes are written this many times a frame: a tone of JSW's jump lasts a quarter of one */
  private static final int STEPS = 8;
  /** a glide's period is written every this many T-states */
  private static final int RAMP = 1024;
  /**
   * The AY's level against the beeper's: measured on JSW's first 80 s through the viewer's sound (S5), the AY at
   * the volumes it plays sounded at half the beeper's RMS (1,100 against 2,200); twice is the beeper's, and its peak
   * (11,605) leaves room under 32,767.
   */
  private static final double GAIN = 2;
  /** the channel of the effects */
  private static final int FX = 2;

  // the knobs, set from the window's thread, read from the machine's
  private volatile boolean on = true, effectsOn = true;
  /**
   * The music's envelope: a note falls over {@code decay} to a low sustain instead of holding at a high one. JSW's long
   * notes are one pitch played pass after pass of its main loop, a burst each; held at 9 of 15 they lasted until the
   * next note came (Fernando: "hay unas notas que se estiran mucho ... las largas quedan demasiado largas"): nine notes
   * of ten up to 525 ms at full volume. Of the four he heard he chose 256 ms -- falling over 450 ms to 3 of 15.
   */
  private volatile float volume = 13, attack = 4, decay = 450, sustain = 3, release = 140, hold = 120, fxVolume = 12;

  private final Ear ear = new Ear(p -> {
  });
  /**
   * An AY for each channel -- the music's two and the effects' -- at its loudest, each mixed apart: the envelope is
   * laid on its samples, smooth, and the effects stand where the player is between the speakers. The emulator's AY
   * has sixteen volumes three decibels apart, and an envelope written to them was a staircase whose every step was
   * heard as the note struck again (Fernando: "como si se repitiera la última nota cada 10 ms o menos", SmoothTest).
   */
  private final Ay[] ays = new Ay[3];
  private int[] own;
  /** each channel's loudness at the frame's STEPS + 1 points, from 0 to 1 */
  private final double[][] loud = new double[3][STEPS + 1];
  private final boolean[] aySounds = new boolean[3];
  /**
   * {@code sound.ay.duck}: how much the music gives way while an effect sounds, from 0 (not at all) to 1 (silent). On
   * the beeper the two never sound at once -- the game plays the tune's half periods, then the jump's -- and on the
   * AY the tune was held under the jump: two pitches with nothing to do with each other at once
   */
  private volatile float duck = 0.6f;
  /**
   * {@code sound.ay.harmony}: what is played under the melody on its AY's other two channels, at
   * {@code sound.ay.harmonyVolume} -- nothing, the octave and the bass two below, or the chords of the tune's key
   * where it has one (D4, Fernando's third: "octava y bajo en todos, acordes donde hay tonalidad")
   */
  private volatile String harmony = "chords";
  private volatile float harmonyVolume = 11;
  /** each music emitter's harmony, with what it has heard of its key */
  private final Map<Integer, Harmony> tunes = new HashMap<>();
  /** what each channel's AY plays under its note, in semitones below it (null: nothing) */
  private final int[][] below = new int[3][];
  private final Deque<Runs.Run> runs = new ArrayDeque<>();
  /** the effects' runs claimed from the beeper, to be played */
  private final Map<Runs.Run, Boolean> claimed = new IdentityHashMap<>();
  private final long[] startOf = new long[4];
  private long start;
  private FrameSound heard;
  private boolean mixerSet;
  private final Voice[] channels = new Voice[3];
  private final List<long[]> writes = new ArrayList<>(); // {t, register, value}, sorted before the AY sees them
  /**
   * The emulator's AY works 32 T-states at a time and loses a write in the last 32 of a frame (TailNoteTest). What
   * falls there -- a note that starts in the tail of a long frame, pinned to its last T-state -- is written at the
   * start of the next frame instead: that tail is not in the sound anyway.
   */
  private static final int LAST_STEP = 32;
  private final List<long[]> carried = new ArrayList<>();
  private int musicNotes, effectNotes;
  /** the effect sounding at the end of the frame {@code soundingFrame}, claimed for it */
  private Runs.Run sounding;
  private long soundingFrame = -1;

  /** a note on a channel of the AY, with what shapes it */
  private static final class Voice {
    int key;
    double period;
    long on, last, off = -1;
    Envelope envelope;
    double loudness, hold; // loudness from 0 to 15, hold in ms
  }

  public AyStage(AudioOutput output) {
    ear.onRun(runs::add);
    for (int c = 0; c < 3; c++)
      ays[c] = new Ay(output);
    own = new int[2 * output.frameSize()];
  }

  /** Its AYs play through the machine's output again, made anew for its speed. */
  void takeOutputFrom(AudioOutput output) {
    for (Ay ay : ays)
      ay.takeOutputFrom(output);
    own = new int[2 * output.frameSize()];
  }

  /** Its knobs, as the window shows them: what plays the music and the effects, and how a note sounds. */
  List<Knob> knobs() {
    List<String> sources = List.of("beeper", "ay");
    return List.of(
        Knob.choice("music", "Music", "Played by the beeper as the game wrote it, or by an AY", "What plays",
            sources, "ay", () -> on ? "ay" : "beeper", v -> on = "ay".equals(v)),
        Knob.choice("effects", "Effects", "Played by the beeper or the AY; the clicks stay on the beeper", "What plays",
            sources, "ay", () -> effectsOn ? "ay" : "beeper", v -> effectsOn = "ay".equals(v)),
        Knob.choice("harmony", "Harmony", "Under the melody: nothing, the octave and the bass, or chords where the tune has a key",
            "Music", List.of("none", "octave", "chords"), "chords", () -> harmony, v -> harmony = (String) v),
        level("volume", "Music volume", "Of 15", "Music", 0, 15, 1, 13, () -> volume, v -> volume = v),
        level("harmonyVolume", "Harmony volume", "Of 15", "Music", 0, 15, 1, 11, () -> harmonyVolume, v -> harmonyVolume = v),
        level("duck", "Duck", "How much the music gives way while an effect sounds (0 = not at all)", "Music", 0, 1, 0.05, 0.6,
            () -> duck, v -> duck = v),
        level("fxVolume", "Effects volume", "Of 15", "Effects", 0, 15, 1, 12, () -> fxVolume, v -> fxVolume = v),
        level("attack", "Attack", "Milliseconds a note takes to sound", "Envelope", 0, 200, 1, 4, () -> attack, v -> attack = v),
        level("decay", "Decay", "Milliseconds a note takes to fall to the sustain", "Envelope", 0, 2000, 10, 450, () -> decay,
            v -> decay = v),
        level("sustain", "Sustain", "What a held note falls to, of 15", "Envelope", 0, 15, 1, 3, () -> sustain, v -> sustain = v),
        level("release", "Release", "Milliseconds a note takes to go once let go", "Envelope", 0, 2000, 10, 140, () -> release,
            v -> release = v),
        level("hold", "Hold", "Milliseconds of silence that let a note go", "Envelope", 0, 1000, 10, 120, () -> hold,
            v -> hold = v));
  }

  static Knob level(String key, String label, String about, String group, double min, double max, double step,
                    double fallback, java.util.function.Supplier<Float> read, java.util.function.Consumer<Float> write) {
    return Knob.number(key, label, about, group, min, max, step, fallback, () -> (double) read.get(),
        v -> write.accept(((Number) v).floatValue()));
  }

  public void hear(FrameSound frame) {
    startOf[(int) (frame.index() & 3)] = start;
    for (int i = 0; i < frame.size(); i++)
      ear.out(start + frame.t(i), frame.value(i), frame.pc(i), frame.caller(i));
    start += frame.length();
    ear.frame(start);
    // the frame before, now that this one is heard: the music's OUTs are the AY's, and the effects' tones
    if (on && heard != null && heard.index() == frame.index() - 1)
      claim(heard, startOf[(int) (heard.index() & 3)]);
    heard = frame;
  }

  private void claim(FrameSound f, long s) {
    for (int i = 0; i < f.size(); i++)
      if (ear.music(ear.key(f.pc(i), f.caller(i))))
        f.claim(i);
    if (!effectsOn)
      return;
    long end = s + f.length();
    // an effect still sounding when the frame ends (JSW's game over: a burst of most of a frame) is the AY's from
    // where it began; its run, once it ends, goes on from there
    for (Ear.Sounding now : ear.sounding())
      if (now.start() < end && !ear.music(now.key())) {
        for (int i = 0; i < f.size(); i++)
          if (s + f.t(i) >= now.start() && ear.emitter(ear.key(f.pc(i), f.caller(i))) == now.key())
            f.claim(i);
        sounding = new Runs.Run(now.key(), now.start(), end, 3, now.period(), now.period());
        soundingFrame = f.index();
      }
    for (Runs.Run r : runs)
      if (r.tone() && r.start() < end && r.end() >= s && !ear.music(r.key())) {
        claimed.put(r, Boolean.TRUE);
        for (int i = 0; i < f.size(); i++) {
          long t = s + f.t(i);
          if (t >= r.start() && t <= r.end() && ear.emitter(ear.key(f.pc(i), f.caller(i))) == r.key())
            f.claim(i);
        }
      }
  }

  public int mixInto(FrameSound frame, int[] stereo, int samples) {
    long s = startOf[(int) (frame.index() & 3)], end = s + frame.length();
    int mixed = frame.mixed(); // what the viewer mixes of it: past it, a write lands on its last T-state
    if (!on) {
      while (!runs.isEmpty() && runs.peekFirst().start() < end)
        claimed.remove(runs.pollFirst());
      for (int c = 0; c < 3; c++)
        channels[c] = null;
      return 0;
    }
    writes.clear();
    for (long[] w : carried) // written first, at the start: anything this frame writes at its start comes after
      writes.add(new long[]{0, w[1], w[2]});
    carried.clear();
    if (!mixerSet) {
      for (int c = 0; c < 3; c++)
        writes.add(new long[]{0, 16 * c + 7, 0x38}); // its three tones, no noise: the note and the two under it
      mixerSet = true;
    }
    Envelope music = new Envelope(attack, decay, sustain / 15.0, release);
    List<Runs.Run> goesOn = new ArrayList<>();
    while (!runs.isEmpty() && runs.peekFirst().start() < end) {
      Runs.Run r = runs.pollFirst();
      if (r.tone() && r.steady() && ear.music(r.key()))
        tunes.computeIfAbsent(r.key(), k -> new Harmony()).heard(ear.tuning().midi(r.period()), r.end() - r.start());
      if (r.tone() && ear.music(r.key()))
        note(musicChannel(r.key()), r.key(), r.start(), r.end(), r.period(), r.steady(), s, music, volume, hold);
      else if (claimed.remove(r) != null)
        effect(r, s);
      if (r.end() >= end) // it goes on into the next frame: there its edges are claimed too, and it holds its note
        goesOn.add(r);
    }
    for (int i = goesOn.size() - 1; i >= 0; i--)
      runs.addFirst(goesOn.get(i));
    if (sounding != null && soundingFrame == frame.index()) {
      effect(sounding, s);
      sounding = null;
    }
    for (Ear.Sounding now : ear.sounding())
      if (now.start() < end && ear.music(now.key()))
        note(musicChannel(now.key()), now.key(), now.start(), end, now.period(), true, s, music, volume, hold);
    long span = Math.min(frame.length(), mixed);
    for (int q = 0; q <= STEPS; q++) {
      long tq = s + span * q / STEPS;
      for (int c = 0; c < 3; c++) {
        Voice v = channels[c];
        double level = 0;
        if (v != null && v.on <= tq) {
          if (v.off < 0 && tq > v.last + v.hold * MS)
            v.off = v.last + (long) (v.hold * MS);
          double sinceOff = v.off < 0 ? -1 : (tq - v.off) / MS;
          level = v.envelope.level((tq - v.on) / MS, sinceOff) * v.loudness;
          if (q < STEPS && v.envelope.over((tq - v.on) / MS, sinceOff))
            channels[c] = null;
        }
        loud[c][q] = amplitude(level);
      }
    }
    // the music gives way to the effect as loud as the effect sounds -- all of it from half the effects' volume up,
    // and back as the effect dies out (a release's tail at -60 dB silenced the music, at first) -- point by point, so
    // that between two points it is a ramp
    float d = duck;
    if (d > 0) {
      double full = amplitude(fxVolume / 2);
      for (int q = 0; q <= STEPS; q++) {
        double presence = full <= 0 ? 0 : Math.min(1, loud[FX][q] / full);
        for (int c = 0; c < FX; c++)
          loud[c][q] *= 1 - d * presence;
      }
    }
    // an AY at its loudest while its channel sounds this frame, and silent when it does not: the envelope is laid on
    // its samples, and a silent AY is one the emulator does not synthesise (at its loudest all three cost 1.5 ms)
    for (int c = 0; c < 3; c++) {
      boolean sounds = false;
      for (double a : loud[c])
        sounds |= a > 0;
      if (sounds != aySounds[c]) {
        aySounds[c] = sounds;
        volumes(c);
      }
    }
    writes.sort((a, b) -> Long.compare(a[0], b[0]));
    for (long[] w : writes) { // each channel's AY has its registers numbered from 16 times the channel
      if (w[0] >= mixed - LAST_STEP) {
        carried.add(w);
        continue;
      }
      ays[(int) (w[1] >> 4)].write((int) (w[1] & 15), (int) w[2], w[0]);
    }
    int most = 0;
    for (int c = 0; c < 3; c++) {
      ays[c].endFrame(mixed);
      java.util.Arrays.fill(own, 0, 2 * samples, 0);
      int n = ays[c].mixInto(own, samples);
      most = Math.max(most, n);
      for (int i = 0; i < n; i++) {
        // the loudness between the frame's points, a straight line from one to the next: no steps
        double x = (double) i * STEPS / n;
        int q = (int) x;
        double a = q >= STEPS ? loud[c][STEPS] : loud[c][q] + (loud[c][q + 1] - loud[c][q]) * (x - q);
        stereo[2 * i] += (int) Math.round(own[2 * i] * a * GAIN);
        stereo[2 * i + 1] += (int) Math.round(own[2 * i + 1] * a * GAIN);
      }
    }
    return most;
  }

  /** a run of an effect: on the effects' channel at its pitch, a glide with its ramp, held as its instrument says */
  private void effect(Runs.Run r, long frameStart) {
    Instruments.Instrument in = Instruments.of(ear.emitters().commonest(r.key()));
    Voice v = channels[FX];
    boolean legato = v != null && v.key == r.key() && v.off < 0;
    if (below[FX] != null) { // the effects' channel was the music's, with the menu's effects on the beeper
      below[FX] = null;
      under(FX, Math.max(r.start(), frameStart) - frameStart, true);
    }
    if (!legato)
      effectNotes++;
    note(FX, r.key(), r.start(), r.end(), r.firstPeriod(), true, frameStart, in.envelope(), fxVolume * in.loudness(),
        in.hold(), legato);
    if (!r.steady()) // the ramp: the period from the first to the last, as the speaker went
      for (long t = Math.max(r.start(), frameStart) + RAMP; t < r.end(); t += RAMP) {
        double p = r.firstPeriod() + (r.lastPeriod() - r.firstPeriod()) * (t - r.start()) / (r.end() - r.start());
        period(FX, p, t - frameStart);
        if (trace != null)
          trace.pitch(t, r.key(), p);
      }
  }

  /** a run of a music emitter, from {@code from} to {@code to}: it holds the note sounding, or starts one */
  private void note(int c, int key, long from, long to, double period, boolean steady, long frameStart,
                    Envelope envelope, double loudness, double holdMs) {
    if (c < 0)
      return;
    Voice v = channels[c];
    if (v != null && v.key == key && v.off < 0 && Math.abs(1200 * Math.log(period / v.period) / Math.log(2)) <= SAME) {
      v.last = Math.max(v.last, to);
      return;
    }
    if (!steady || to < frameStart) { // a glide, or a run already played: it only says the emitter is still on
      if (v != null && v.key == key)
        v.last = Math.max(v.last, to);
      return;
    }
    musicNotes++;
    note(c, key, from, to, period, true, frameStart, envelope, loudness, holdMs, false);
    harmonize(c, key, period, Math.max(from, frameStart), frameStart);
  }

  /** the two voices under a note of music struck on channel {@code c}, as {@code sound.ay.harmony} says */
  private void harmonize(int c, int key, double period, long when, long frameStart) {
    long at = when - frameStart;
    String mode = harmony;
    int[] b = null;
    if (!"none".equals(mode)) {
      Harmony h = tunes.computeIfAbsent(key, k -> new Harmony());
      b = "octave".equals(mode) ? new int[]{12, 24} : h.under(ear.tuning().midi(period), anotherVoice(c, key));
      for (int i = 0; i < 2; i++)
        if (b[i] != Harmony.NONE)
          period(c, 1 + i, period * Math.pow(2, b[i] / 12.0), at);
      if (trace != null)
        trace.harmony(when, key, b);
    }
    below[c] = b;
    under(c, at, true);
  }

  /** another emitter's music sounding on another channel: a tune of two voices, a harmony already */
  private boolean anotherVoice(int c, int key) {
    for (int o = 0; o < FX; o++)
      if (o != c && channels[o] != null && channels[o].key != key && channels[o].off < 0)
        return true;
    return false;
  }

  /**
   * Channel {@code c}'s AY's three volumes as the frame begins: the note at its loudest while the channel sounds, and
   * the voices under it; silent, all three, when it does not
   */
  private void volumes(int c) {
    writes.add(new long[]{0, 16 * c + 8, aySounds[c] ? 15 : 0});
    under(c, 0, aySounds[c]);
  }

  /**
   * The volumes of the two voices under channel {@code c}'s note, from {@code at}. Never the note's own: a note struck
   * mid-frame on an AY silent the frame before had it set to 0 for the rest of the frame (HeardTest: 98.1 % of Dan's windows where it is 99.2)
   */
  private void under(int c, long at, boolean sounds) {
    int h = Math.round(harmonyVolume);
    for (int i = 0; i < 2; i++)
      writes.add(new long[]{at, 16 * c + 9 + i, sounds && below[c] != null && below[c][i] != Harmony.NONE ? h : 0});
  }

  /** a note on channel {@code c}: a new one, or (legato) the one sounding moved to this pitch without starting over */
  private void note(int c, int key, long from, long to, double period, boolean steady, long frameStart,
                    Envelope envelope, double loudness, double holdMs, boolean legato) {
    Voice v = channels[c];
    if (v == null || v.key != key)
      channels[c] = v = new Voice();
    long at = Math.max(from, frameStart);
    if (!legato) {
      v.on = at;
      v.off = -1;
    }
    v.key = key;
    v.period = period;
    v.last = Math.max(v.last, to);
    v.envelope = envelope;
    v.loudness = loudness;
    v.hold = holdMs;
    period(c, period, at - frameStart);
    if (trace != null)
      trace.pitch(at, key, period);
  }

  private void period(int c, double period, long at) {
    period(c, 0, period, at);
  }

  /** the period of tone {@code tone} (0 the note, 1 and 2 the voices under it) of channel {@code c}'s AY */
  private void period(int c, int tone, double period, long at) {
    int n = (int) Math.max(1, Math.min(4095, Math.round(period / 32)));
    writes.add(new long[]{at, register(c, 2 * tone), n & 0xff});
    writes.add(new long[]{at, register(c, 2 * tone + 1), n >> 8});
  }

  /**
   * The register {@code which} of channel {@code c}'s AY: each channel is an AY of its own, whose registers are
   * numbered from 16 times the channel
   */
  private static int register(int c, int which) {
    return 16 * c + which;
  }

  /** the AY's level at each of its sixteen volumes: the emulator's table, which is the real chip's */
  private static final int[] LEVELS = {0, 901, 1341, 1904, 2775, 4053, 5552, 8972, 11084, 17345, 23115, 29487, 37380,
      45041, 55585, 65535};

  /**
   * The AY's amplitude at a volume from 0 to 15 against its loudest: its own level at each of its sixteen volumes and
   * a straight line between two of them -- the steps it has, followed without stepping
   */
  static double amplitude(double volume) {
    if (volume <= 0)
      return 0;
    double v = Math.min(15, volume);
    int lo = (int) Math.floor(v);
    double a = lo >= 15 ? LEVELS[15] : LEVELS[lo] + (LEVELS[lo + 1] - LEVELS[lo]) * (v - lo);
    return a / LEVELS[15];
  }

  /** the channel of an emitter's notes: the one it has, or a free one, or the one released longest ago */
  private int musicChannel(int key) {
    int n = effectsOn ? 2 : 3, free = -1, released = -1;
    for (int c = 0; c < n; c++) {
      Voice v = channels[c];
      if (v != null && v.key == key)
        return c;
      if (v == null && free < 0)
        free = c;
      if (v != null && v.off >= 0 && (released < 0 || v.off < channels[released].off))
        released = c;
    }
    return free >= 0 ? free : released;
  }

  /** what the AY is told to play, for the tests: at {@code t}, emitter {@code key}'s pitch is now {@code period} */
  interface Trace {
    void pitch(long t, int key, double period);

    /** under the note emitter {@code key} struck at {@code t}, these semitones below it ({@link Harmony#NONE}: none) */
    default void harmony(long t, int key, int[] below) {
    }
  }

  private Trace trace;

  void trace(Trace t) {
    trace = t;
  }

  /** the ear it plays from, for the tests */
  Ear ear() {
    return ear;
  }

  /** what is played under the melody: the menu's {@code sound.ay.harmony} */
  void harmony(String mode) {
    harmony = mode;
  }

  /** the effects on the AY or left to the beeper: the menu's {@code sound.effects} */
  void effects(boolean on) {
    effectsOn = on;
  }

  /** how many notes of music it started, and of effects (for the tests) */
  int musicNotes() {
    return musicNotes;
  }

  int effectNotes() {
    return effectNotes;
  }
}
