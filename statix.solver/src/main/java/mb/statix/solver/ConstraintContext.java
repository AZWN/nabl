package mb.statix.solver;

import mb.nabl2.terms.ITerm;
import mb.nabl2.terms.ITermVar;
import mb.statix.scopegraph.terms.Scope;
import mb.statix.solver.completeness.IsComplete;
import mb.statix.solver.log.IDebugContext;

public class ConstraintContext {

    private final IsComplete isComplete;
    private final IDebugContext debug;

    public ConstraintContext(IsComplete isComplete, IDebugContext debug) {
        this.isComplete = isComplete;
        this.debug = debug;
    }

    public IDebugContext debug() {
        return debug;
    }

    public boolean isComplete(Scope scope, ITerm label, State state) {
        return isComplete.test(scope, label, state);
    }

    public boolean isRigid(ITermVar var, State state) {
        return !state.vars().contains(var);
    }

    public boolean isClosed(Scope scope, State state) {
        return !state.scopes().contains(scope);
    }

}