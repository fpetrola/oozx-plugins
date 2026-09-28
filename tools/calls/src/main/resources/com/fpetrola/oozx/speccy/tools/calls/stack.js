// Where the machine is right now: the stack as a chain, the outermost call on top.

var above = null;

calls.stack().forEach(function (step, depth) {
  node(depth, said(step.address()) + "\nback to " + said(step.back()) + "\nSP " + said(step.sp()));
  if (above !== null) edge(above, depth);
  above = depth;
});
