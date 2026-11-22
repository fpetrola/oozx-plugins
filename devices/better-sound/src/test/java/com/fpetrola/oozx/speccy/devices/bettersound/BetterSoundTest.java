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

import com.fpetrola.oozx.Speccy;
import com.fpetrola.oozx.speccy.modules.sound.SilentSoundDevice;
import com.fpetrola.oozx.speccy.modules.sound.SoundCard;
import com.fpetrola.oozx.speccy.modules.z80.SpectrumZ80Clock;
import com.fpetrola.z80.registers.RegisterName;
import model.harness.MachineTest;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A tune played on the beeper, as a game plays it, heard through the AY on a running machine. */
class BetterSoundTest {
  private static final int PROGRAM = 0x8000, TOGGLE = 0x8100, TABLE = 0x8200, LEVEL = 0x8300;
  /**
   * DI; LD SP,0xC000; loop: LD HL,TABLE; next: LD A,(HL); OR A; JR Z,loop; LD C,A; INC HL; LD B,60;
   * note: CALL TOGGLE; DJNZ note; JR next
   */
  private static final int[] PLAYING = {0xF3, 0x31, 0x00, 0xC0, 0x21, 0x00, 0x82, 0x7E, 0xB7, 0x28, 0xF9, 0x4F, 0x23,
      0x06, 60, 0xCD, 0x00, 0x81, 0x10, 0xFB, 0x18, 0xF0};
  /** LD A,(LEVEL); XOR 0x10; LD (LEVEL),A; OUT (0xFE),A; LD D,C; wait: DEC D; JR NZ,wait; RET */
  private static final int[] TOGGLING = {0x3A, 0x00, 0x83, 0xEE, 0x10, 0x32, 0x00, 0x83, 0xD3, 0xFE, 0x51, 0x15, 0x20,
      0xFD, 0xC9};
  /** half periods of a tune that goes up and down among three pitches and comes back, as a game's does */
  private static final int[] TUNE = {100, 80, 67, 80, 100, 80, 67, 80, 0};

  private static class Loudest extends SilentSoundDevice {
    int peak;

    public void play(int[] data, int length) {
      for (int i = 0; i < length; i++)
        peak = Math.max(peak, Math.abs(data[i]));
    }
  }

  @Test
  void aTuneOnTheBeeperIsPlayedAsMusicAndTheBeeperComesBackWhenItIsTakenOut() {
    Loudest card = new Loudest();
    Speccy speccy = Speccy.create(new SpectrumZ80Clock(), binder -> binder.bind(SoundCard.class).toInstance(card));
    speccy.init();
    speccy.picture.active = false;
    speccy.sound.output.enabled = true;
    speccy.machine.selectDefault();
    poke(speccy, PROGRAM, PLAYING);
    poke(speccy, TOGGLE, TOGGLING);
    poke(speccy, TABLE, TUNE);
    speccy.cpu.getOoz80().getState().getRegister(RegisterName.PC).write(PROGRAM);

    BetterSoundPeripheral better = (BetterSoundPeripheral) speccy.peripheralRegistry.find(BetterSoundPeripheral.class);
    plug(speccy, better, true);
    MachineTest.runFrames(speccy, 150);
    assertTrue(better.musicNotes() > 0, "the tune was not played as music");
    assertTrue(card.peak > 0, "plugged in, the speaker made no sound");

    plug(speccy, better, false);
    card.peak = 0;
    int notes = better.musicNotes();
    MachineTest.runFrames(speccy, 20);
    assertTrue(card.peak > 0, "taken out, the machine's own beeper did not come back");
    assertEquals(notes, better.musicNotes(), "taken out, it still heard the speaker");
  }

  /**
   * Equinox's menu tune: two OUTs take turns every 48 T-states, each writing its own voice, so the speaker shakes too
   * fast to hear while the voices differ. Heard edge by edge it was one steady 36 kHz tone and none of it music; it is
   * a tune and a bass, the bass lower than any measured game played (95 Hz).
   */
  @Test
  void twoOutsTakingTurnsAreTwoVoicesOfMusic() {
    List<Phrases.Shape> shapes = new ArrayList<>();
    Ear ear = new Ear(p -> shapes.add(p.shape()));
    int[] tune = {3000, 2400, 2000, 2400, 3000, 2400, 2000, 2400}, bass = {9000, 18400, 12000, 18400, 9000, 18400, 12000, 18400};
    long frameEnd = 69_888;
    for (long t = 0; t < tune.length * 10 * 69_888L; t += 96) {
      int note = (int) (t / (10 * 69_888L));
      ear.out(t, (int) (t / tune[note] & 1) << 4, 0xFB37, 0xF5AC);
      ear.out(t + 48, (int) ((t + 48) / bass[note] & 1) << 4, 0xFB55, 0xF5AC);
      if (t >= frameEnd) {
        ear.frame(frameEnd);
        frameEnd += 69_888;
      }
    }
    ear.flush();
    // before both OUTs wrote both levels they are not voices yet: that start is a blip of the beeper's
    assertEquals(2, shapes.stream().filter(x -> x == Phrases.Shape.MUSIC).count());
  }

  /**
   * Ping Pong's tune: one pulse a period, a hundredth of it wide and widening as the note goes on, made by two OUTs
   * that always write the same, one up and one down. Each pulse was two sounds, and its pitch 5 % sharp once it was
   * one: it ended on the last pulse's fall.
   */
  @Test
  void aTuneOfNarrowPulsesIsMusicAtItsPitch() {
    List<Phrases.Phrase> phrases = new ArrayList<>();
    Ear ear = new Ear(phrases::add);
    int[] tune = {14741, 9856, 11675, 14741, 11675, 14741, 9856, 7354};
    long t = 0, frameEnd = 69_888;
    for (int period : tune)
      for (int width = 69; width < 69 + 52 * 12; width += 52, t += period) {
        ear.out(t, 0x10, 0xA1D2, -1);
        ear.out(t + width, 0, 0xA1E0, -1);
        for (; frameEnd <= t; frameEnd += 69_888)
          ear.frame(frameEnd);
      }
    ear.flush();
    Phrases.Phrase music = phrases.stream().filter(p -> p.shape() == Phrases.Shape.MUSIC).findFirst().orElseThrow();
    for (int i = 0; i < tune.length; i++)
      assertEquals(tune[i], music.notes().get(i).period(), tune[i] * 0.01, "note " + i);
  }

  /** As its window does it: plugged in or out, and the machine told to bring its devices up to date. */
  private static void plug(Speccy speccy, BetterSoundPeripheral better, boolean in) {
    better.plugIn(in);
    speccy.peripheralRegistry.update();
    assertEquals(in, speccy.peripheralRegistry.isActive(BetterSoundPeripheral.class));
  }

  private static void poke(Speccy speccy, int at, int[] bytes) {
    for (int i = 0; i < bytes.length; i++)
      speccy.memory.poke(at + i, (byte) bytes[i]);
  }
}
