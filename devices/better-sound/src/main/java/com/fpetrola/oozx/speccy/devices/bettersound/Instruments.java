package com.fpetrola.oozx.speccy.devices.bettersound;


/**
 * The instrument of each shape of effect (PLAN-SONIDO.md, S4): how loud, its envelope, and how long a silence it
 * holds across -- an effect the beeper plays a burst a frame (JSW's jump, its fall) is held between bursts, so on the
 * AY it is one sound that moves, not a stutter. The pitch is never the instrument's: it is what the speaker played.
 */
final class Instruments {
  /** volume as a share of the effects' volume, then the envelope in ms and the sustain from 0 to 1, and the hold */
  record Instrument(double loudness, double attack, double decay, double sustain, double release, double hold) {
    Envelope envelope() {
      return new Envelope(attack, decay, sustain, release);
    }
  }

  static final Instrument ARC = new Instrument(1, 1, 60, 0.6, 40, 80);
  static final Instrument SWEEP = new Instrument(1, 1, 90, 0.7, 50, 80);
  static final Instrument BLIP = new Instrument(1, 1, 30, 0, 15, 0);
  static final Instrument ANY = new Instrument(0.9, 1, 60, 0.5, 40, 50);

  /** the instrument of an effect, by the shape its routine plays most (null: nothing known yet) */
  static Instrument of(Phrases.Shape shape) {
    if (shape == null)
      return ANY;
    return switch (shape) {
      case ARC -> ARC;
      case SWEEP_UP, SWEEP_DOWN -> SWEEP;
      case BLIP -> BLIP;
      default -> ANY;
    };
  }

  private Instruments() {
  }
}
