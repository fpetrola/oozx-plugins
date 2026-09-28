// Only the routines called the most, and the calls between them: the busy part of the program.

var shown = 12;
var found = routines();
var busiest = Object.keys(found)
    .sort(function (one, other) { return found[other].times - found[one].times; })
    .slice(0, shown);

busiest.forEach(function (address) {
  node(address, said(Number(address)) + "\n×" + found[address].times);
  found[address].calls.forEach(function (called) {
    if (busiest.indexOf(String(called)) >= 0) edge(address, called);
  });
});
