package mb.statix.solver.constraint;

import java.util.Optional;

import javax.annotation.Nullable;

import com.google.common.collect.ImmutableSet;

import mb.nabl2.terms.substitution.ISubstitution;
import mb.nabl2.terms.unification.IUnifier;
import mb.nabl2.terms.unification.PersistentUnifier;
import mb.statix.solver.ConstraintContext;
import mb.statix.solver.ConstraintResult;
import mb.statix.solver.Delay;
import mb.statix.solver.IConstraint;
import mb.statix.solver.State;

public class CTrue implements IConstraint {

    private final @Nullable IConstraint cause;

    public CTrue() {
        this(null);
    }

    public CTrue(@Nullable IConstraint cause) {
        this.cause = cause;
    }

    @Override public Optional<IConstraint> cause() {
        return Optional.ofNullable(cause);
    }

    @Override public CTrue withCause(@Nullable IConstraint cause) {
        return new CTrue(cause);
    }

    @Override public CTrue apply(ISubstitution.Immutable subst) {
        return this;
    }

    @Override public Optional<ConstraintResult> solve(State state, ConstraintContext params) throws Delay {
        return Optional.of(ConstraintResult.of(state, ImmutableSet.of()));
    }

    @Override public String toString(IUnifier unifier) {
        return "true";
    }

    @Override public String toString() {
        return toString(PersistentUnifier.Immutable.of());
    }

}