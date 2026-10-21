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

package com.fpetrola.oozx.speccy.devices.parallelprinter;

import com.fpetrola.oozx.speccy.devices.ay.AyPeripheral;
import com.fpetrola.oozx.speccy.peripherals.Peripheral;
import com.fpetrola.oozx.speccy.peripherals.PeripheralRegistry;
import com.fpetrola.oozx.speccy.ports.Wired;
import com.fpetrola.oozx.speccy.ports.BusAnswer;
import com.fpetrola.oozx.speccy.machine.SpectrumMachine;
import com.fpetrola.oozx.speccy.peripherals.PluggablePeripheral;
import com.fpetrola.oozx.speccy.ports.DefaultPortHandler;

import java.util.List;
import java.util.function.IntConsumer;

/**
 * The printer as a machine sees it. A +2A or +3 has the port on its back: bit 0 reads BUSY,
 * low for a printer that is never busy, and a write latches the data; the strobe is bit 4 of the
 * +3's own paging port, which the machine hands over. A +D or a DISCiPLE brings its own port and
 * hands the printer the bytes itself, so on those machines this has no ports and is only asked
 * whether it is there.
 * <p>
 * A machine with an AY on its board has an RS232 socket on the chip's I/O port A, and a printer
 * cabled to it reads the transmit line a bit at a time.
 */
@com.google.inject.Singleton
public class ParallelPrinterPeripheral extends PluggablePeripheral {

  /** The RS232's transmit line, on the pins of the AY's I/O port A. */
  private static final int TXD = 0x08;

  private final ParallelPrinter printer;
  private final PeripheralRegistry peripherals;
  private final Wired[] centronics;
  private final IntConsumer rs232;
  private AyPeripheral ay;

  @com.google.inject.Inject
  public ParallelPrinterPeripheral(ParallelPrinter printer, PeripheralRegistry peripherals) {
    super(List.of());
    this.printer = printer;
    this.peripherals = peripherals;
    centronics = new Wired[]{Wired.at(0xf002, 0x0000, new DefaultPortHandler(true, true) {
      public BusAnswer read(int port) {
        return BusAnswer.of(0xfe);
      }

      public void write(int port, byte value) {
        printer.write(value);
      }
    }), Wired.at(0xf002, 0x1000, new DefaultPortHandler(false, true) {
      /** The strobe is bit 4 of the +3's own paging port, next to the disk motor. */
      public void write(int port, byte value) {
        printer.strobe((value & 0x10) != 0);
      }
    })};
    rs232 = pins -> printer.serial((pins & TXD) != 0);
  }

  public ParallelPrinter printer() {
    return printer;
  }

  @Override
  public boolean fitsOn(SpectrumMachine machine) {
    return machine.pagesThrough1ffd() || machine.hasOnBoard(AyPeripheral.class);
  }

  @Override
  public void activate(SpectrumMachine machine) {
    ports(machine.pagesThrough1ffd() ? centronics : new Wired[0]);
    for (Class<? extends Peripheral> part : machine.onBoard()) {
      if (peripherals.find(part) instanceof AyPeripheral chip) ay = chip;
    }
    if (ay != null) ay.onPortA(rs232);
  }

  @Override
  public void deactivate() {
    if (ay != null) ay.offPortA(rs232);
    ay = null;
  }
}
