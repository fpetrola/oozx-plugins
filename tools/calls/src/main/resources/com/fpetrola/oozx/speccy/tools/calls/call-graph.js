// Each routine once, with how far it runs and how often it was called; an arrow to each one it calls.
// machine: the Speccy running; calls: the CallTree watching it; listing: its Disassembly.
// node(id, label) and edge(from, to) are what gets drawn, laid out by levels once this ends.
// said, visit, routines and code come from the prelude. Ctrl+Enter runs it again.

var found = routines();

for (var address in found) {
  var routine = found[address];
  node(address, said(routine.from) + "–" + said(routine.to) + "\n×" + routine.times);
  routine.calls.forEach(function (called) { edge(address, called); });
}
