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
import com.fpetrola.oozx.rzx.RzxRecorder;
import com.fpetrola.oozx.rzx.RzxSession;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * What a recorder keeps of a machine being played, a session plays again to the same screen:
 * measured by playing a known recording, recording the machine it drives from some frame on, and
 * playing that recording on a second machine.
 */
class ARecordingOfAMachineReplaysTheSameTest {
  private static final int SKIPPED = 1000, RECORDED = 3000;
  private RzxSession played;

  private static File recording() {
    try {
      return model.harness.TestFiles.testFile("/rzx/jsw-full.rzx");
    } catch (Exception e) {
      throw new IllegalStateException("the jsw-full.rzx test resource is missing", e);
    }
  }

  @Test
  @org.junit.jupiter.api.Timeout(value = 10, unit = java.util.concurrent.TimeUnit.MINUTES)
  void the_recording_of_a_played_machine_replays_to_the_same_screen() throws Exception {
    played = RzxSession.open(recording()).quiet();
    for (int frame = 0; frame < SKIPPED; frame++) played.playFrame();
    RzxRecorder recorder = new RzxRecorder(played.getSpeccy(), answer -> {
      played.whenRead((port, value) -> answer.accept(value));
      return () -> { };
    });
    java.util.List<byte[]> seen = screensAsEachFrameBegins(played.getSpeccy());
    for (int frame = 0; frame < RECORDED; frame++) played.playFrame();
    assertReplaysTheSame(recorder, seen);
    played.release();
  }

  /** The window's way: a machine running free, recorded through its own port bus, replays to the same screens. */
  @Test
  @org.junit.jupiter.api.Timeout(value = 10, unit = java.util.concurrent.TimeUnit.MINUTES)
  void a_machine_running_free_recorded_through_its_ports_replays_to_the_same_screen() throws Exception {
    played = RzxSession.open(recording()).quiet();
    for (int frame = 0; frame < SKIPPED; frame++) played.playFrame();
    Speccy speccy = played.getSpeccy();
    speccy.speed.setEmulation(com.fpetrola.oozx.speccy.modules.timer.Speed.UNLIMITED);
    played.release();
    RzxRecorder recorder = RzxRecorder.of(speccy);
    java.util.List<byte[]> seen = screensAsEachFrameBegins(speccy);
    for (int frame = 0; frame < RECORDED; frame++) {
      long frames = speccy.machine.current.frameCount();
      while (speccy.machine.current.frameCount() == frames) {
        speccy.loop.doOpcodes();
        speccy.scheduler.runDue();
      }
    }
    // The fetch that begins the frame after the last one, which a machine left running makes by itself.
    speccy.cpu.step();
    assertReplaysTheSame(recorder, seen);
  }

  /** A session following the recorder catches up with the played machine and then waits for it, frame by frame. */
  @Test
  @org.junit.jupiter.api.Timeout(value = 10, unit = java.util.concurrent.TimeUnit.MINUTES)
  void a_session_following_the_recorder_catches_up_and_waits_for_it() throws Exception {
    played = RzxSession.open(recording()).quiet();
    for (int frame = 0; frame < SKIPPED; frame++) played.playFrame();
    RzxRecorder recorder = new RzxRecorder(played.getSpeccy(), answer -> {
      played.whenRead((port, value) -> answer.accept(value));
      return () -> { };
    });
    for (int frame = 0; frame < 200; frame++) played.playFrame();
    RzxSession twin = RzxSession.follow(recorder).quiet();
    java.util.List<byte[]> seen = new java.util.ArrayList<>();
    int caughtUp = 0, matching = 0, compared = 0;
    for (int frame = 0; frame < RECORDED; frame++) {
      played.playFrame();
      seen.add(screenOf(played.getSpeccy()));
      while (twin.playFrame()) {
        // The twin's frames count from the recorder's start, 200 frames before this loop's.
        int at = twin.getPlayback().getFrameIndex() - 200;
        if (at >= 0 && at < seen.size()) {
          compared++;
          if (java.util.Arrays.equals(screenOf(twin.getSpeccy()), seen.get(at))) matching++;
        }
      }
      caughtUp++;
    }
    recorder.stop();
    System.out.printf("twin: played %d frames, compared %d, matching %d%n", twin.getPlayback().getFrameIndex(), compared, matching);
    assertEquals(RECORDED, caughtUp, "times the twin had nothing left to play");
    assertEquals(RECORDED - 1, compared, "twin frames held against the played machine's");
    assertEquals(compared, matching, "twin frames whose screen is the played machine's");
    assertEquals(RECORDED + 200 - 2, twin.getPlayback().getFrameIndex(), "frames the twin played: all but the recorder's first and the one it cannot touch");
    played.release();
    twin.release();
  }

  /** Stops the recorder, plays its recording on another machine and holds the screens of every frame it begins against the ones seen. */
  private static void assertReplaysTheSame(RzxRecorder recorder, java.util.List<byte[]> seen) throws Exception {
    recorder.stop();
    Path copy = Files.createTempFile("recorded", ".rzx");
    copy.toFile().deleteOnExit();
    recorder.writeTo(copy);
    int kept = recorder.frames();
    RzxSession replayed = RzxSession.open(copy.toFile()).quiet();
    java.util.List<byte[]> replay = screensAsEachFrameBegins(replayed.getSpeccy());
    int frames = 0;
    while (replayed.playFrame()) frames++;
    int[] matching = new int[3], firstDiffering = {-1, -1, -1};
    for (int offset = 0; offset < 3; offset++) {
      for (int at = 0; at + offset < seen.size() && at < replay.size(); at++) {
        if (java.util.Arrays.equals(replay.get(at), seen.get(at + offset))) matching[offset]++;
        else if (firstDiffering[offset] < 0) firstDiffering[offset] = at;
      }
    }
    System.out.printf("recorded %d frames, the copy played %d and began %d; screens matching at offset 0: %d (first differing %d), offset 1: %d (first %d), offset 2: %d (first %d)%n",
        kept, frames, replay.size(), matching[0], firstDiffering[0], matching[1], firstDiffering[1], matching[2], firstDiffering[2]);
    assertEquals(RECORDED - 1, kept, "frames kept: all but the one the recorder started in");
    assertEquals(kept - 1, frames, "frames the copy plays");
    assertEquals(replay.size(), Math.max(matching[0], Math.max(matching[1], matching[2])), "frames the copy begins with the screen the played machine had");
    replayed.release();
  }

  /** The screen as each frame begins: at the first fetch after the machine counted a frame, which is where a recorder cuts its frames. */
  private static java.util.List<byte[]> screensAsEachFrameBegins(Speccy speccy) {
    java.util.List<byte[]> screens = new java.util.ArrayList<>();
    long[] frameSeen = {-1};
    speccy.cpu.beforeFetch().watch(0x0000, 0xffff, pc -> {
      long now = speccy.machine.current.frameCount();
      if (now == frameSeen[0]) return;
      frameSeen[0] = now;
      screens.add(screenOf(speccy));
    });
    return screens;
  }

  private static byte[] screenOf(Speccy speccy) {
    byte[] screen = new byte[6912];
    for (int offset = 0; offset < screen.length; offset++) screen[offset] = (byte) speccy.memory.peek(0x4000 + offset);
    return screen;
  }
}
