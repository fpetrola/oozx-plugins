package com.fpetrola.oozx.speccy.devices.bettersound;


import java.util.Optional;

/**
 * The two voices under a tune's melody (PLAN-SONIDO.md, D4; Fernando: "octava y bajo en todos, acordes donde hay
 * tonalidad"), note by note, as semitones below it. While the tune has no key -- too few notes heard, or none that a
 * key fits -- the melody doubled an octave and two below. Where it has one, a chord of the key: the one sounding as
 * long as the note is in it, else the first of the key's that holds it, a voice of it under the melody and its root
 * in the bass. Under another voice of the tune, which is a harmony already, only the octave.
 */
final class Harmony {
  /** no voice */
  static final int NONE = -1;
  /** a key is taken from this many notes on: the Moonlight Sonata's first 24 are its first eight bars */
  static final int MIN_NOTES = 24;
  /** the inner voice stays at least this far under the melody: a second against it is a clash, not a chord */
  private static final int INNER_GAP = 3;
  /** the bass is the root at or under this MIDI note (G3), and an octave at least under the melody */
  private static final int BASS_TOP = 55;
  private static final int[] DOUBLED = {12, 24};

  /** a triad: its root and the semitones of its third and fifth above it */
  private record Triad(int root, int third, int fifth) {
    boolean holds(int pitchClass) {
      int d = Math.floorMod(pitchClass - root, 12);
      return d == 0 || d == third || d == fifth;
    }
  }

  /** a key's triads, from the most to the least wanted, as degrees above the tonic */
  private static final Triad[] MAJOR = {new Triad(0, 4, 7), new Triad(7, 4, 7), new Triad(5, 4, 7),
      new Triad(9, 3, 7), new Triad(2, 3, 7), new Triad(4, 3, 7)};
  /** in minor the dominant is major, as the harmonic minor raises the seventh (the toccata's C sharp) */
  private static final Triad[] MINOR = {new Triad(0, 3, 7), new Triad(7, 4, 7), new Triad(5, 3, 7),
      new Triad(8, 4, 7), new Triad(3, 4, 7), new Triad(10, 4, 7)};

  private final double[] time = new double[12];
  private int notes;
  private Triad chord;

  /** the tune sounded at MIDI {@code midi} for {@code length} */
  void heard(int midi, double length) {
    time[Math.floorMod(midi, 12)] += length;
  }

  /**
   * The two voices under a note of the melody struck at MIDI {@code midi}, in semitones below it ({@link #NONE}:
   * none)
   */
  int[] under(int midi, boolean anotherVoice) {
    notes++;
    if (anotherVoice)
      return new int[]{12, NONE};
    Optional<Key> key = notes < MIN_NOTES ? Optional.empty() : Key.of(time);
    if (key.isEmpty())
      return DOUBLED.clone();
    Triad c = chordOf(key.get(), Math.floorMod(midi, 12));
    if (c == null)
      return DOUBLED.clone();
    chord = c;
    return new int[]{midi - inner(c, midi), midi - bass(c, midi)};
  }

  /** the chord sounding if it holds the note, else the first of the key's that does (absolute roots) */
  private Triad chordOf(Key key, int pitchClass) {
    if (chord != null && chord.holds(pitchClass))
      return chord;
    for (Triad t : key.minor() ? MINOR : MAJOR) {
      Triad a = new Triad(Math.floorMod(key.tonic() + t.root(), 12), t.third(), t.fifth());
      if (a.holds(pitchClass))
        return a;
    }
    return null;
  }

  /** the highest note of the chord at least INNER_GAP under the melody, and not the melody's own pitch class */
  private static int inner(Triad c, int midi) {
    int m = midi - INNER_GAP;
    while (!c.holds(Math.floorMod(m, 12)) || Math.floorMod(m - midi, 12) == 0)
      m--;
    return m;
  }

  /** the chord's root an octave at least under the melody, at or under BASS_TOP */
  private static int bass(Triad c, int midi) {
    int b = midi - 12;
    while (Math.floorMod(b - c.root(), 12) != 0)
      b--;
    while (b > BASS_TOP)
      b -= 12;
    return b;
  }
}
