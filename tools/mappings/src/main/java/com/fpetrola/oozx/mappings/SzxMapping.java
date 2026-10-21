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

package com.fpetrola.oozx.mappings;

import com.fpetrola.oozx.Speccy;
import com.fpetrola.oozx.formats.Bindings;
import com.fpetrola.oozx.formats.Bytes;
import com.fpetrola.oozx.formats.DeclaredFormat;
import com.fpetrola.oozx.formats.Encoding;
import com.fpetrola.oozx.formats.Fixed;
import com.fpetrola.oozx.formats.Layout;
import com.fpetrola.oozx.formats.Mapping;
import com.fpetrola.oozx.formats.SnapshotFile;
import com.fpetrola.oozx.snapshots.SzxFormat;
import com.fpetrola.oozx.speccy.devices.ay.AyPeripheral;
import com.fpetrola.oozx.speccy.devices.ulaplus.UlaPlusPeripheral;
import com.fpetrola.oozx.speccy.machine.RomNotLoadedException;
import com.fpetrola.oozx.speccy.machine.Spectrum;

import java.util.List;
import java.util.Map;

/** What an SZX carries beyond the machine it names: the AY in its block AY, the ULAplus in PLTT, and the machine and ROMs a session kept. */
public final class SzxMapping implements Mapping {

  static final Fixed AY = Fixed.of("AY\0\0", 18);
  static final Fixed PLTT = Fixed.of("PLTT", 66);

  static final Layout<AyPeripheral> AY_TABLE = Layout.<AyPeripheral>of()
      .bytes(AY, 2, 16, AyFields.REGISTERS).u8(AY, 1, AyFields.SELECTED);
  static final Layout<UlaPlusPeripheral> ULAPLUS = Layout.<UlaPlusPeripheral>of().encoded(PLTT, 0, 66, new Palette());

  @Override
  public Class<? extends DeclaredFormat> format() {
    return SzxFormat.class;
  }

  @Override
  public Bindings bindings() {
    return Bindings.of().on(AyPeripheral.class, AY_TABLE).on(UlaPlusPeripheral.class, ULAPLUS);
  }

  @Override
  public List<Fixed> blocks() {
    return List.of(AY, PLTT);
  }

  /**
   * Before the machine is walked: the machine it was kept from exactly, and its ROMs, which the
   * header cannot say of a clone written as the model it copies; and the ULAplus, which is in the
   * machine if the file has its block and not if it does not, as the old loader did.
   */
  @Override
  public void plug(SnapshotFile file, Speccy speccy) {
    byte[] block = file.carried().get(SzxSession.BLOCK);
    if (block != null) becomes(SzxSession.around(block), speccy, file);
    if (speccy.peripheralRegistry.find(UlaPlusPeripheral.class) instanceof UlaPlusPeripheral chip) {
      chip.fitted(file.has(PLTT));
      speccy.peripheralRegistry.update();
    }
  }

  private static void becomes(Map<String, String> around, Speccy speccy, SnapshotFile file) {
    Spectrum was = speccy.machine.current;
    String named = around.get("machine");
    Spectrum machine = named == null ? was : speccy.machine.forShortName(named).orElse(null);
    if (machine == null) {
      file.note("this build has no " + named + ": loaded into the " + was.getName());
      machine = was;
    }
    boolean otherRoms = runsOn(around.get("roms"), machine, speccy, file);
    if (machine == was && !otherRoms) return;
    try {
      speccy.machine.select(machine);
    } catch (RomNotLoadedException noRoms) {
      file.note("the " + machine.getName() + " cannot be switched on here (" + noRoms.getMessage() + "): loaded into the " + was.getName());
      speccy.machine.select(was);
    }
  }

  /** Whether the machine is to run on other ROMs than it does: a set it has, and whose ROMs are here. */
  private static boolean runsOn(String roms, Spectrum machine, Speccy speccy, SnapshotFile file) {
    if (roms == null || roms.equals(speccy.roms.chosenSet(machine))) return false;
    if (!speccy.roms.setsFor(machine).containsKey(roms)) {
      file.note("the " + machine.getName() + " has no ROMs called " + roms + " here");
      return false;
    }
    List<String> before = speccy.roms.running(machine);
    speccy.roms.chooseSet(machine, roms);
    if (speccy.roms.missingFor(machine).isEmpty()) return true;
    file.note("the ROMs " + roms + " are not here: the " + machine.getName() + " runs on its own");
    speccy.roms.runOn(machine, before);
    return false;
  }

  /** PLTT: whether the palette is on, the register last named, and the sixty-four colours as written. */
  static final class Palette implements Encoding<UlaPlusPeripheral> {
    public void read(Bytes bytes, UlaPlusPeripheral chip, SnapshotFile file) {
      int[] colours = new int[64];
      for (int colour = 0; colour < 64; colour++) colours[colour] = bytes.u8(2 + colour);
      chip.asItWas(colours, bytes.u8(0) == 1);
    }

    public void write(UlaPlusPeripheral chip, Bytes bytes, SnapshotFile file) {
      bytes.u8(0, chip.inUse() ? 1 : 0).u8(1, chip.named());
      for (int colour = 0; colour < 64; colour++) bytes.u8(2 + colour, chip.register(colour));
    }

    public List<Object> fields() {
      return List.of();
    }
  }
}
