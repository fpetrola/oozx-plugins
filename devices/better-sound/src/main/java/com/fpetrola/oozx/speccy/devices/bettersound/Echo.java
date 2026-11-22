package com.fpetrola.oozx.speccy.devices.bettersound;

/**
 * An echo on stereo samples (PLAN-SONIDO.md, S5): what came in {@code delay} ms ago comes back, {@code wet} of it
 * heard, and {@code feedback} of it goes round again. The room's: a bigger room, a longer and fuller one.
 */
public final class Echo {
  private final int rate;
  private final int[][] ring;
  private int at;

  public Echo(int rate, int longestMs) {
    this.rate = rate;
    int n = Math.max(1, rate * longestMs / 1000);
    ring = new int[][]{new int[n], new int[n]};
  }

  /** the first {@code samples} stereo samples of {@code stereo}, in place */
  public void process(int[] stereo, int samples, double delayMs, double feedback, double wet) {
    int n = ring[0].length, d = (int) Math.max(1, Math.min(n - 1, Math.round(delayMs * rate / 1000)));
    for (int i = 0; i < samples; i++, at = at + 1 == n ? 0 : at + 1) {
      int from = at - d < 0 ? at - d + n : at - d;
      for (int c = 0; c < 2; c++) {
        int in = stereo[2 * i + c], back = ring[c][from];
        stereo[2 * i + c] = (int) Math.round(in + wet * back);
        ring[c][at] = (int) Math.round(in + feedback * back);
      }
    }
  }
}
