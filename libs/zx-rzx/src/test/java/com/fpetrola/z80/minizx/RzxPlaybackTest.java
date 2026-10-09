package com.fpetrola.z80.minizx;

import com.fpetrola.z80.cpu.DefaultInstructionExecutor;
import com.fpetrola.z80.cpu.OOZ80;
import com.fpetrola.z80.cpu.State;
import com.fpetrola.z80.ide.rzx.InputRecordingBlock;
import com.fpetrola.z80.ide.rzx.RzxFile;
import com.fpetrola.z80.instructions.factory.DefaultInstructionFactory;
import com.fpetrola.z80.minizx.emulation.MockedMemory;
import com.fpetrola.z80.registers.DefaultRegisterBankFactory;
import com.fpetrola.z80.spy.NullInstructionSpy;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RzxPlaybackTest {
  @Test
  void anInterruptWhoseHandlerStartsWithAPrefixedInstructionCountsTheAcknowledgeAndBothItsFetches() {
    // Batman The Movie: its handler starts with PUSH IX; taken as two fetches, every frame ran one fetch long
    RzxPlayback playback = playback(new int[]{3, 5}, bytes(0x8000, 0x18, 0xFE), bytes(0x38, 0xDD, 0xE5, 0xFB, 0xED, 0x4D));
    playback.playFrames(2);
    assertEquals(7, playback.getInstructions());
  }

  @Test
  void anInstructionThatWritesRCountsItsOwnTwoFetches() {
    RzxPlayback playback = playback(new int[]{4}, bytes(0x8000, 0x3E, 0x55, 0xED, 0x4F, 0x18, 0xFE), bytes(0x38, 0xFB, 0xED, 0x4D));
    playback.playFrames(1);
    assertEquals(4, playback.getInstructions());
  }

  private static int[] bytes(int address, int... bytes) {
    int[] chunk = new int[bytes.length + 1];
    chunk[0] = address;
    System.arraycopy(bytes, 0, chunk, 1, bytes.length);
    return chunk;
  }

  private static RzxPlayback playback(int[] frameFetches, int[]... chunks) {
    RZXPlayerIO player = new RZXPlayerIO();
    State state = new State(player, new DefaultRegisterBankFactory().createBank(), new MockedMemory(true));
    for (int[] chunk : chunks)
      for (int i = 1; i < chunk.length; i++)
        state.getMemory().write(chunk[0] + i - 1, chunk[i]);
    state.getPc().write(0x8000);
    state.getRegisterSP().write(0xFF00);
    state.setIff1(true);
    state.setIntMode(State.InterruptionMode.IM1);
    OOZ80 ooz80 = new OOZ80(state, com.fpetrola.z80.minizx.emulation.Helper.getInstructionFetcher(state, new NullInstructionSpy(), new DefaultInstructionFactory(state)), new DefaultInstructionExecutor(state, false));
    InputRecordingBlock block = new InputRecordingBlock();
    for (int fetches : java.util.stream.IntStream.concat(java.util.Arrays.stream(frameFetches), java.util.stream.IntStream.of(1000)).toArray()) {
      InputRecordingBlock.Frame frame = new InputRecordingBlock.Frame();
      frame.fetchCounter = fetches;
      frame.returnValues = new byte[0];
      block.frames.add(frame);
    }
    block.numberOfFrames = block.frames.size();
    return new RzxPlayback(ooz80, player, new RzxFile(null, null, null, block), tstates -> {
    }, ooz80::execute);
  }
}
