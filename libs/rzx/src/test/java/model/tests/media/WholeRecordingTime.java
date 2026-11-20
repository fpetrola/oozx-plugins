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

package model.tests.media;

import com.fpetrola.oozx.Speccy;
import com.fpetrola.oozx.rzx.RzxSession;
import com.fpetrola.z80.minizx.RzxPlayback;
import model.harness.MachineTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.util.concurrent.TimeUnit;

/**
 * A whole recording, timed. The work is fixed by construction - the same 85368 frames and the same
 * 355229553 instructions every time - so the seconds are the emulator and nothing else.
 * <p>
 * The other measurement here, InstructionsPerSecond, runs a loop of its own and is steady to half a
 * percent; it is steady because it touches none of what a game touches - no contention, no ports,
 * no interrupt, no screen - and it therefore cannot see what this one sees. Use that one to ask
 * whether a change cost anything, and this one to ask how long something actually takes.
 */
@EnabledIfSystemProperty(named = "oozx.measure", matches = "true")
@Timeout(value = 20, unit = TimeUnit.MINUTES)
class WholeRecordingTime extends MachineTest {
  @Test
  void theWholeThing() throws Exception {
    dev.plugdesk.app.Configuration.shared().setValue("machine", "processor", "Generated");
    for (int round = 1; round <= Integer.getInteger("oozx.rounds", 1); round++) {
      RzxSession session = RzxSession.open(System.getProperty("oozx.rzx") != null ? new java.io.File(System.getProperty("oozx.rzx")) : model.harness.TestFiles.testFile("/rzx/jsw-full.rzx"));
      session.getSpeccy().picture.active = Boolean.getBoolean("oozx.picture");
      session.getSpeccy().sound.output.enabled = Boolean.getBoolean("oozx.sound");
      session.getSpeccy().sound.setCard(new com.fpetrola.oozx.speccy.modules.sound.SilentSoundDevice());
      RzxPlayback playback = session.getPlayback();
      long start = System.nanoTime();
      int frames = 0;
      while (session.playFrame()) frames++;
      double seconds = (System.nanoTime() - start) / 1e9;
      System.out.printf("TOTAL round %d: %d frames, %d instrucciones, %.2f s, %.1f MIPS%n",
          round, frames, playback.getInstructions(), seconds, playback.getInstructions() / seconds / 1e6);
      session.release();
    }
    dev.plugdesk.app.Configuration.shared().setValue("machine", "processor", null);
  }

  /** The same moment of the game, driven by the recording and then by the machine's own loop: what the player costs. */
  @Test
  void theRecordingAgainstTheMachineAlone() throws Exception {
    dev.plugdesk.app.Configuration.shared().setValue("machine", "processor", "Generated");
    for (int round = 1; round <= Integer.getInteger("oozx.rounds", 1); round++) {
      RzxSession session = RzxSession.open(new java.io.File(System.getProperty("oozx.rzx")));
      Speccy speccy = session.getSpeccy();
      speccy.picture.active = false;
      speccy.sound.output.enabled = false;
      speccy.sound.setCard(new com.fpetrola.oozx.speccy.modules.sound.SilentSoundDevice());
      RzxPlayback playback = session.getPlayback();
      for (int frame = 0; frame < 10000; frame++) session.playFrame();
      long instructions = playback.getInstructions(), start = System.nanoTime();
      for (int frame = 0; frame < 10000; frame++) session.playFrame();
      double played = (System.nanoTime() - start) / 1e9;
      instructions = playback.getInstructions() - instructions;
      session.release();
      speccy.speed.setEmulation(com.fpetrola.oozx.speccy.modules.timer.Speed.UNLIMITED);
      step(speccy);
      long frames = speccy.machine.current.frameCount(), tstates = 0, steps = 0;
      start = System.nanoTime();
      while (System.nanoTime() - start < played * 1e9) {
        int r = speccy.cpu.getOoz80().getState().getRegister(com.fpetrola.z80.registers.RegisterName.R).read();
        step(speccy);
        steps += (speccy.cpu.getOoz80().getState().getRegister(com.fpetrola.z80.registers.RegisterName.R).read() - r) & 0x7F;
        tstates++;
      }
      System.out.printf("ALONE round %d: played 10000 frames in %.2f s, %.0f instr/frame | own loop in the same time: %d doOpcodes calls, %d frames, R advanced %d (mod 128 per call)%n",
          round, played, instructions / 10000.0, tstates, speccy.machine.current.frameCount() - frames, steps);
    }
    dev.plugdesk.app.Configuration.shared().setValue("machine", "processor", null);
  }
}
