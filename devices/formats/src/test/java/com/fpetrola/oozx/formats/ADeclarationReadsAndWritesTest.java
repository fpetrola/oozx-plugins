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

package com.fpetrola.oozx.formats;

import com.fpetrola.emulation.helpers.machine.MachineTypes;
import com.fpetrola.oozx.speccy.parts.Visitable;
import com.fpetrola.oozx.speccy.parts.PartVisitor;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * A format declared as stretches, tables over parts and bindings, read and written by the engine
 * with the same declaration: here over parts made up for the test, so that nothing but the engine
 * is tried.
 */
class ADeclarationReadsAndWritesTest {

  /** A part with two registers and a flag. */
  static class Chip implements Visitable {
    int a, bc;
    boolean on, alsoOn;

    public void accept(PartVisitor visitor) {
      visitor.visit(this);
    }
  }

  /** A part with pages. */
  static class Ram implements Visitable {
    final byte[][] banks = new byte[8][Pages.LENGTH];

    public void accept(PartVisitor visitor) {
      visitor.visit(this);
    }
  }

  static final Field<Chip, Integer> A = Field.of("a", chip -> chip.a, (chip, v) -> chip.a = v);
  static final Field<Chip, Integer> BC = Field.of("bc", chip -> chip.bc, (chip, v) -> chip.bc = v);
  static final Field<Chip, Boolean> ON = Field.of("on", chip -> chip.on, (chip, v) -> chip.on = v);
  static final Field<Chip, Boolean> ALSO_ON = Field.of("alsoOn", chip -> chip.alsoOn, (chip, v) -> chip.alsoOn = v);
  static final PageField<Ram> PAGE = PageField.of((ram, bank) -> ram.banks[bank].clone(), (ram, bank, bytes) -> ram.banks[bank] = bytes.clone());

  static final Fixed HEADER = Fixed.of("header", 4);
  static final Fixed TAIL = Fixed.of("tail", 1);
  static final Layout<Chip> CHIP = Layout.<Chip>of().u8(HEADER, 0, A).u16(HEADER, 1, BC).bit(HEADER, 3, 2, ON).alsoSets(ALSO_ON);
  static final Layout<Ram> RAM = Layout.<Ram>of().pages(PAGE);
  static final Bindings BINDINGS = Bindings.of().on(Chip.class, CHIP).on(Ram.class, RAM);

  static byte[] page(int value) {
    byte[] page = new byte[Pages.LENGTH];
    Arrays.fill(page, (byte) value);
    return page;
  }

  static byte[] concat(byte[]... parts) {
    Sink sink = new Sink();
    for (byte[] part : parts) sink.bytes(part);
    return sink.toBytes();
  }

  static void walk(List<Visitable> parts, SnapshotFile file, boolean reading) {
    parts.forEach(part -> BINDINGS.apply(part, reading ? Direction.reading(file) : Direction.writing(file)));
  }

  @Test
  void eachFieldIsReadFromItsPlace() {
    Shape shape = Shape.of(MachineTypes.SPECTRUM48K, HEADER, Pages.of(5, 2, 0));
    SnapshotFile file = shape.parse(concat(new byte[]{0x11, 0x34, 0x12, 0x04}, page(5), page(2), page(0)));
    Chip chip = new Chip();
    Ram ram = new Ram();
    walk(List.of(chip, ram), file, true);
    assertEquals(0x11, chip.a);
    assertEquals(0x1234, chip.bc);
    assertEquals(true, chip.on);
    assertEquals(true, chip.alsoOn);
    assertArrayEquals(page(2), ram.banks[2]);
  }

  @Test
  void whatIsWrittenIsWhatIsRead() {
    Shape shape = Shape.of(MachineTypes.SPECTRUM48K, HEADER, Pages.of(5, 2, 0));
    Chip chip = new Chip();
    chip.a = 0x22;
    chip.bc = 0xbeef;
    chip.on = true;
    Ram ram = new Ram();
    ram.banks[5] = page(5);
    SnapshotFile out = shape.empty();
    walk(List.of(chip, ram), out, false);
    byte[] bytes = shape.assemble(out);
    assertEquals(4 + 3 * Pages.LENGTH, bytes.length);

    Chip back = new Chip();
    Ram ramBack = new Ram();
    walk(List.of(back, ramBack), shape.parse(bytes), true);
    assertEquals(0x22, back.a);
    assertEquals(0xbeef, back.bc);
    assertEquals(true, back.on);
    assertArrayEquals(page(5), ramBack.banks[5]);
  }

  @Test
  void thePageAtTheTopIsKnownOnlyOnceItsPortIsRead() {
    Shape shape = Shape.of(MachineTypes.SPECTRUM128K, Pages.of(5, 2), Pages.bankAtTop(TAIL, 0), TAIL, Pages.remaining());
    SnapshotFile file = shape.parse(concat(page(5), page(2), page(3), new byte[]{3}, page(0), page(1), page(4), page(6), page(7)));
    for (int bank = 0; bank < 8; bank++) {
      assertArrayEquals(page(bank), file.pageOrZeros(bank), "bank " + bank);
    }
    byte[] copied = concat(page(5), page(2), page(2), new byte[]{2}, page(0), page(1), page(3), page(4), page(6), page(7));
    assertArrayEquals(page(7), shape.parse(copied).pageOrZeros(7));
    byte[] different = concat(page(5), page(2), page(9), new byte[]{2}, page(0), page(1), page(3), page(4), page(6), page(7));
    assertThrows(Refused.class, () -> shape.parse(different));

    SnapshotFile out = shape.empty();
    for (int bank = 0; bank < 8; bank++) out.page(bank, page(bank));
    out.u8(TAIL, 0, 3);
    assertArrayEquals(concat(page(5), page(2), page(3), new byte[]{3}, page(0), page(1), page(4), page(6), page(7)), shape.assemble(out));
  }

  @Test
  void aStretchTheShapeDoesNotHaveIsNotRead() {
    Layout<Chip> inTheTail = Layout.<Chip>of().u8(TAIL, 0, A);
    SnapshotFile file = Shape.of(MachineTypes.SPECTRUM48K, HEADER).parse(new byte[]{9, 0, 0, 0});
    Chip chip = new Chip();
    chip.a = 7;
    inTheTail.read(file, chip);
    assertEquals(7, chip.a);
  }

  @Test
  void aVirtualStretchIsFilledByRulesAndNeverWritten() {
    Fixed stacked = Fixed.virtual("stacked", 2);
    Rule rule = new Rule() {
      public void afterParsing(SnapshotFile file) { file.u16(stacked, 0, file.u16(HEADER, 1) + 1); }
      public void beforeAssembling(SnapshotFile file) { file.u16(HEADER, 1, file.u16(stacked, 0) - 1); }
    };
    Shape shape = Shape.of(MachineTypes.SPECTRUM48K, HEADER, stacked).with(rule);
    SnapshotFile file = shape.parse(new byte[]{0, 0x10, 0, 0});
    assertEquals(0x11, file.u16(stacked, 0));
    assertArrayEquals(new byte[]{0, 0x10, 0, 0}, shape.assemble(file));
  }

  @Test
  void leftoverBytesAreNoted() {
    SnapshotFile file = Shape.of(MachineTypes.SPECTRUM48K, HEADER).parse(new byte[]{0, 0, 0, 0, 1, 2});
    assertEquals(List.of("skipped: 2 bytes at the end"), file.notes());
  }

  @Test
  void aPeripheralNoBindingTakesIsNotedWhenWriting() {
    class Gadget extends com.fpetrola.oozx.speccy.peripherals.AbstractPeripheral {
      Gadget() { super(List.of()); }
    }
    SnapshotFile out = Shape.of(MachineTypes.SPECTRUM48K, HEADER).empty();
    walk(new ArrayList<>(List.of(new Chip(), new Gadget())), out, false);
    assertEquals(List.of("not carried: Gadget"), out.notes());
  }
}
