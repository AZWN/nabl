package mb.statix.solver.query;

import java.util.List;

import mb.nabl2.terms.ITerm;
import mb.nabl2.terms.matching.MatchException;
import mb.nabl2.terms.unification.UnificationException;
import mb.nabl2.util.Tuple2;
import mb.statix.scopegraph.reference.DataWF;
import mb.statix.scopegraph.reference.ResolutionException;
import mb.statix.solver.Completeness;
import mb.statix.solver.Config;
import mb.statix.solver.IDebugContext;
import mb.statix.solver.Solver;
import mb.statix.solver.State;
import mb.statix.spec.Lambda;

public class ConstraintDataWF implements DataWF<ITerm> {

    private final Lambda constraint;
    private final State state;
    private final Completeness completeness;
    private final IDebugContext debug;

    public ConstraintDataWF(Lambda constraint, State state, Completeness completeness, IDebugContext debug) {
        this.constraint = constraint;
        this.state = state;
        this.completeness = completeness;
        this.debug = debug;
    }

    public boolean wf(List<ITerm> datum) throws ResolutionException, InterruptedException {
        try {
            final Tuple2<State, Lambda> result = constraint.apply(datum, state);
            final Config config = Config.of(result._1(), result._2().getBody(), completeness);
            return Solver.entails(config, result._2().getBodyVars(), debug)
                    .orElseThrow(() -> new ResolutionException("Data well-formedness check delayed"));
        } catch(MatchException | UnificationException ex) {
            return false;
        }
    }

}