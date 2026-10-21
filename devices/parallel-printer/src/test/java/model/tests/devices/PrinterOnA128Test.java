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
import com.fpetrola.oozx.speccy.devices.parallelprinter.ParallelPrinterPeripheral;
import com.fpetrola.oozx.speccy.machine.Spec128;
import com.fpetrola.oozx.speccy.modules.keyboard.SpectrumKey;
import model.harness.MachineTest;
import org.junit.jupiter.api.Test;

import static com.fpetrola.oozx.speccy.modules.keyboard.SpectrumKey.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PrinterOnA128Test extends MachineTest {

  private Speccy speccy() {
    Speccy speccy = silentMachine();
    speccy.machine.select(speccy.machine.model(Spec128.class));
    return speccy;
  }

  private ParallelPrinterPeripheral pluggedIn(Speccy speccy) {
    ParallelPrinterPeripheral printer = (ParallelPrinterPeripheral) speccy.peripheralRegistry.find(ParallelPrinterPeripheral.class);
    printer.plugIn(true);
    speccy.peripheralRegistry.update();
    assertTrue(speccy.peripheralRegistry.isActive(ParallelPrinterPeripheral.class), "a 128 has a socket for it: the RS232");
    return printer;
  }

  @Test
  void whatTheAyPutsOnTheRs232LineIsPrinted() {
    Speccy speccy = speccy();
    ParallelPrinterPeripheral printer = pluggedIn(speccy);
    speccy.ports.write(0xfffd, (byte) 14);
    // Bit 3 of the I/O port A is the line; the other pins carry whatever they carry.
    int[] start = {0xf7}, o = {0x08, 0x08, 0x08, 0x08, 0xf7, 0xf7, 0x08, 0xf7}, stop = {0xff};
    for (int[] bits : new int[][]{stop, start, o, stop}) {
      for (int pins : bits) {
        speccy.ports.write(0xbffd, (byte) pins);
      }
    }
    assertEquals("O", printer.printer().text());
  }

  @Test
  void aSerialPrinterLeavesTheCentronicsPortToTheMachinesThatHaveOne() {
    Speccy speccy = speccy();
    pluggedIn(speccy);
    assertNotEquals(0xfe, speccy.ports.read(0x0ffd) & 0xff, "a 128 has no Centronics port to read BUSY from");
  }

  @Test
  void lprintIn128BasicComesOutOfThePrinter() {
    Speccy speccy = speccy();
    ParallelPrinterPeripheral printer = pluggedIn(speccy);
    runFrames(speccy, 100);
    press(speccy, CAPS_SHIFT, SIX);
    press(speccy, ENTER);
    runFrames(speccy, 50);
    for (SpectrumKey key : new SpectrumKey[]{L, P, R, I, N, T, SPACE}) {
      press(speccy, key);
    }
    press(speccy, SYMBOL_SHIFT, P);
    press(speccy, H);
    press(speccy, I);
    press(speccy, SYMBOL_SHIFT, P);
    press(speccy, ENTER);
    for (int frame = 0; frame < 100 && !printer.printer().text().endsWith("\n"); frame++) {
      runFrames(speccy, 1);
    }
    // The editor types in lower case until CAPS LOCK; the line ends where the ROM ends it.
    assertEquals("hi", printer.printer().text().strip());
  }

  /** Held long enough for the ROM to see it and let go before it repeats. */
  private static void press(Speccy speccy, SpectrumKey... keys) {
    for (SpectrumKey key : keys) {
      speccy.keys.press(key);
    }
    runFrames(speccy, 3);
    speccy.keys.releaseAll();
    runFrames(speccy, 3);
  }
}
