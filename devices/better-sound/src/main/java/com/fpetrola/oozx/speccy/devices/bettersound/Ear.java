package com.fpetrola.oozx.speccy.devices.bettersound;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * The ear of PLAN-SONIDO.md, S2: what the game's speaker played, as phrases of music and of effects. Fed the OUTs to
 * the ULA's port as the replay makes them -- time, byte, the pc of the OUT and where its routine returns to -- and the
 * end of each frame; it knows no game. {@link Runs} turns the speaker's edges into runs, {@link Phrases} an emitter's
 * runs into phrases and their notes, {@link Emitters} says which routine plays music, {@link Tuning} how far off the
 * semitones the game's notes are.
 *
 * <p>The runs of a frame wait for the frame's end: if the frame's edges hold no run for long and two square waves
 * explain them ({@link Voices}), the frame was two voices, and each voice is a run of its own, under a key of its own;
 * otherwise the runs go on as they came.
 *
 * <p>Edges closer than {@link #CARRIER} are no pitch anyone hears: they are two OUTs taking turns, each a voice of its
 * own, and the speaker shakes while they differ (Equinox). From then on each changes where what its OUTs write does,
 * and has runs of its own, as a voice {@link Voices} found has.
 */
public final class Ear {
  /** what the ear's first step is fed: every change of the speaker's level, and the end of every frame, in T-states */
  public interface Input {
    /** the speaker changed level at {@code t}, by an OUT of emitter {@code key} */
    void edge(long t, int key);

    /** a frame of the game ended at {@code end}: time went on with or without edges */
    void frame(long end);
  }

  private final Consumer<Phrases.Phrase> out;
  private final Map<Long, Integer> keys = new HashMap<>();
  private final List<int[]> named = new ArrayList<>();
  private final Emitters emitters = new Emitters();
  private final Tuning tuning = new Tuning();
  private final Phrases phrases;
  private final Runs runs;
  private int level = -1;
  /** a half period shorter than this, 20 kHz, is above hearing */
  private static final int CARRIER = 88;
  private long lastEdge = Long.MIN_VALUE / 2;
  private int lastKey;
  private boolean carrier;
  /** the voice, 0 or 1, of each OUT seen taking turns: never the one of the OUT it took turns with */
  private final Map<Integer, Integer> sides = new HashMap<>();
  /** the levels each OUT wrote, a bit each: one that wrote both is a voice; one that always writes one is half a pulse */
  private final Map<Integer, Integer> wrote = new HashMap<>();
  // the frame being heard: its edges and the runs that closed in it
  private long[] edgeT = new long[1024];
  private int[] edgeKey = new int[1024];
  private int edges;
  private final List<Runs.Run> held = new ArrayList<>();
  private final Runs[] voices = new Runs[2];
  private final int[] voiceLevel = {-1, -1};
  private final List<Consumer<Runs.Run>> runListeners = new ArrayList<>();
  /** how many frames were heard as two voices */
  int splitFrames;

  public Ear(Consumer<Phrases.Phrase> out) {
    this.out = out;
    phrases = new Phrases(this::phrase);
    runs = new Runs(r -> held.add(new Runs.Run(emitters.find(r.key()), r.start(), r.end(), r.halves(),
        r.firstPeriod(), r.lastPeriod())), emitters::join);
    for (int v = 0; v < 2; v++) {
      int voice = v;
      voices[v] = new Runs(r -> toPhrases(new Runs.Run(voiceKey(r.key(), voice), r.start(), r.end(), r.halves(),
          r.firstPeriod(), r.lastPeriod())), 2 * Runs.LONGEST_HALF);
    }
  }

  /** the key of voice {@code v} of the emitter of {@code key} */
  private int voiceKey(int key, int v) {
    int[] n = named.get(emitters.find(key));
    return key(n[0], n[1], v + 1);
  }

  /** the key of an OUT's pc and its routine's return address (-1 for none) */
  public int key(int pc, int caller) {
    return key(pc, caller, 0);
  }

  private int key(int pc, int caller, int voice) {
    long k = ((long) pc << 32 | (caller & 0xffffL) << 8 | voice) ^ (caller < 0 ? 0xff0000L : 0);
    return keys.computeIfAbsent(k, x -> {
      named.add(new int[]{pc, caller, voice});
      return named.size() - 1;
    });
  }

  /** the pc, the caller and the voice (0 for the emitter itself, 1 and 2 for its two voices) a key stands for */
  public int[] named(int key) {
    return named.get(key).clone();
  }

  /** the emitter a key belongs to: the key of the first of the OUTs that play one sound together */
  public int emitter(int key) {
    return emitters.find(key);
  }

  /** an OUT to the ULA's port at {@code t}: only a change of the speaker (bit 4) is an edge */
  public void out(long t, int value, int pc, int caller) {
    int l = value >> 4 & 1, k = key(pc, caller);
    wrote.merge(k, 1 << l, (a, b) -> a | b);
    Integer side = sides.get(k);
    if (side != null && voiceLevel[side] != (voiceLevel[side] = l))
      voices[side].edge(t, k);
    if (l == level)
      return;
    level = l;
    boolean burst = t - lastEdge < CARRIER;
    if (!burst)
      endCarrier();
    else if (k != lastKey && wrote.get(k) == 3 && wrote.getOrDefault(lastKey, 0) == 3) {
      int first = sides.computeIfAbsent(lastKey, x -> 1 - sides.getOrDefault(k, 1));
      sides.putIfAbsent(k, 1 - first);
      emitters.join(lastKey, k);
    }
    carrier = burst;
    lastEdge = t;
    lastKey = k;
    if (!burst)
      edge(t, k);
  }

  /** the shaking over: it fell at its last edge */
  private void endCarrier() {
    if (carrier) {
      carrier = false;
      edge(lastEdge, lastKey);
    }
  }

  private void edge(long t, int k) {
    if (sides.containsKey(k))
      return;
    if (edges == edgeT.length) {
      edgeT = Arrays.copyOf(edgeT, edges * 2);
      edgeKey = Arrays.copyOf(edgeKey, edges * 2);
    }
    edgeT[edges] = t;
    edgeKey[edges++] = k;
    runs.edge(t, k);
  }

  /** a frame ended at {@code end} */
  public void frame(long end) {
    if (end - lastEdge >= CARRIER)
      endCarrier();
    runs.frame(end);
    for (Runs v : voices)
      v.frame(end);
    if (!twoVoices())
      held.forEach(this::toPhrases);
    held.clear();
    edges = 0;
    int k = runs.sounding(), a = voices[0].sounding(), b = voices[1].sounding();
    phrases.frame(end, e -> k >= 0 && e == emitters.find(k) || a >= 0 && e == voiceKey(a, 0)
        || b >= 0 && e == voiceKey(b, 1));
  }

  /** what is still open: at the end of a recording */
  public void flush() {
    endCarrier();
    runs.flush();
    for (Runs v : voices)
      v.flush();
    held.forEach(this::toPhrases);
    held.clear();
    phrases.flush();
  }

  /** a tone being played now: its emitter, where it started and its whole period */
  public record Sounding(int key, long start, double period) {
  }

  /**
   * The emitter of {@code key} is playing music now: its phrase still open has turned out to be music, or nine
   * tenths of what it played so far was. Asked frame by frame by whoever plays the music instead of the beeper.
   */
  public boolean music(int key) {
    int e = emitters.find(key);
    return phrases.music(e) || phrases.music(voiceKey(e, 0)) || phrases.music(voiceKey(e, 1))
        || emitters.mostlyMusic(e);
  }

  /** every run, with its emitter, as it ends: at the end of the frame it ended in, or the next */
  public void onRun(Consumer<Runs.Run> listener) {
    runListeners.add(listener);
  }

  /** the tones being played now that have three half periods already: the speaker's, and each voice's */
  public List<Sounding> sounding() {
    List<Sounding> now = new ArrayList<>(3);
    int k = runs.sounding();
    sounding(runs, k < 0 ? -1 : emitters.find(k), now);
    for (int v = 0; v < 2; v++) {
      int s = voices[v].sounding();
      sounding(voices[v], s < 0 ? -1 : voiceKey(s, v), now);
    }
    return now;
  }

  private static void sounding(Runs r, int key, List<Sounding> now) {
    if (key >= 0 && r.openHalves() >= 3)
      now.add(new Sounding(key, r.openStart(), r.openPeriod()));
  }

  public Emitters emitters() {
    return emitters;
  }

  public Tuning tuning() {
    return tuning;
  }

  /** the frame's edges, if they were two voices of one emitter: each voice a run, to the phrases */
  private boolean twoVoices() {
    if (edges == 0)
      return false;
    int emitter = emitters.find(edgeKey[0]);
    for (int i = 1; i < edges; i++)
      if (emitters.find(edgeKey[i]) != emitter)
        return false;
    if (edges < 32 || mostlyTone())
      return false;
    Voices.Split split = Voices.split(edgeT, edges);
    if (split == null)
      return false;
    splitFrames++;
    // each voice is, in this frame, the grid Voices fitted: one steady run of its half period, from its first edge
    // to its last; a note it holds over frames is one note in its phrase, as for any run
    for (int v = 0; v < 2; v++) {
      long first = Long.MIN_VALUE, last = 0;
      int count = 0;
      for (int i = 0; i < edges; i++)
        if (split.voice()[i] == v) {
          if (first == Long.MIN_VALUE)
            first = edgeT[i];
          last = edgeT[i];
          count++;
        }
      double period = 2 * (v == 0 ? split.halfA() : split.halfB());
      // as many half periods as its grid has from its first edge to its last, not as many edges as were its: an edge
      // of the voice that fell on the other's is not its, and counting edges stretched the period (MM's title: 4 %)
      int halves = (int) Math.max(count - 1, Math.round(2.0 * (last - first) / period));
      toPhrases(new Runs.Run(voiceKey(emitter, v), first, last, halves, period, period));
    }
    return true;
  }

  /** a note held for frames is a run still open, which counts as much as one that closed here */
  private boolean mostlyTone() {
    long tone = Math.min(edges, runs.openHalves());
    for (Runs.Run r : held)
      if (r.tone())
        tone += r.halves();
    return tone >= edges / 2;
  }

  private void toPhrases(Runs.Run r) {
    for (Consumer<Runs.Run> l : runListeners)
      l.accept(r);
    phrases.run(r);
  }

  private void phrase(Phrases.Phrase p) {
    emitters.phrase(p);
    for (Phrases.Note n : p.notes())
      tuning.note(n.period(), n.end() - n.start() + 1);
    out.accept(p);
  }
}
