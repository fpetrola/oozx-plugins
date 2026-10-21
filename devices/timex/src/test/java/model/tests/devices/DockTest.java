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
import com.fpetrola.oozx.speccy.machine.DockCartridge;
import com.fpetrola.oozx.speccy.machine.Tc2068;
import com.fpetrola.oozx.speccy.modules.memory.MemoryPart;
import model.harness.MachineTest;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;

import static com.fpetrola.oozx.speccy.machine.DockCartridge.DOCK;
import static com.fpetrola.oozx.speccy.machine.DockCartridge.EXROM;
import static com.fpetrola.oozx.speccy.machine.DockCartridge.HOME;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DockTest extends MachineTest {

  private static final int NOTHING = 0, EMPTY_RAM = 1, ROM = 2, RAM = 3;

  private final Speccy speccy = silentMachine();

  /** One block as libspectrum reads it: the bank, what each of its eight pages is, and eight K of a filler for each page that brings bytes. */
  private static byte[] block(int bank, int[] types, int... fillers) {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    out.write(bank);
    for (int type : types) out.write(type);
    for (int filler : fillers) {
      byte[] page = new byte[0x2000];
      Arrays.fill(page, (byte) filler);
      out.writeBytes(page);
    }
    return out.toByteArray();
  }

  private static byte[] file(byte[]... blocks) {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    for (byte[] block : blocks) out.writeBytes(block);
    return out.toByteArray();
  }

  private Tc2068 aTimexWith(byte[] dck) throws IOException {
    speccy.machine.select(speccy.machine.model(Tc2068.class));
    Tc2068 timex = (Tc2068) speccy.machine.current;
    timex.insert(dck == null ? null : DockCartridge.read("game", dck));
    speccy.machine.reset(false);
    return timex;
  }

  private void out(int port, int value) {
    speccy.ports.write(port, (byte) value);
  }

  private int peek(int address) {
    return speccy.memory.peek(address) & 0xff;
  }

  @Test
  void theDockHoldsTheCartridgeAndWhatItDoesNotFillReadsAsNothing() throws IOException {
    aTimexWith(file(block(DOCK, new int[]{ROM, NOTHING, NOTHING, RAM, NOTHING, NOTHING, NOTHING, NOTHING}, 0x11, 0x33)));
    out(0xff, 0x00);
    out(0xf4, 0x01);
    assertEquals(0x11, peek(0x0000), "the cartridge's ROM is not in the dock's first eight K");
    out(0xf4, 0x02);
    assertEquals(0xff, peek(0x2000), "a page the cartridge does not have is empty bus");
    out(0xf4, 0x08);
    assertEquals(0x33, peek(0x6000), "the cartridge's RAM comes with what the file put in it");
    speccy.memory.poke(0x6000, (byte) 0x77);
    assertEquals(0x77, peek(0x6000), "and it takes writes");
    speccy.memory.poke(0x0000, (byte) 0x77);
    out(0xf4, 0x01);
    assertEquals(0x11, peek(0x0000), "its ROM does not");
  }

  @Test
  void anExromPageTakesThePlaceOfTheMachinesOwnOnlyWhereTheCartridgeHasOne() throws IOException {
    aTimexWith(null);
    out(0xff, 0x80);
    out(0xf4, 0x02);
    int machinesOwn = peek(0x2000);
    aTimexWith(file(block(EXROM, new int[]{ROM, NOTHING, NOTHING, NOTHING, NOTHING, NOTHING, NOTHING, NOTHING}, 0x22)));
    out(0xff, 0x80);
    out(0xf4, 0x01);
    assertEquals(0x22, peek(0x0000), "the cartridge's EXROM page is not behind the dock");
    out(0xf4, 0x02);
    assertEquals(machinesOwn, peek(0x2000), "where it has none, the machine's EXROM is still there");
  }

  @Test
  void theHomeBankFillsTheMachinesOwnRamWhenTheCartridgeGoesIn() throws IOException {
    aTimexWith(file(block(HOME, new int[]{NOTHING, NOTHING, NOTHING, NOTHING, RAM, NOTHING, NOTHING, NOTHING}, 0x44)));
    out(0xf4, 0x00);
    assertEquals(0x44, peek(0x8000));
    assertEquals(0x44, peek(0x9fff));
    speccy.memory.poke(0x8000, (byte) 0x55);
    speccy.machine.reset(false);
    assertEquals(0x55, peek(0x8000), "only the reset that takes the cartridge in loads it");
  }

  @Test
  void takenOutTheDockIsEmptyAgain() throws IOException {
    Tc2068 timex = aTimexWith(file(block(DOCK, new int[]{ROM, NOTHING, NOTHING, NOTHING, NOTHING, NOTHING, NOTHING, NOTHING}, 0x11)));
    timex.insert(null);
    speccy.machine.reset(false);
    out(0xff, 0x00);
    out(0xf4, 0x01);
    assertEquals(0xff, peek(0x0000));
  }

  @Test
  void whereThePictureIsTheCartridgeWaitsForTheUlaLikeTheRamThere() throws IOException {
    MemoryPart[] dock = DockCartridge.read("game", block(DOCK, new int[]{ROM, ROM, ROM, ROM, NOTHING, NOTHING, NOTHING, NOTHING}, 1, 2, 3, 4)).bank(DOCK);
    assertFalse(dock[1].contended);
    assertTrue(dock[2].contended, "0x4000 is where the ULA reads the picture");
    assertTrue(dock[3].contended);
  }

  @Test
  void whatLibspectrumRefusesIsRefused() {
    int[] onePage = {ROM, NOTHING, NOTHING, NOTHING, NOTHING, NOTHING, NOTHING, NOTHING};
    assertThrows(IOException.class, () -> DockCartridge.read("game", block(7, onePage, 0x11)), "an unknown bank");
    assertThrows(IOException.class, () -> DockCartridge.read("game", block(DOCK, new int[]{4, 0, 0, 0, 0, 0, 0, 0})), "an unknown page type");
    assertThrows(IOException.class, () -> DockCartridge.read("game", block(DOCK, onePage)), "a page with no bytes after it");
    assertThrows(IOException.class, () -> DockCartridge.read("game", new byte[]{0, 2, 0}), "less than a block");
    assertThrows(IOException.class, () -> DockCartridge.read("game", block(HOME, onePage, 0x11)), "the machine's own ROM, which no dock replaces");
  }

  @Test
  void aDckIsKnownByItsName() {
    assertTrue(DockCartridge.isOne("Flight Simulator.DCK"));
    assertFalse(DockCartridge.isOne("game.rom"));
  }
}
