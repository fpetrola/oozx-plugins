// The program by where it sits in memory: one box per block, an arrow when code in one calls into another.

var size = 0x1000;
var blocks = {};

function blockOf(address) {
  var start = address - address % size;
  blocks[start] = blocks[start] || {};
  return start;
}

visit(function (call, caller) {
  var block = blockOf(call.address());
  blocks[block][call.address()] = true;
  if (caller && blockOf(caller.address()) !== block) edge(blockOf(caller.address()), block);
});

for (var start in blocks) {
  var begins = Number(start);
  node(start, said(begins) + "–" + said(begins + size - 1) + "\n" + Object.keys(blocks[start]).length + " routines");
}
