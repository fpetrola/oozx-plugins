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

package model.tests.devices;

import com.fpetrola.oozx.Speccy;
import com.fpetrola.oozx.speccy.devices.uspeech.UspeechPeripheral;
import com.fpetrola.oozx.speccy.machine.Spec128;
import com.fpetrola.oozx.speccy.machine.Spec48;
import com.fpetrola.oozx.speccy.machine.SpecPlus3;
import com.fpetrola.oozx.speccy.modules.sound.SilentSoundDevice;
import com.fpetrola.oozx.speccy.modules.sound.SoundCard;
import com.fpetrola.oozx.speccy.modules.z80.SpectrumZ80Clock;
import model.harness.MachineTest;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UspeechTest extends MachineTest {

  private static class Loudest extends SilentSoundDevice {
    int peak;

    public void play(int[] data, int length) {
      for (int i = 0; i < length; i++) peak = Math.max(peak, Math.abs(data[i]));
    }
  }

  private final Loudest card = new Loudest();

  private static File file(byte[] image) throws IOException {
    File file = File.createTempFile("uspeech", ".rom");
    file.deleteOnExit();
    Files.write(file.toPath(), image);
    return file;
  }

  /** Its own 2K: 0xAA at the start and, at 0x0038, a jump back to the program at 0x8010. */
  private static byte[] board() {
    byte[] image = new byte[UspeechPeripheral.ROM_SIZE];
    image[0] = (byte) 0xAA;
    image[0x38] = (byte) 0xC3;
    image[0x39] = 0x10;
    image[0x3a] = (byte) 0x80;
    return image;
  }

  /**
   * The chip's 2K, where allophone 1 starts at byte 2: LOADALL with fifteen repeats, the loudest
   * amplitude, a period of a hundred samples and no filter at all - a click every hundredth sample
   * - and then a halt.
   */
  private static byte[] chip() {
    byte[] image = new byte[UspeechPeripheral.ROM_SIZE];
    byte[] allophone = {(byte) 0x8F, (byte) 0xFF, 100, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0x00};
    System.arraycopy(allophone, 0, image, 2, allophone.length);
    return image;
  }

  private Speccy speccy() {
    Speccy speccy = Speccy.create(new SpectrumZ80Clock(), binder -> binder.bind(SoundCard.class).toInstance(card));
    speccy.init();
    speccy.picture.active = false;
    speccy.sound.output.enabled = true;
    speccy.machine.select(speccy.machine.model(Spec48.class));
    return speccy;
  }

  private UspeechPeripheral pluggedIn(Speccy speccy, boolean withRoms) throws IOException {
    UspeechPeripheral uspeech = (UspeechPeripheral) speccy.peripheralRegistry.find(UspeechPeripheral.class);
    // The choice is kept for every machine this test makes, so the one without ROMs takes them back.
    speccy.roms.runOn(uspeech, withRoms ? List.of(file(board()).getPath(), file(chip()).getPath()) : List.of());
    uspeech.plugIn(true);
    assertTrue(speccy.peripheralRegistry.update(), "its ROM goes over the machine's, so it arrives with a hard reset");
    speccy.machine.reset(true);
    return uspeech;
  }

  @Test
  void anAccessTo0x0038FlipsItAndReadsWhereItFlippedTo() throws IOException {
    Speccy speccy = speccy();
    UspeechPeripheral uspeech = pluggedIn(speccy, true);
    assertTrue(uspeech.isAvailable());
    assertEquals(0xF3, speccy.memory.peek(0x0000) & 0xff, "out, the machine's ROM shows through it");
    assertEquals(0xC3, speccy.memory.read(0x0038), "the read flipped it in and read its ROM");
    assertTrue(uspeech.isPaged());
    assertEquals(0xAA, speccy.memory.peek(0x0800) & 0xff, "its 2K again at 0x0800");
    assertEquals(0xff, speccy.memory.peek(0x2000) & 0xff, "and nothing above 0x2000");
    assertEquals(0, speccy.memory.peek(0x1000) & 0xff, "the chip has nothing waiting");
    speccy.memory.write(0x0038, (byte) 0);
    assertFalse(uspeech.isPaged(), "a write flips it too");
    assertEquals(0xF3, speccy.memory.peek(0x0000) & 0xff);
  }

  @Test
  void theFetchAt0x0038BringsItInAndAReadOfItTakesItOut() throws IOException {
    Speccy speccy = speccy();
    UspeechPeripheral uspeech = pluggedIn(speccy, true);
    // DI; JP 0x0038 - its ROM jumps to 0x8010 - and there LD A,(0x0038); LD (0x8100),A; JR $.
    byte[] program = {(byte) 0xF3, (byte) 0xC3, 0x38, 0x00};
    byte[] back = {0x3A, 0x38, 0x00, 0x32, 0x00, (byte) 0x81, 0x18, (byte) 0xFE};
    for (int i = 0; i < program.length; i++) speccy.memory.poke(0x8000 + i, program[i]);
    for (int i = 0; i < back.length; i++) speccy.memory.poke(0x8010 + i, back[i]);
    speccy.cpu.getOoz80().getState().getPc().write(0x8000);
    runFrames(speccy, 1);
    assertEquals(0xF5, speccy.memory.peek(0x8100) & 0xff, "the read of 0x0038 should have seen the 48K's PUSH AF there");
    assertFalse(uspeech.isPaged(), "and taken it out");
  }

  @Test
  void itsPortFlipsItAsWell() throws IOException {
    Speccy speccy = speccy();
    UspeechPeripheral uspeech = pluggedIn(speccy, true);
    speccy.ports.read(0x0038);
    assertTrue(uspeech.isPaged());
    speccy.ports.write(0x0038, (byte) 7);
    assertFalse(uspeech.isPaged());
  }

  @Test
  void anAllophoneWrittenWhileItIsInIsHeardAndWaitsUntilTheChipTakesIt() throws IOException {
    Speccy speccy = speccy();
    UspeechPeripheral uspeech = pluggedIn(speccy, true);
    speccy.memory.read(0x0038);
    speccy.zxClock.addTStates(1000);
    speccy.memory.write(0x1000, (byte) 1);
    assertEquals(1, uspeech.allophone());
    assertEquals(1, speccy.memory.peek(0x1000) & 0xff, "written a moment ago, it is still waiting");
    // Halted, the chip looks for the next allophone every sixty-four samples, as Fuse's does.
    speccy.zxClock.addTStates(64 * 358 + 1000);
    assertEquals(0, speccy.ports.read(0x1000) & 0xff, "by then the chip has taken it");
    card.peak = 0;
    speccy.sound.frame();
    speccy.sound.frame();
    assertTrue(card.peak > 0, "the chip said nothing");
  }

  @Test
  void itWasSoldForTheFortyEight() throws IOException {
    Speccy speccy = speccy();
    UspeechPeripheral uspeech = (UspeechPeripheral) speccy.peripheralRegistry.find(UspeechPeripheral.class);
    assertTrue(uspeech.fitsOn(speccy.machine.model(Spec48.class)));
    assertFalse(uspeech.fitsOn(speccy.machine.model(Spec128.class)));
    assertFalse(uspeech.fitsOn(speccy.machine.model(SpecPlus3.class)));
  }

  @Test
  void withoutItsRomsItIsOverNothing() throws IOException {
    Speccy speccy = speccy();
    UspeechPeripheral uspeech = pluggedIn(speccy, false);
    assertFalse(uspeech.isAvailable());
    assertEquals(0xF5, speccy.memory.read(0x0038));
    assertFalse(uspeech.isPaged());
  }
}
