package com.fpetrola.oozx.speccy.devices.bettersound;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Two voices on one speaker (PLAN-SONIDO.md, S2): a two-channel beeper engine toggles the speaker every half period
 * of each voice, so its edges are the union of two square waves, and no run of {@link Runs} holds for long. Given
 * the edges of such a stretch, this finds the two half periods that most edges have a partner at -- an edge of a
 * voice is followed, or preceded, by another of the same voice one half period away -- and says which voice each
 * edge is. What it cannot explain that way, it does not split: noise stays noise.
 */
final class Voices {
  /** how many of the edges the two voices must explain */
  private static final double EXPLAINED = 0.85;
  /** how many edges each voice must have, of the stretch's */
  private static final double EACH = 0.2;

  private Voices() {
  }

  /**
   * The voice of each of the {@code n} edges in {@code t} (0 the voice of the shorter half period, 1 the other, -1
   * neither) and the two half periods, or null if two voices do not explain them.
   */
  static Split split(long[] t, int n) {
    if (n < 32)
      return null;
    Map<Integer, Integer> votes = new HashMap<>();
    for (int i = 0; i < n; i++)
      for (int j = i + 1; j < Math.min(n, i + 7); j++) {
        long d = t[j] - t[i];
        if (d > Runs.LONGEST_HALF)
          break;
        if (d > 0)
          votes.merge(bin(d), 1, Integer::sum);
      }
    // the candidates: the ten lags with the most votes; the voices are the two that explain the most edges
    List<Integer> candidates = new ArrayList<>();
    Map<Integer, Integer> left = new HashMap<>(votes);
    for (int found = 0; found < 10 && !left.isEmpty(); found++) {
      int top = best(left, Integer.MIN_VALUE);
      if (top == Integer.MIN_VALUE)
        break;
      // and the half and the third of it: a voice's edge has a partner two half periods away as well as one, so the
      // double explains as many edges, and of two that explain as many the shorter is the voice
      for (int m = 1; m <= 3; m++) {
        int f = top - (int) Math.round(Math.log(m) * 200);
        if (candidates.stream().noneMatch(c -> Math.abs(c - f) <= 2))
          candidates.add(f);
      }
      for (int k = -2; k <= 2; k++)
        left.remove(top + k);
    }
    candidates.sort(null);
    int[] voice = null, count = null;
    double ha = 0, hb = 0;
    int explained = -1;
    for (int x = 0; x < candidates.size(); x++)
      for (int y = x + 1; y < candidates.size(); y++) {
        int a = candidates.get(x), b = candidates.get(y);
        if (related(a, b))
          continue;
        double h1 = Math.exp(Math.min(a, b) / 200.0), h2 = Math.exp(Math.max(a, b) / 200.0);
        int[] v = new int[n], c = new int[2];
        assign(t, n, h1, h2, v, c);
        if (c[0] + c[1] > explained && c[0] >= EACH * n && c[1] >= EACH * n) {
          explained = c[0] + c[1];
          voice = v;
          count = c;
          ha = h1;
          hb = h2;
        }
      }
    if (voice == null)
      return null;
    // each voice is a grid, t = phase + k h: fitted on the edges the partners gave it, it says exactly which edge is
    // whose, where two edges of the two voices fall close and a partner could be either's
    for (int round = 0; round < 2; round++) {
      double[] pa = grid(t, n, voice, 0, ha), pb = grid(t, n, voice, 1, hb);
      if (pa == null || pb == null)
        break;
      ha = pa[1];
      hb = pb[1];
      count = new int[2];
      for (int i = 0; i < n; i++) {
        double da = off(t[i], pa), db = off(t[i], pb);
        voice[i] = da <= tolerance(ha) && da / ha <= db / hb ? 0 : db <= tolerance(hb) ? 1 : -1;
        if (voice[i] >= 0)
          count[voice[i]]++;
      }
    }
    if (count[0] + count[1] < EXPLAINED * n || count[0] < EACH * n || count[1] < EACH * n)
      return null;
    // and both at once: a tune that changes note inside the stretch is two half periods one after the other, each
    // edge with its partner, and one voice
    long[] first = {Long.MAX_VALUE, Long.MAX_VALUE}, last = {Long.MIN_VALUE, Long.MIN_VALUE};
    for (int i = 0; i < n; i++)
      if (voice[i] >= 0) {
        first[voice[i]] = Math.min(first[voice[i]], t[i]);
        last[voice[i]] = Math.max(last[voice[i]], t[i]);
      }
    long together = Math.min(last[0], last[1]) - Math.max(first[0], first[1]);
    if (together < 0.5 * (t[n - 1] - t[0]))
      return null;
    return new Split(voice, ha, hb);
  }

  private static void assign(long[] t, int n, double ha, double hb, int[] voice, int[] count) {
    for (int i = 0; i < n; i++) {
      double ea = partner(t, n, i, ha), eb = partner(t, n, i, hb);
      voice[i] = ea <= tolerance(ha) && (ea / ha <= eb / hb || eb > tolerance(hb)) ? 0
          : eb <= tolerance(hb) ? 1 : -1;
      if (voice[i] >= 0)
        count[voice[i]]++;
    }
  }

  /** the phase and the half period of voice {@code v}'s edges, by least squares on their index in the grid */
  private static double[] grid(long[] t, int n, int[] voice, int v, double h) {
    long t0 = Long.MIN_VALUE;
    double sk = 0, st = 0, skk = 0, skt = 0;
    int m = 0;
    for (int i = 0; i < n; i++) {
      if (voice[i] != v)
        continue;
      if (t0 == Long.MIN_VALUE)
        t0 = t[i];
      double k = Math.round((t[i] - t0) / h), y = t[i] - t0;
      sk += k;
      st += y;
      skk += k * k;
      skt += k * y;
      m++;
    }
    double den = m * skk - sk * sk;
    if (m < 3 || den == 0)
      return null;
    double slope = (m * skt - sk * st) / den, phase = (st - slope * sk) / m;
    return new double[]{t0 + phase, slope};
  }

  /** how far an edge is from the nearest point of a grid {phase, half period} */
  private static double off(long t, double[] grid) {
    double k = Math.round((t - grid[0]) / grid[1]);
    return Math.abs(t - (grid[0] + k * grid[1]));
  }

  record Split(int[] voice, double halfA, double halfB) {
  }

  private static int bin(long d) {
    return (int) Math.round(Math.log(d) * 200); // half a per cent wide
  }

  /** the bin with the most votes (with its two neighbours), that is not a multiple or a fraction of {@code other} */
  private static int best(Map<Integer, Integer> votes, int other) {
    int best = Integer.MIN_VALUE, most = 0;
    for (int bin : votes.keySet()) {
      if (other != Integer.MIN_VALUE && related(bin, other))
        continue;
      int v = votes.get(bin) + votes.getOrDefault(bin - 1, 0) + votes.getOrDefault(bin + 1, 0);
      if (v > most) {
        most = v;
        best = bin;
      }
    }
    return best;
  }

  private static boolean related(int bin, int other) {
    double r = Math.exp((bin - other) / 200.0);
    for (int m = 1; m <= 6; m++)
      if (Math.abs(r - m) < 0.03 * m || Math.abs(1 / r - m) < 0.03 * m)
        return true;
    return false;
  }

  private static double tolerance(double h) {
    return Math.max(4, 0.015 * h);
  }

  /** how far the edge {@code i} is from having another edge {@code h} before or after it */
  private static double partner(long[] t, int n, int i, double h) {
    double best = Double.MAX_VALUE;
    for (double target : new double[]{t[i] + h, t[i] - h}) {
      int k = Arrays.binarySearch(t, 0, n, Math.round(target));
      int at = k >= 0 ? k : -k - 1;
      for (int j = Math.max(0, at - 1); j <= Math.min(n - 1, at); j++)
        if (j != i)
          best = Math.min(best, Math.abs(t[j] - target));
    }
    return best;
  }
}
