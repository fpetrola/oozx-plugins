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


package com.fpetrola.oozx.speccy.devices.spec256;

import com.fpetrola.z80.cpu.OOZ80;
import com.fpetrola.z80.registers.RegisterName;

import java.util.function.Supplier;

/**
 * Nine processors where a machine has one: the machine's own, and eight that follow it a step
 * ahead through the same instructions over their own memories.
 * <p>
 * A follower is given the machine's program counter before every instruction, so it always
 * decodes what the machine is about to run and can never wander off; what it keeps is what it
 * moved, and on eight planes that is the colour of a pixel. It counts nobody's time, answers no
 * port, and is invisible to everything that asks this processor about itself: the state handed
 * out here is the machine's.
 * <p>
 * The eight go first and the machine last.
 */
public final class LockstepZ80 extends OOZ80 {
  /** What a follower has of its own, in this order and two bytes each in what {@link #registers} says. */
  private static final RegisterName[] ITS_OWN = {RegisterName.AF, RegisterName.BC, RegisterName.DE, RegisterName.HL,
      RegisterName.IX, RegisterName.IY, RegisterName.SP, RegisterName.AFx, RegisterName.BCx, RegisterName.DEx,
      RegisterName.HLx, RegisterName.MEMPTR};

  private final OOZ80[] followers;
  private final Alignment.Aligned[] aligned;
  /** What the followers start from over the copy of the machine, asked each time they start. */
  private final Supplier<byte[]> startingFrom;
  private boolean following;

  public LockstepZ80(OOZ80 one, OOZ80[] followers, Alignment alignment, Supplier<byte[]> startingFrom) {
    super(one);
    this.followers = followers;
    this.startingFrom = startingFrom;
    aligned = new Alignment.Aligned[followers.length];
    for (int i = 0; i < followers.length; i++) aligned[i] = alignment.between(state, followers[i].getState());
  }

  @Override
  public void execute() {
    if (!following) startFollowing();
    takenByAll(OOZ80::execute);
    super.execute();
  }

  @Override
  public void interruption() {
    takenByAll(OOZ80::interruption);
    super.interruption();
  }

  @Override
  public void nmi() {
    takenByAll(OOZ80::nmi);
    super.nmi();
  }

  @Override
  public void reset() {
    super.reset();
    for (OOZ80 follower : followers) follower.reset();
    following = false;
  }

  /** The followers start again before the next instruction. */
  public void startAgain() {
    following = false;
  }

  /** What the eight have in their registers, as they start from it; nothing before they start, when they are the machine's. */
  public byte[] registers() {
    if (!following) return null;
    byte[] all = new byte[followers.length * ITS_OWN.length * 2];
    int at = 0;
    for (OOZ80 follower : followers) {
      for (RegisterName name : ITS_OWN) {
        int value = follower.getState().getRegister(name).read();
        all[at++] = (byte) value;
        all[at++] = (byte) (value >> 8);
      }
    }
    return all;
  }

  /**
   * Followers start out as copies of the processor they follow, and do it here rather than when
   * they were built: a machine moved onto this processor is given the registers it had afterwards.
   * What a snapshot said each had of its own goes over the copy.
   */
  private void startFollowing() {
    following = true;
    for (OOZ80 follower : followers) follower.getState().takeFrom(state);
    byte[] said = startingFrom.get();
    if (said == null || said.length != followers.length * ITS_OWN.length * 2) return;
    int at = 0;
    for (OOZ80 follower : followers) {
      for (RegisterName name : ITS_OWN) {
        follower.getState().getRegister(name).write(said[at] & 0xff | (said[at + 1] & 0xff) << 8);
        at += 2;
      }
    }
  }

  private void takenByAll(java.util.function.Consumer<OOZ80> what) {
    for (int i = 0; i < followers.length; i++) {
      aligned[i].now();
      what.accept(followers[i]);
    }
  }
}
