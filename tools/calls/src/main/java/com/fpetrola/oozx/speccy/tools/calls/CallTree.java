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


package com.fpetrola.oozx.speccy.tools.calls;

import com.fpetrola.oozx.Speccy;
import com.fpetrola.oozx.speccy.modules.z80.PcTraps;
import com.fpetrola.z80.registers.RegisterName;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * What called what, read off a running machine from outside it.
 * <p>
 * Nothing in the emulator knows this is here: it watches the fetches the way a logic analyser
 * watches a bus, and everything it says is worked out from the program counter and the stack
 * pointer. Which is the point of it - a plugin can follow a game without the machine growing
 * anything to be followed by.
 */
public class CallTree {

  /** A place a call went, under whoever called it. */
  public static final class Call {
    private final int address;
    private int times;
    private int from;
    private int to;
    private final Map<Integer, Integer> back = new LinkedHashMap<>();
    private final Map<Integer, Call> made = new LinkedHashMap<>();

    Call(int address) {
      this.address = address;
      from = to = address;
    }

    public int address() {
      return address;
    }

    public int times() {
      return times;
    }

    /** Where the calls that went here came back to, and how many took each way out. */
    /** From the lowest to the highest address run while it was the one running. */
    public int from() {
      return from;
    }

    public int to() {
      return to;
    }

    public Map<Integer, Integer> back() {
      return Map.copyOf(back);
    }

    public List<Call> made() {
      return List.copyOf(made.values());
    }

    private Call copy() {
      Call copy = new Call(address);
      copy.absorb(this);
      return copy;
    }

    private void reach(int pc) {
      from = Math.min(from, pc);
      to = Math.max(to, pc);
    }

    private boolean holds(int pc) {
      return pc >= from && pc <= to;
    }

    private void absorb(Call other) {
      times += other.times;
      other.back.forEach((at, many) -> back.merge(at, many, Integer::sum));
      reach(other.from);
      reach(other.to);
      other.made.values().forEach(this::adopt);
    }

    private void adopt(Call child) {
      made.computeIfAbsent(child.address, Call::new).absorb(child);
    }
  }

  /**
   * A routine being run, and the stack as it was on the way in, which is how it is left. One
   * entered by a jump with something pushed is only maybe a call, kept apart from its caller
   * until it is known whether it comes back to what was pushed.
   */
  private record Frame(Call routine, int sp, int back, Call maybeCalledBy) {
  }

  /** One rung of the stack as it stands: what is running, and where it will come back to. */
  public record Step(int address, int back, int sp) {
  }

  private final Speccy machine;
  private final PcTraps.Watch watching;
  private final Deque<Frame> frames = new ArrayDeque<>();
  private Call program = new Call(-1);
  private int wentTo = -1;
  private int comesBackTo;
  private int jumpedFrom;
  private int fallsTo = -1;
  private long counted;

  public CallTree(Speccy machine) {
    this.machine = machine;
    watching = machine.cpu.beforeFetch().watch(0x0000, 0xffff, this::sawFetch);
  }

  /**
   * A call counts only once the next fetch lands on its target, since a conditional one that
   * did not go looks the same when it is fetched; and a routine is left when the stack has
   * unwound past where it was entered, which is every way back out - RET in all its forms, and
   * the routines that come back by juggling the stack instead of returning.
   */
  private synchronized void sawFetch(int pc) {
    int sp = machine.cpu.getOoz80().getState().getRegister(RegisterName.SP).read();
    boolean unwound = false;
    while (!frames.isEmpty() && sp > frames.peek().sp()) {
      left(frames.pop(), pc);
      unwound = true;
    }
    Call current = frames.isEmpty() ? program : frames.peek().routine();
    if (wentTo >= 0 && pc == wentTo) {
      current = entered(current.made.computeIfAbsent(wentTo, Call::new), sp, comesBackTo, null);
      counted++;
    } else if (fallsTo >= 0 && (pc < jumpedFrom || pc > fallsTo) && !unwound && !frames.isEmpty()
        && sp < frames.peek().sp() && !current.holds(pc)) {
      current = entered(new Call(pc), sp, byteAt(sp) | byteAt(sp + 1) << 8, current);
    }
    wentTo = fallsTo = -1;
    current.reach(pc);
    int opcode = byteAt(pc);
    int next = byteAt(pc + 1);
    jumpedFrom = pc;
    if (opcode == 0xc3 || (opcode & 0xc7) == 0xc2 || opcode == 0xe9 || opcode == 0xc9
        || (opcode & 0xc7) == 0xc0) {
      fallsTo = pc + ((opcode & 0xc7) == 0xc2 ? 3 : 1) & 0xffff;
    } else if ((opcode == 0xdd || opcode == 0xfd) && next == 0xe9 || opcode == 0xed && (next == 0x45 || next == 0x4d)) {
      fallsTo = pc + 2 & 0xffff;
    }
    if (opcode == 0xcd || (opcode & 0xc7) == 0xc4) {
      wentTo = byteAt(pc + 1) | byteAt(pc + 2) << 8;
      comesBackTo = pc + 3 & 0xffff;
    } else if ((opcode & 0xc7) == 0xc7) {
      wentTo = opcode & 0x38;
      comesBackTo = pc + 1 & 0xffff;
    }
  }

  private Call entered(Call routine, int sp, int back, Call maybeCalledBy) {
    routine.times++;
    routine.back.merge(back, 1, Integer::sum);
    frames.push(new Frame(routine, sp, back, maybeCalledBy));
    return routine;
  }

  /**
   * A routine entered by a jump was a call if it came back to what had been pushed before it; if
   * the stack went past it some other way, it was the caller's own code, pushed registers and all.
   */
  private void left(Frame frame, int pc) {
    Call caller = frame.maybeCalledBy();
    if (caller == null) {
      return;
    }
    if (pc == frame.back()) {
      caller.adopt(frame.routine());
      counted++;
    } else {
      caller.reach(frame.routine().from);
      caller.reach(frame.routine().to);
      frame.routine().made.values().forEach(caller::adopt);
    }
  }

  private int byteAt(int address) {
    return machine.memory.peek(address & 0xffff) & 0xff;
  }

  /** The tree as it stands, copied so that the machine can go on running into its own. */
  public synchronized List<Call> program() {
    return program.copy().made();
  }

  /**
   * Where the machine is right now, the outermost call first: the same frames the tree is built
   * from, said as a stack rather than as a history.
   */
  public synchronized List<Step> stack() {
    List<Step> steps = new ArrayList<>();
    frames.forEach(frame -> steps.add(0, new Step(frame.routine().address(), frame.back(), frame.sp())));
    return steps;
  }

  /** How many calls have been counted: what tells a window the tree is not the one it drew. */
  public synchronized long counted() {
    return counted;
  }

  /** Forget the run so far and watch the next one. */
  public synchronized void forget() {
    program = new Call(-1);
    frames.clear();
    wentTo = fallsTo = -1;
    counted++;
  }

  /** Lets the machine go: from here on it runs as though this had never been watching. */
  public void close() {
    watching.off();
  }
}
