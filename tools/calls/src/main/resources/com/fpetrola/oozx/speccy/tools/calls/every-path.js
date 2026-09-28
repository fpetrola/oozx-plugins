// The tree as it was run: a routine appears once for every way it was reached, not merged.

var deepest = 6;

visit(function (call, caller, path) {
  if (path.length > deepest) return;
  node(path.join("/"), said(call.address()) + "\n×" + call.times());
  if (caller) edge(path.slice(0, -1).join("/"), path.join("/"));
});
