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
import com.fpetrola.oozx.formats.Codes;
import com.fpetrola.oozx.formats.DeclaredFormat;
import com.fpetrola.oozx.formats.Encoding;
import com.fpetrola.oozx.formats.Field;
import com.fpetrola.oozx.formats.Fixed;
import com.fpetrola.oozx.formats.Layout;
import com.fpetrola.oozx.formats.Mapping;
import com.fpetrola.oozx.formats.SnapshotFile;
import com.fpetrola.oozx.snapshots.SzxFormat;
import com.fpetrola.oozx.speccy.devices.disk.Disk;
import com.fpetrola.oozx.speccy.devices.disk.Fdd;
import com.fpetrola.oozx.speccy.devices.opus.OpusPeripheral;
import dev.crystal.plugins.api.OnlyWith;

import java.util.List;

/**
 * The Opus Discovery in an SZX: OPUS says whether its ROM is paged in, which way the head was last stepping, the six
 * registers of its PIA, the four of its controller, and carries its RAM, its kept length before it, and an ODSK for
 * each drive with a disk in it, as the Beta's. Its ROM is not written. A file with OPUS plugs it in.
 */
@OnlyWith({"device-opus", "device-disk"})
public final class OpusMapping implements Mapping {

  static final Fixed OPUS = Fixed.of("OPUS", 23);
  static final Fixed ODSK = Fixed.each("ODSK", 7, 4);
  static final Codes<Disk.Type> KINDS = Codes.<Disk.Type>of("Opus disk type").is(0, Disk.Type.OPD).is(1, Disk.Type.OPD).otherwise(Disk.Type.OPD);

  static final Layout<OpusPeripheral> TABLE = Layout.<OpusPeripheral>of()
      .bit(OPUS, 0, 2, Field.of("opus.seekingLower", opus -> opus.fdc().direction == Fdd.Dir.OUT,
          (opus, lower) -> opus.fdc().direction = lower ? Fdd.Dir.OUT : Fdd.Dir.IN))
      .u32(OPUS, 8, Field.of("opus.romLength", opus -> 0, (opus, length) -> { }))
      .encoded(OPUS, 12, 6, new Registers())
      .u8(OPUS, 18, Field.of("opus.drives", opus -> OpusPeripheral.DRIVES, (opus, drives) -> { }))
      .u8(OPUS, 19, Field.of("opus.track", opus -> opus.fdc().trackRegister, (opus, track) -> opus.fdc().trackRegister = track))
      .u8(OPUS, 20, Field.of("opus.sector", opus -> opus.fdc().sectorRegister, (opus, sector) -> opus.fdc().sectorRegister = sector))
      .u8(OPUS, 21, Field.of("opus.data", opus -> opus.fdc().dataRegister, (opus, data) -> opus.fdc().dataRegister = data))
      .u8(OPUS, 22, Field.of("opus.status", opus -> opus.fdc().statusRegister, (opus, status) -> opus.fdc().statusRegister = status))
      .bit(OPUS, 0, 0, Field.of("opus.paged", OpusPeripheral::isPaged, OpusPeripheral::pagedIn))
      .rest(OPUS, 0, 1, 4, OpusPeripheral.RAM_SIZE, Field.of("opus.ram", OpusPeripheral::ram, OpusPeripheral::ram))
      .each(ODSK, opus -> OpusPeripheral.DRIVES, 0, OpusPeripheral::drive, Beta128Mapping.drive(ODSK, KINDS));

  /** The PIA's six, in the order OPUS keeps them: side A's control, data and direction, then side B's. */
  static final class Registers implements Encoding<OpusPeripheral> {
    public void read(Bytes bytes, OpusPeripheral opus, SnapshotFile file) {
      opus.pia(new OpusPeripheral.Pia(bytes.u8(12), bytes.u8(13), bytes.u8(14), bytes.u8(15), bytes.u8(16), bytes.u8(17)));
    }

    public void write(OpusPeripheral opus, Bytes bytes, SnapshotFile file) {
      OpusPeripheral.Pia pia = opus.pia();
      bytes.u8(12, pia.controlA()).u8(13, pia.dataA()).u8(14, pia.directionA())
          .u8(15, pia.controlB()).u8(16, pia.dataB()).u8(17, pia.directionB());
    }

    public List<Object> fields() {
      return List.of();
    }
  }

  @Override
  public Class<? extends DeclaredFormat> format() {
    return SzxFormat.class;
  }

  @Override
  public Bindings bindings() {
    return Bindings.of().on(OpusPeripheral.class, TABLE);
  }

  @Override
  public List<Fixed> blocks() {
    return List.of(OPUS, ODSK);
  }

  @Override
  public void plug(SnapshotFile file, Speccy speccy) {
    if (file.has(OPUS) && speccy.peripheralRegistry.find(OpusPeripheral.class) instanceof OpusPeripheral opus) opus.plugIn(true);
  }
}
