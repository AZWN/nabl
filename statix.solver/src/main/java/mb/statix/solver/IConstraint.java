package mb.statix.solver;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.immutables.value.Value;
import org.metaborg.util.iterators.Iterables2;

import com.google.common.collect.ImmutableList;

import mb.nabl2.terms.ITerm;
import mb.nabl2.terms.ITermVar;
import mb.nabl2.terms.substitution.ISubstitution;
import mb.nabl2.util.TermFormatter;
import mb.statix.scopegraph.reference.CriticalEdge;
import mb.statix.spec.Spec;

public interface IConstraint {

    IConstraint apply(ISubstitution.Immutable subst);

    default Collection<CriticalEdge> criticalEdges(@SuppressWarnings("unused") Spec spec) {
        return ImmutableList.of();
    }

    /**
     * Return the terms that are used as constraint arguments.
     *
     * @return Constraint argument terms.
     */
    default Iterable<ITerm> terms() {
        return Iterables2.empty();
    }

    /**
     * Solve constraint
     * 
     * @param state
     *            -- monotonic from one call to the next
     * @param params
     * @return true is reduced, false if delayed
     * @throws InterruptedException
     * @throws Delay
     */
    Optional<ConstraintResult> solve(State state, ConstraintContext params) throws InterruptedException, Delay;

    String toString(TermFormatter termToString);

    Optional<IConstraint> cause();

    IConstraint withCause(IConstraint cause);

    static String toString(Iterable<? extends IConstraint> constraints, TermFormatter termToString) {
        final StringBuilder sb = new StringBuilder();
        boolean first = true;
        for(IConstraint constraint : constraints) {
            if(!first) {
                sb.append(", ");
            }
            first = false;
            sb.append(constraint.toString(termToString));
        }
        return sb.toString();
    }

    @Value.Immutable
    static abstract class AConstraintResult {

        @Value.Parameter public abstract State state();

        @Value.Parameter public abstract List<IConstraint> constraints();

        @Value.Parameter public abstract List<ITermVar> vars();

        public static ConstraintResult of(State state) {
            return ConstraintResult.of(state, ImmutableList.of(), ImmutableList.of());
        }

        public static ConstraintResult ofConstraints(State state, IConstraint... constraints) {
            return ofConstraints(state, Arrays.asList(constraints));
        }

        public static ConstraintResult ofConstraints(State state, Iterable<? extends IConstraint> constraints) {
            return ConstraintResult.of(state, ImmutableList.copyOf(constraints), ImmutableList.of());
        }

        public static ConstraintResult ofVars(State state, Iterable<? extends ITermVar> vars) {
            return ConstraintResult.of(state, ImmutableList.of(), ImmutableList.copyOf(vars));
        }

    }

}