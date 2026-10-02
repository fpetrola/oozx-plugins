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

package com.fpetrola.oozx.rzx;

import com.fpetrola.oozx.Speccy;
import com.fpetrola.oozx.speccy.modules.snapshot.Snapshots;
import com.fpetrola.oozx.speccy.modules.z80.PcTraps;
import com.fpetrola.z80.ide.rzx.CreatorInfo;
import com.fpetrola.z80.ide.rzx.InputRecordingBlock;
import com.fpetrola.z80.ide.rzx.RzxFile;
import com.fpetrola.z80.ide.rzx.RzxHeader;
import com.fpetrola.z80.ide.rzx.RzxWriter;
import com.fpetrola.z80.ide.rzx.SnapshotBlock;
import com.fpetrola.z80.registers.Register;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.IntConsumer;

/**
 * A machine being played, kept from a moment on so that a {@link RzxSession} can play the same
 * again: the machine as it was at the first frame after this was asked for, and for every frame
 * since, how many fetches it ran and what each port read answered, in order.
 * <p>
 * Counted the way the player counts: fetches are the R register's steps, two at most for one
 * instruction, and the one an interrupt's acknowledge adds is nobody's, since the player skips it
 * when it accepts the interrupt it raises at a frame's end. A frame ends where the machine ends
 * it, which the player will do by the count.
 */
public final class RzxRecorder {

  /** Where the port reads' answers come from: the machine's bus when it is played, the recording when it is replayed. */
  public interface Reads {
    Runnable listen(IntConsumer answer);
  }

  private static final String SNAPSHOT = "z80";

  private final Speccy speccy;
  private final Register registerR;
  private final List<InputRecordingBlock.Frame> frames = java.util.Collections.synchronizedList(new ArrayList<>());
  private final ByteArrayOutputStream answers = new ByteArrayOutputStream();
  private final PcTraps.Watch fetching, executed;
  private final Runnable taken, stopListening;
  private byte[] snapshot;
  private int fetches, previousR;
  private boolean acknowledged, started;
  private long frameSeen;

  /** Recording the machine's own ports from here on. */
  public static RzxRecorder of(Speccy speccy) {
    return new RzxRecorder(speccy, answer -> {
      com.fpetrola.oozx.speccy.modules.ports.MachinePortBus.ReadListener listener = (port, value) -> answer.accept(value);
      speccy.ports.whenRead(listener);
      return () -> speccy.ports.stopTellingAboutReads(listener);
    });
  }

  public RzxRecorder(Speccy speccy, Reads reads) {
    this.speccy = speccy;
    this.registerR = speccy.cpu.getOoz80().getState().getRegisterR();
    this.frameSeen = speccy.machine.current.frameCount();
    this.stopListening = reads.listen(value -> { if (started) answers.write(value & 0xff); });
    this.taken = () -> acknowledged = true;
    speccy.cpu.whenAnInterruptIsTaken(taken);
    this.fetching = speccy.cpu.beforeFetch().watch(0x0000, 0xffff, this::aboutToFetch);
    this.executed = speccy.cpu.afterInstruction().watch(0x0000, 0xffff, this::executed);
  }

  /** The first fetch of a frame: the one before is over, or, the first time, this is where the recording starts. */
  private void aboutToFetch(int pc) {
    long now = speccy.machine.current.frameCount();
    if (now == frameSeen) return;
    frameSeen = now;
    if (started) {
      InputRecordingBlock.Frame frame = new InputRecordingBlock.Frame();
      frame.fetchCounter = fetches;
      frame.returnValues = answers.toByteArray();
      frame.inCounter = frame.returnValues.length;
      frames.add(frame);
    } else {
      snapshot = snapshotNow();
      started = true;
    }
    fetches = 0;
    answers.reset();
    previousR = registerR.read() & 0x7F;
    acknowledged = false;
  }

  private void executed(int pc) {
    if (!started) return;
    int now = registerR.read() & 0x7F;
    int stepped = (now - previousR) & 0x7F;
    previousR = now;
    if (acknowledged) {
      acknowledged = false;
      stepped = Math.max(0, stepped - 1);
    }
    fetches += stepped > 2 ? 2 : stepped;
  }

  private byte[] snapshotNow() {
    try {
      Path file = Files.createTempFile("rzx-recorder", "." + SNAPSHOT);
      file.toFile().deleteOnExit();
      Snapshots.of(speccy).save(file.toAbsolutePath().toString());
      return Files.readAllBytes(file);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  /** Whether the first frame has begun, so there is something to play. */
  public boolean started() {
    return started;
  }

  public int frames() {
    return frames.size();
  }

  /** Whether that frame has been recorded, so a session following this can play up to it. */
  public boolean hasFrame(int index) {
    return index < frames.size();
  }

  /**
   * The recording as it stands and as it grows: the frames are this recorder's own, so a session
   * playing it finds the frames recorded after it started.
   */
  public RzxFile recording() {
    if (!started) throw new IllegalStateException("nothing recorded yet");
    RzxHeader header = new RzxHeader();
    header.signature = "RZX!";
    SnapshotBlock block = new SnapshotBlock();
    block.setSnapshotExtension(SNAPSHOT);
    block.setSnapshotData(snapshot);
    block.setUncompressedLength(snapshot.length);
    InputRecordingBlock input = new InputRecordingBlock();
    input.frames = frames;
    return new RzxFile(header, new CreatorInfo(), block, input);
  }

  /** The recording so far, as a file a session opens. */
  public void writeTo(Path out) throws IOException {
    if (!started) throw new IllegalStateException("nothing recorded yet");
    List<InputRecordingBlock.Frame> copy;
    synchronized (frames) {
      copy = new ArrayList<>(frames);
    }
    RzxWriter.write(snapshot, SNAPSHOT, copy, out);
  }

  public void stop() {
    fetching.off();
    executed.off();
    speccy.cpu.stopTellingAboutInterrupts(taken);
    stopListening.run();
  }
}
