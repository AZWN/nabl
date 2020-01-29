package mb.statix.solver.persistent.query;

import mb.statix.spec.Spec;
import org.metaborg.util.log.Level;

import com.google.common.collect.ImmutableList;

import mb.nabl2.terms.ITerm;
import mb.nabl2.terms.unification.ud.IUniDisunifier;
import mb.statix.scopegraph.reference.DataLeq;
import mb.statix.scopegraph.reference.ResolutionException;
import mb.statix.solver.Delay;
import mb.statix.solver.IConstraint;
import mb.statix.solver.IState;
import mb.statix.solver.completeness.IsComplete;
import mb.statix.solver.log.IDebugContext;
import mb.statix.solver.persistent.Solver;
import mb.statix.solver.query.ResolutionDelayException;
import mb.statix.spec.Rule;


class ConstraintDataLeq implements DataLeq<ITerm> {

    private final Spec spec;
    private final Rule constraint;
    private final IState.Immutable state;
    private final IsComplete isComplete;
    private final IDebugContext debug;
    private volatile Boolean alwaysTrue;

    public ConstraintDataLeq(Spec spec, Rule constraint, IState.Immutable state, IsComplete isComplete,
                             IDebugContext debug) {
        this.spec = spec;
        this.constraint = constraint;
        this.state = state;
        this.isComplete = isComplete;
        this.debug = debug;
    }

    @Override public boolean leq(ITerm datum1, ITerm datum2) throws ResolutionException, InterruptedException {
        final IUniDisunifier.Immutable unifier = state.unifier();
        try {
            final IConstraint result;
            if((result = constraint.apply(ImmutableList.of(datum1, datum2), unifier).orElse(null)) == null) {
                return false;
            }
            if(Solver.entails(spec, state, result, isComplete, debug)) {
                if(debug.isEnabled(Level.Info)) {
                    debug.info("{} shadows {}", unifier.toString(datum1), unifier.toString(datum2));
                }
                return true;
            } else {
                if(debug.isEnabled(Level.Info)) {
                    debug.info("{} does not shadow {}", unifier.toString(datum1), unifier.toString(datum2));
                }
                return false;
            }
        } catch(Delay d) {
            throw new ResolutionDelayException("Data order delayed.", d);
        }
    }

    @Override public boolean alwaysTrue() throws InterruptedException {
        if(alwaysTrue != null)
            return alwaysTrue.booleanValue();

        return alwaysTrue = constraint.isAlways(spec).orElse(false);
    }

}