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

package model.tests.formats;

import com.fpetrola.emulation.helpers.machine.MachineTypes;
import com.fpetrola.oozx.Speccy;
import com.fpetrola.oozx.formats.CoreFields;
import com.fpetrola.oozx.formats.Field;
import com.fpetrola.oozx.formats.Shape;
import com.fpetrola.oozx.speccy.devices.ay.AyPeripheral;
import com.fpetrola.oozx.speccy.machine.Paging128;
import com.fpetrola.oozx.speccy.machine.PagingPlus3;
import com.fpetrola.oozx.speccy.machine.SpectrumMachine;
import com.fpetrola.oozx.speccy.modules.display.Border;
import com.fpetrola.oozx.speccy.modules.machine.Machine;
import com.fpetrola.oozx.speccy.modules.memory.SpectrumMemory;
import com.fpetrola.oozx.speccy.modules.z80.Cpu;
import com.fpetrola.oozx.speccy.modules.z80.SpectrumZ80Clock;
import com.fpetrola.oozx.speccy.parts.Visitable;

import java.util.List;
import java.util.Set;

/**
 * A machine said part by part, one value a line, in the names the goldens use: what a snapshot
 * leaves in the machine once it is loaded, which is what the net holds every format to. It walks
 * the machine as a format does, and reaches each value through the same fields.
 */
final class MachineDescription {

  private static final List<Field<Cpu, Integer>> REGISTERS = List.of(
      CoreFields.A, CoreFields.F, CoreFields.BC, CoreFields.DE, CoreFields.HL,
      CoreFields.A_, CoreFields.F_, CoreFields.BC_, CoreFields.DE_, CoreFields.HL_,
      CoreFields.IX, CoreFields.IY, CoreFields.SP, CoreFields.PC, CoreFields.I, CoreFields.R, CoreFields.MEMPTR);
  private static final Set<Field<Cpu, Integer>> BYTE_WIDE = Set.of(CoreFields.A, CoreFields.F, CoreFields.A_, CoreFields.F_, CoreFields.I, CoreFields.R);

  private final StringBuilder text = new StringBuilder();
  private List<Integer> banks = List.of();

  private MachineDescription() {
  }

  static String of(Speccy speccy) {
    MachineDescription description = new MachineDescription();
    speccy.accept(description::say);
    return description.text.toString();
  }

  private void say(Visitable part) {
    if (part instanceof Machine machine) {
      MachineTypes model = machine.current.snapshotModel();
      line("machine", model == null ? "none" : model.name());
      line("issue2", CoreFields.ISSUE_2.get(machine));
      banks = model == null ? List.of() : Shape.banksOf(model);
    } else if (part instanceof Paging128 paging) {
      line("port7ffd", hex(CoreFields.PORT_7FFD.get(paging), 8));
      if (paging instanceof PagingPlus3 plus3) line("port1ffd", hex(CoreFields.PORT_1FFD.get(plus3), 8));
    } else if (part instanceof SpectrumMachine) {
      // an unpaged model: nothing of its own to say
    } else if (part instanceof Cpu cpu) {
      REGISTERS.forEach(register -> line(register.label(), hex(register.get(cpu), BYTE_WIDE.contains(register) ? 8 : 16)));
      line("iff1", CoreFields.IFF1.get(cpu));
      line("iff2", CoreFields.IFF2.get(cpu));
      line("im", "IM" + CoreFields.IM.get(cpu));
      line("halted", CoreFields.HALTED.get(cpu));
      line("eiPending", CoreFields.EI_PENDING.get(cpu));
    } else if (part instanceof SpectrumMemory memory) {
      banks.forEach(bank -> line("page." + bank, Fields.digest(CoreFields.PAGE.get(memory, bank))));
    } else if (part instanceof Border border) {
      line("border", CoreFields.BORDER.get(border));
    } else if (part instanceof SpectrumZ80Clock clock) {
      line("tstates", CoreFields.TSTATES.get(clock));
    } else if (part instanceof AyPeripheral ay) {
      line("ay.selected", hex(ay.selected(), 8));
      StringBuilder registers = new StringBuilder();
      for (int register = 0; register < 16; register++) {
        registers.append(register == 0 ? "" : " ").append(String.format("%02x", ay.register(register)));
      }
      line("ay.registers", registers);
    } else {
      line("part", part.getClass().getSimpleName());
    }
  }

  private void line(String name, Object value) {
    text.append(name).append(" = ").append(value).append('\n');
  }

  private static String hex(int value, int bits) {
    return String.format("0x%0" + bits / 4 + "x", value);
  }
}
