package com.fpetrola.oozx.speccy.devices.bettersound;

/** A one-pole low-pass on stereo samples (PLAN-SONIDO.md, S5): what is above the cutoff, softened. */
public final class OnePole {
  private final int rate;
  private double left, right;

  public OnePole(int rate) {
    this.rate = rate;
  }

  /** the first {@code samples} stereo samples of {@code stereo}, in place */
  public void process(int[] stereo, int samples, double cutoffHz) {
    double a = 1 - Math.exp(-2 * Math.PI * cutoffHz / rate);
    for (int i = 0; i < samples; i++) {
      left += a * (stereo[2 * i] - left);
      right += a * (stereo[2 * i + 1] - right);
      stereo[2 * i] = (int) Math.round(left);
      stereo[2 * i + 1] = (int) Math.round(right);
    }
  }
}
