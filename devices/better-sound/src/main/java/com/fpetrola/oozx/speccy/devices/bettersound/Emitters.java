package com.fpetrola.oozx.speccy.devices.bettersound;

import java.util.HashMap;
import java.util.Map;

/**
 * Who plays what (PLAN-SONIDO.md, S2): an emitter is a routine that drives the speaker, known by the pc of its OUT
 * and the place it was called from. Two OUTs that play one sound between them are one emitter. An emitter whose
 * phrases are mostly music plays music, and one whose phrases are sweeps, arcs and blips plays effects: the
 * separation is by what each routine played, never by an address someone said was the music.
 */
public final class Emitters {
  public enum Kind {MUSIC, EFFECTS, UNKNOWN}

  private final Map<Integer, Integer> parent = new HashMap<>();
  private final Map<Integer, long[]> time = new HashMap<>(); // emitter -> {music, effects}, in T-states
  private final Map<Integer, int[]> shapes = new HashMap<>(); // emitter -> phrases of each shape, music aside

  /** the emitter a key belongs to */
  public int find(int key) {
    int p = parent.getOrDefault(key, key);
    if (p == key)
      return key;
    int root = find(p);
    parent.put(key, root);
    return root;
  }

  /** two keys played one run: they are one emitter */
  public void join(int a, int b) {
    int ra = find(a), rb = find(b);
    if (ra == rb)
      return;
    int root = Math.min(ra, rb), other = Math.max(ra, rb);
    parent.put(other, root);
    int[] so = shapes.remove(other);
    if (so != null) {
      int[] r = shapes.computeIfAbsent(root, k -> new int[so.length]);
      for (int i = 0; i < so.length; i++)
        r[i] += so[i];
    }
    long[] o = time.remove(other);
    if (o != null) {
      long[] r = time.computeIfAbsent(root, k -> new long[2]);
      r[0] += o[0];
      r[1] += o[1];
    }
  }

  void phrase(Phrases.Phrase p) {
    if (p.shape() == Phrases.Shape.CLICK)
      return;
    if (p.shape() != Phrases.Shape.MUSIC)
      shapes.computeIfAbsent(find(p.key()), k -> new int[Phrases.Shape.values().length])[p.shape().ordinal()]++;
    long[] t = time.computeIfAbsent(find(p.key()), k -> new long[2]);
    t[p.shape() == Phrases.Shape.MUSIC ? 0 : 1] += p.end() - p.start() + 1;
  }

  /** nine tenths of what the emitter played, by time, was music: its next phrase is music from its first note */
  public boolean mostlyMusic(int key) {
    long[] t = time.get(find(key));
    return t != null && t[0] > 0 && t[0] >= 9 * t[1];
  }

  /** the shape of most of the emitter's phrases, music aside; null if it played none but music and clicks */
  public Phrases.Shape commonest(int key) {
    int[] n = shapes.get(find(key));
    if (n == null)
      return null;
    int best = -1;
    for (int i = 0; i < n.length; i++)
      if (n[i] > 0 && (best < 0 || n[i] > n[best]))
        best = i;
    return best < 0 ? null : Phrases.Shape.values()[best];
  }

  /** what an emitter plays, by the time it spent on each */
  public Kind kind(int key) {
    long[] t = time.get(find(key));
    if (t == null)
      return Kind.UNKNOWN;
    return t[0] >= t[1] ? Kind.MUSIC : Kind.EFFECTS;
  }
}
