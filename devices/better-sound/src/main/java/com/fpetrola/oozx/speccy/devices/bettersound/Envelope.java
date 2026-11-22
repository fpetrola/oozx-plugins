package com.fpetrola.oozx.speccy.devices.bettersound;

/**
 * The envelope of a note, in milliseconds (PLAN-SONIDO.md, S3): it rises for the attack, falls to the sustain for the
 * decay, holds there, and once released falls to nothing for the release, from wherever it was. What takes the flat
 * square of the beeper out of the AY: its sixteen volumes, written a few times a frame.
 */
final class Envelope {
  private final double attack, decay, sustain, release;

  Envelope(double attack, double decay, double sustain, double release) {
    this.attack = attack;
    this.decay = decay;
    this.sustain = sustain;
    this.release = release;
  }

  /** the level, 0 to 1, {@code sinceOn} ms after the note began and {@code sinceOff} after it was released (-1: not) */
  double level(double sinceOn, double sinceOff) {
    if (sinceOff < 0)
      return held(sinceOn);
    double from = held(sinceOn - sinceOff);
    return release <= 0 || sinceOff >= release ? 0 : from * (1 - sinceOff / release);
  }

  /** released and fallen to nothing */
  boolean over(double sinceOn, double sinceOff) {
    return sinceOff >= 0 && level(sinceOn, sinceOff) <= 0;
  }

  private double held(double t) {
    if (t < attack)
      return t / attack;
    if (t < attack + decay)
      return 1 - (1 - sustain) * (t - attack) / decay;
    return sustain;
  }
}
