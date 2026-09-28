// Evaluated before any script: what all of them can use.

// An address the way the view says numbers: decimal is the view's Decimal box.
function said(address) {
  return decimal ? String(address) : ("000" + address.toString(16).toUpperCase()).slice(-4);
}

// fn(call, caller, path) for every call in the tree, a caller before what it called;
// caller is null at the top, path the addresses from the top down to call.
function visit(fn) {
  function down(call, caller, path) {
    path = path.concat([call.address()]);
    fn(call, caller, path);
    call.made().forEach(function (made) { down(made, call, path); });
  }
  calls.program().forEach(function (call) { down(call, null, []); });
}

var Routines = Java.type("com.fpetrola.oozx.speccy.tools.calls.Routines");

// Each routine once, however many places called it: {address: {times, from, to, calls: [addresses], bytes, ends}}.
// from and to are the lowest and highest address run while it was running, whatever it jumped to;
// bytes and ends are read from its code, from the entry up to the first RET or JP that is not conditional.
function routines() {
  var found = {};
  visit(function (call, caller) {
    var seen = found[call.address()] || (found[call.address()] = {times: 0, from: call.from(), to: call.to(), calls: []});
    seen.times += call.times();
    seen.from = Math.min(seen.from, call.from());
    seen.to = Math.max(seen.to, call.to());
    var calling = caller && found[caller.address()].calls;
    if (calling && calling.indexOf(call.address()) < 0) calling.push(call.address());
  });
  Routines.found(calls.program(), listing).forEach(function (read) {
    found[read.address()].bytes = read.bytes();
    found[read.address()].ends = read.ends();
  });
  return found;
}

// A box with the instructions from one address to another, as the debugger lists them, at most that many.
function code(id, from, to, most) {
  var lines = listing.between(from, to);
  node(id, most ? lines.subList(0, Math.min(most, lines.size())) : lines);
}
