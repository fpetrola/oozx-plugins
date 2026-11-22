package com.fpetrola.oozx.speccy.devices.bettersound;

import com.fpetrola.oozx.speccy.screen.Knob;

import java.util.List;


/**
 * The room on the whole mix (PLAN-SONIDO.md, S5): an echo whose delay and feedback grow with how open the room is --
 * a hall full of air rings longer than a cell between walls -- and, if asked, a low-pass that softens the edge of the
 * squares. Off unless asked ({@code sound.fx.echo} and {@code sound.fx.lowpass} at 0): the AY is heard pure (D3).
 */
public final class RoomFx {
  private static final int RATE = 44100;
  /** how open the room is, from 0 (all walls) to 1 (all air): a machine has no room, so the middle */
  private static final float OPENNESS = 0.5f;
  private volatile float wet, cutoff;
  private final Echo echo = new Echo(RATE, 400);
  private final OnePole lowpass = new OnePole(RATE);

  List<Knob> knobs() {
    return List.of(
        AyStage.level("echo", "Echo", "How much of the sound comes back (0 = no echo)", "Room", 0, 1, 0.05, 0, () -> wet,
            v -> wet = v),
        AyStage.level("lowpass", "Low-pass", "Hz above which the squares are softened (0 = no filter)", "Room", 0, 16000, 250, 0,
            () -> cutoff, v -> cutoff = v));
  }

  /** reshapes the mix already made: echo, filters */
  void shape(int[] stereo, int samples) {
    float w = wet, c = cutoff;
    if (w > 0)
      echo.process(stereo, samples, 60 + 140 * OPENNESS, 0.2 + 0.3 * OPENNESS, w);
    if (c > 0)
      lowpass.process(stereo, samples, c);
  }
}
