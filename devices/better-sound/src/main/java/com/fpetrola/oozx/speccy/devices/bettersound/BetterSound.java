/*
 * Copyright (c) 2023-2026 Fernando Damian Petrola
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package com.fpetrola.oozx.speccy.devices.bettersound;

import com.fpetrola.oozx.speccy.devices.ula.Beeper;
import com.fpetrola.oozx.speccy.modules.memory.MemoryBus;
import com.fpetrola.oozx.speccy.modules.sound.AudioOutput;
import com.fpetrola.oozx.speccy.modules.sound.AudioSource;
import com.fpetrola.oozx.speccy.modules.sound.Sound;
import com.fpetrola.oozx.speccy.modules.z80.Processors;
import com.fpetrola.oozx.speccy.screen.Knob;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * The machine's speaker, heard and played again: every write to the ULA's port is kept with the instruction that made
 * it and where that routine returns to, the AY takes the music and the effects a frame later, once it knows them, and
 * a beeper of its own plays what it left. A frame of the speaker is played one frame late, as the viewer did.
 */
final class BetterSound implements AudioSource, Processors.WriteListener {
  private final MemoryBus memory;
  private final AyStage ay;
  private final RoomFx room = new RoomFx();
  private final FrameSound[] frames = {new FrameSound(), new FrameSound()};
  private long recording;
  private Beeper beeper;
  private int[] mix;
  private int made;

  BetterSound(AudioOutput output, MemoryBus memory) {
    this.memory = memory;
    ay = new AyStage(output);
    frames[0].reset(0);
    takeOutputFrom(output);
  }

  List<Knob> knobs() {
    List<Knob> knobs = new ArrayList<>(ay.knobs());
    knobs.addAll(room.knobs());
    return knobs;
  }

  /** how many notes of music the AY started, for the tests */
  int musicNotes() {
    return ay.musicNotes();
  }

  @Override
  public void written(long tstates, int port, int value, int pc, int sp) {
    if ((port & 1) == 0)
      frames[(int) (recording & 1)].add((int) tstates, value & 0xff, pc, memory.peek(sp) | memory.peek(sp + 1) << 8);
  }

  @Override
  public void takeOutputFrom(AudioOutput output) {
    beeper = new Beeper(output, () -> false, new Sound.Output());
    ay.takeOutputFrom(output);
    mix = new int[2 * output.frameSize()];
  }

  /** The frame that ended is heard, which decides the one before, and that one is played. */
  @Override
  public void endFrame(int frameTstates) {
    FrameSound now = frames[(int) (recording & 1)];
    now.close(frameTstates);
    ay.hear(now);
    made = recording > 0 ? play(frames[(int) ((recording - 1) & 1)]) : 0;
    frames[(int) (++recording & 1)].reset(recording);
  }

  private int play(FrameSound frame) {
    int last = frame.length() - 1;
    for (int i = 0; i < frame.size(); i++)
      if (!frame.claimed(i)) {
        int value = frame.value(i);
        beeper.write(Math.min(last, frame.t(i)), ((value & 0x10) != 0 ? 2 : 0) + ((value & 0x08) == 0 ? 1 : 0), false);
      }
    beeper.endFrame(frame.length());
    Arrays.fill(mix, 0);
    int samples = beeper.mixInto(mix, mix.length / 2);
    samples = Math.max(samples, ay.mixInto(frame, mix, samples));
    room.shape(mix, samples);
    return samples;
  }

  @Override
  public int mixInto(int[] samples, int frames) {
    int count = Math.min(made, frames);
    for (int i = 0; i < 2 * count; i++)
      samples[i] += mix[i];
    return count;
  }

  @Override
  public void close() {
    beeper.close();
  }
}
