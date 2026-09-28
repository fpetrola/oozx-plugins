// Each routine with its instructions inside, the way the debugger lists them; the one about to run is selected.
// A routine is read from its entry up to the first RET or JP that is not conditional; one that runs on
// into data is cut at the first lines.

var most = 40;
var found = routines();

for (var address in found) {
  var entry = Number(address);
  code(address, entry, entry + found[address].bytes - 1, most);
  found[address].calls.forEach(function (called) { edge(address, called); });
}
