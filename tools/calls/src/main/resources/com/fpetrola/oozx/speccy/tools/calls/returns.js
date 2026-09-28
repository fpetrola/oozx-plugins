// Where each routine went back to: more than one place means it is called from more than one.

visit(function (call) {
  node(call.address(), said(call.address()));
  call.back().forEach(function (at, many) {
    node("back" + at, "↩ " + said(at));
    edge(call.address(), "back" + at);
  });
});
