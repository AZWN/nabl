package mb.statix.solver.concurrent;

import org.metaborg.util.task.ICancel;
import org.metaborg.util.task.IProgress;

import mb.nabl2.terms.ITerm;
import mb.statix.scopegraph.terms.Scope;
import mb.statix.solver.IConstraint;
import mb.statix.solver.log.IDebugContext;
import mb.statix.solver.persistent.SolverResult;
import mb.statix.spec.Spec;

public class StatixTypeChecker extends AbstractTypeChecker<Scope, ITerm, ITerm, SolverResult> {

    private final StatixSolver solver;

    public StatixTypeChecker(String resource, Coordinator<Scope, ITerm, ITerm> coordinator, Spec spec,
            IConstraint constraint, IDebugContext debug, IProgress progress, ICancel cancel) {
        super(resource, coordinator);
        this.solver = new StatixSolver(resource, constraint, spec, debug, progress, cancel, this);
    }

    @Override public void run(Scope root) throws InterruptedException {
        solver.solve(root).whenComplete((result, ex) -> {
            if(ex != null) {
                failed(ex);
            } else {
                done(result);
            }
        });
    }

    @Override public String toString() {
        return "StatixTypeChecker[" + resource() + "]";
    }

}