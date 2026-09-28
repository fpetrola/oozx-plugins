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

package model.tests.tapes;

import com.fpetrola.oozx.speccy.modules.scheduler.Scheduler;
import com.fpetrola.oozx.speccy.modules.scheduler.Task;
import com.fpetrola.z80.cpu.Z80Clock;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/** A scheduler that says when each task is due, so that a test can take the clock straight there. */
final class Remembering extends Scheduler {

  private final Map<Task, Long> due = new HashMap<>();

  Remembering(Z80Clock clock) {
    super(clock);
  }

  @Override
  public void schedule(Task task, long at) {
    due.put(task, at);
    super.schedule(task, at);
  }

  @Override
  public void cancel(Task task) {
    due.remove(task);
    super.cancel(task);
  }

  /** The task of that name and when it is due, if it is scheduled. */
  Optional<Map.Entry<Task, Long>> next(String name) {
    return due.entrySet().stream().filter(entry -> entry.getKey().name().equals(name)).findFirst();
  }

  void ran(Task task) {
    due.remove(task);
  }
}
