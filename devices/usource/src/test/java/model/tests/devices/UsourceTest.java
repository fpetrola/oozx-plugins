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
import com.fpetrola.oozx.speccy.devices.usource.UsourcePeripheral;
import com.fpetrola.oozx.speccy.machine.Spec128;
import com.fpetrola.oozx.speccy.machine.Spec48;
import com.fpetrola.oozx.speccy.machine.SpecPlus3;
import model.harness.MachineTest;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UsourceTest extends MachineTest {

  /** 8K that says where it is: 0xAA at its start, and at 0x0bae - 0x2bae in the mirror - a jump back to the program. */
  private static File markingRom() throws IOException {
    byte[] image = new byte[UsourcePeripheral.ROM_SIZE];
    image[0] = (byte) 0xAA;
    byte[] back = {(byte) 0xC3, 0x10, (byte) 0x80};
    System.arraycopy(back, 0, image, 0x0bae, back.length);
    File file = File.createTempFile("usource", ".rom");
    file.deleteOnExit();
    Files.write(file.toPath(), image);
    return file;
  }

  private Speccy speccy() {
    Speccy speccy = silentMachine();
    speccy.machine.select(speccy.machine.model(Spec48.class));
    return speccy;
  }

  private UsourcePeripheral pluggedIn(Speccy speccy, String rom) {
    if (rom != null) speccy.roms.choose("UsourcePeripheral", rom);
    UsourcePeripheral usource = (UsourcePeripheral) speccy.peripheralRegistry.find(UsourcePeripheral.class);
    usource.plugIn(true);
    assertTrue(speccy.peripheralRegistry.update(), "its ROM goes over the machine's, so it arrives with a hard reset");
    speccy.machine.reset(true);
    return usource;
  }

  @Test
  void anyAccessToItsPortFlipsTheRomInAndOut() throws IOException {
    Speccy speccy = speccy();
    UsourcePeripheral usource = pluggedIn(speccy, markingRom().getPath());
    assertTrue(usource.isAvailable());
    assertEquals(0xF3, speccy.memory.peek(0) & 0xff, "it starts out");
    speccy.ports.read(0x2bae);
    assertTrue(usource.isPaged());
    assertEquals(0xAA, speccy.memory.peek(0) & 0xff, "the ROM is not at 0x0000");
    assertEquals(0xAA, speccy.memory.peek(0x2000) & 0xff, "nor at 0x2000, where it is again");
    speccy.ports.write(0x2bae, (byte) 0);
    assertFalse(usource.isPaged());
    assertEquals(0xF3, speccy.memory.peek(0) & 0xff, "the machine's ROM did not come back");
    speccy.ports.read(0x2baf);
    assertFalse(usource.isPaged(), "the port is decoded on all sixteen lines");
  }

  @Test
  void theProcessorGoingTo0x2baeBringsItInBeforeItFetches() throws IOException {
    Speccy speccy = speccy();
    UsourcePeripheral usource = pluggedIn(speccy, markingRom().getPath());
    // DI; JP 0x2BAE - which is RANDOMIZE USR 11182 - and, at 0x8010, JR $ to come back to.
    byte[] program = {(byte) 0xF3, (byte) 0xC3, (byte) 0xAE, 0x2B};
    for (int i = 0; i < program.length; i++) speccy.memory.poke(0x8000 + i, program[i]);
    speccy.memory.poke(0x8010, (byte) 0x18);
    speccy.memory.poke(0x8011, (byte) 0xFE);
    var state = speccy.cpu.getOoz80().getState();
    state.getPc().write(0x8000);
    runFrames(speccy, 1);
    assertTrue(usource.isPaged(), "reaching 0x2bae did not bring it in");
    int pc = state.getPc().read();
    assertTrue(pc == 0x8010 || pc == 0x8011, "the jump at 0x2bae was not read from its ROM: pc " + Integer.toHexString(pc));
  }

  @Test
  void withoutItsRomNothingFlips() {
    Speccy speccy = speccy();
    UsourcePeripheral usource = pluggedIn(speccy, null);
    assertFalse(usource.isAvailable());
    speccy.ports.read(0x2bae);
    assertFalse(usource.isPaged());
    assertEquals(0xF3, speccy.memory.peek(0) & 0xff);
  }

  @Test
  void itWasSoldForTheFortyEightAndThe128() {
    Speccy speccy = speccy();
    UsourcePeripheral usource = (UsourcePeripheral) speccy.peripheralRegistry.find(UsourcePeripheral.class);
    assertTrue(usource.fitsOn(speccy.machine.model(Spec48.class)));
    assertTrue(usource.fitsOn(speccy.machine.model(Spec128.class)));
    assertFalse(usource.fitsOn(speccy.machine.model(SpecPlus3.class)));
  }
}
