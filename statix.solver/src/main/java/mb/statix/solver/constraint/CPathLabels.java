package mb.statix.solver.constraint;

import static mb.nabl2.terms.build.TermBuild.B;
import static mb.nabl2.terms.matching.TermMatch.M;

import java.util.Optional;

import javax.annotation.Nullable;

import mb.nabl2.terms.ITerm;
import mb.nabl2.terms.substitution.ISubstitution;
import mb.nabl2.terms.unification.IUnifier;
import mb.nabl2.util.TermFormatter;
import mb.statix.scopegraph.path.IScopePath;
import mb.statix.solver.Delay;
import mb.statix.solver.IConstraint;
import mb.statix.taico.solver.MConstraintContext;
import mb.statix.taico.solver.MConstraintResult;
import mb.statix.taico.solver.MState;

public class CPathLabels implements IConstraint {

    private final ITerm pathTerm;
    private final ITerm labelsTerm;

    private final @Nullable IConstraint cause;

    public CPathLabels(ITerm pathTerm, ITerm labelsTerm) {
        this(pathTerm, labelsTerm, null);
    }

    public CPathLabels(ITerm pathTerm, ITerm labelsTerm, @Nullable IConstraint cause) {
        this.pathTerm = pathTerm;
        this.labelsTerm = labelsTerm;
        this.cause = cause;
    }

    @Override public Optional<IConstraint> cause() {
        return Optional.ofNullable(cause);
    }

    @Override public CPathLabels withCause(@Nullable IConstraint cause) {
        return new CPathLabels(pathTerm, labelsTerm, cause);
    }

    @Override public CPathLabels apply(ISubstitution.Immutable subst) {
        return new CPathLabels(subst.apply(pathTerm), subst.apply(labelsTerm), cause);
    }
    
    @Override
    public Optional<MConstraintResult> solve(MState state, MConstraintContext params) throws Delay {
        final IUnifier unifier = state.unifier();
        if(!(unifier.isGround(pathTerm))) {
            throw Delay.ofVars(unifier.getVars(pathTerm));
        }
        
        @SuppressWarnings("unchecked") final IScopePath<ITerm, ITerm> path =
                M.blobValue(IScopePath.class).match(pathTerm, unifier).orElseThrow(
                        () -> new IllegalArgumentException("Expected path, got " + unifier.toString(pathTerm)));
        return Optional
                .of(new MConstraintResult(state, new CEqual(B.newList(path.labels()), labelsTerm, this)));
    }

    @Override public String toString(TermFormatter termToString) {
        final StringBuilder sb = new StringBuilder();
        sb.append("labels(");
        sb.append(termToString.format(pathTerm));
        sb.append(", ");
        sb.append(termToString.format(labelsTerm));
        sb.append(")");
        return sb.toString();
    }

    @Override public String toString() {
        return toString(ITerm::toString);
    }

}