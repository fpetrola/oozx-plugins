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

import com.fpetrola.oozx.speccy.machine.Paging128;
import com.fpetrola.oozx.speccy.machine.PagingPlus3;
import com.fpetrola.oozx.speccy.modules.display.Border;
import com.fpetrola.oozx.speccy.modules.machine.Machine;
import com.fpetrola.oozx.speccy.modules.memory.SpectrumMemory;
import com.fpetrola.oozx.speccy.modules.z80.Cpu;
import com.fpetrola.oozx.speccy.modules.z80.SpectrumZ80Clock;
import com.fpetrola.z80.bytecode.RegistersBase;

import java.util.function.BiConsumer;
import java.util.function.ToIntFunction;

/**
 * How to reach what the parts of the core have, once, for every format: each register of the
 * processor, the colour of the border, the paging ports, the banks of RAM, the T-states, issue 2.
 * Through each part's own API; nothing here is kept.
 */
public final class CoreFields {

  public static final Field<Cpu, Integer> A = register("a", RegistersBase::getRegA, RegistersBase::setRegA);
  public static final Field<Cpu, Integer> F = register("f", RegistersBase::getRegF, RegistersBase::setFlags);
  public static final Field<Cpu, Integer> B = register("b", RegistersBase::getRegB, RegistersBase::setRegB);
  public static final Field<Cpu, Integer> C = register("c", RegistersBase::getRegC, RegistersBase::setRegC);
  public static final Field<Cpu, Integer> D = register("d", RegistersBase::getRegD, RegistersBase::setRegD);
  public static final Field<Cpu, Integer> E = register("e", RegistersBase::getRegE, RegistersBase::setRegE);
  public static final Field<Cpu, Integer> H = register("h", RegistersBase::getRegH, RegistersBase::setRegH);
  public static final Field<Cpu, Integer> L = register("l", RegistersBase::getRegL, RegistersBase::setRegL);
  public static final Field<Cpu, Integer> A_ = register("a'", RegistersBase::getRegAx, RegistersBase::setRegAx);
  public static final Field<Cpu, Integer> F_ = register("f'", RegistersBase::getRegFx, RegistersBase::setRegFx);
  public static final Field<Cpu, Integer> B_ = register("b'", RegistersBase::getRegBx, RegistersBase::setRegBx);
  public static final Field<Cpu, Integer> C_ = register("c'", RegistersBase::getRegCx, RegistersBase::setRegCx);
  public static final Field<Cpu, Integer> D_ = register("d'", RegistersBase::getRegDx, RegistersBase::setRegDx);
  public static final Field<Cpu, Integer> E_ = register("e'", RegistersBase::getRegEx, RegistersBase::setRegEx);
  public static final Field<Cpu, Integer> H_ = register("h'", RegistersBase::getRegHx, RegistersBase::setRegHx);
  public static final Field<Cpu, Integer> L_ = register("l'", RegistersBase::getRegLx, RegistersBase::setRegLx);
  public static final Field<Cpu, Integer> I = register("i", RegistersBase::getRegI, RegistersBase::setRegI);
  public static final Field<Cpu, Integer> R = register("r", RegistersBase::getRegR, RegistersBase::setRegR);
  public static final Field<Cpu, Integer> IX = register("ix", RegistersBase::getRegIX, RegistersBase::setRegIX);
  public static final Field<Cpu, Integer> IY = register("iy", RegistersBase::getRegIY, RegistersBase::setRegIY);
  public static final Field<Cpu, Integer> SP = register("sp", RegistersBase::getRegSP, RegistersBase::setRegSP);
  public static final Field<Cpu, Integer> PC = register("pc", RegistersBase::getRegPC, RegistersBase::setRegPC);
  public static final Field<Cpu, Integer> MEMPTR = register("memptr", RegistersBase::getMemPtr, RegistersBase::setMemPtr);

  public static final Field<Cpu, Integer> AF = pair("af", A, F);
  public static final Field<Cpu, Integer> BC = pair("bc", B, C);
  public static final Field<Cpu, Integer> DE = pair("de", D, E);
  public static final Field<Cpu, Integer> HL = pair("hl", H, L);
  public static final Field<Cpu, Integer> AF_ = pair("af'", A_, F_);
  public static final Field<Cpu, Integer> BC_ = pair("bc'", B_, C_);
  public static final Field<Cpu, Integer> DE_ = pair("de'", D_, E_);
  public static final Field<Cpu, Integer> HL_ = pair("hl'", H_, L_);

  public static final Field<Cpu, Boolean> IFF1 = Field.of("iff1", cpu -> registers(cpu).isIFF1(), (cpu, v) -> registers(cpu).setIFF1(v));
  public static final Field<Cpu, Boolean> IFF2 = Field.of("iff2", cpu -> registers(cpu).isIFF2(), (cpu, v) -> registers(cpu).setIFF2(v));
  /** The interrupt mode, 0, 1 or 2. */
  public static final Field<Cpu, Integer> IM = Field.of("im", cpu -> registers(cpu).getIM().ordinal(), (cpu, v) -> registers(cpu).setIM(v));
  public static final Field<Cpu, Boolean> HALTED = Field.of("halted", cpu -> registers(cpu).isHalted(), (cpu, v) -> registers(cpu).setHalted(v));
  public static final Field<Cpu, Boolean> EI_PENDING = Field.of("eiPending", cpu -> registers(cpu).isPendingEI(), (cpu, v) -> registers(cpu).setPendingEI(v));

  public static final Field<Border, Integer> BORDER = Field.of("border", Border::colour, Border::becomes);

  /** The 128's paging port, written as a program writes it, so the memory is paged as it says. */
  public static final Field<Paging128, Integer> PORT_7FFD = Field.of("port7ffd",
      machine -> machine.paging().port7ffd() & 0xff, (machine, value) -> machine.memoryPortWrite(0x7ffd, (byte) (int) value));
  public static final Field<PagingPlus3, Integer> PORT_1FFD = Field.of("port1ffd",
      machine -> machine.paging().port1ffd() & 0xff, (machine, value) -> machine.memoryPort2Write(0x1ffd, (byte) (int) value));

  public static final PageField<SpectrumMemory> PAGE = PageField.of(
      (memory, bank) -> memory.ram(bank).bytes.clone(), (memory, bank, bytes) -> memory.ram(bank).fill(bytes));

  public static final Field<SpectrumZ80Clock, Integer> TSTATES = Field.of("tstates", SpectrumZ80Clock::getTStates, SpectrumZ80Clock::setTStates);

  public static final Field<Machine, Boolean> ISSUE_2 = Field.of("issue2", machine -> machine.unit.issue2, (machine, value) -> machine.unit.issue2 = value);

  private CoreFields() {
  }

  private static RegistersBase registers(Cpu cpu) {
    return new RegistersBase(cpu.getOoz80().getState());
  }

  private static Field<Cpu, Integer> register(String label, ToIntFunction<RegistersBase> get, BiConsumer<RegistersBase, Integer> set) {
    return Field.of(label, cpu -> get.applyAsInt(registers(cpu)), (cpu, value) -> set.accept(registers(cpu), value));
  }

  private static Field<Cpu, Integer> pair(String label, Field<Cpu, Integer> high, Field<Cpu, Integer> low) {
    return Field.of(label, cpu -> high.get(cpu) << 8 | low.get(cpu), (cpu, value) -> {
      high.set(cpu, value >> 8 & 0xff);
      low.set(cpu, value & 0xff);
    });
  }
}
