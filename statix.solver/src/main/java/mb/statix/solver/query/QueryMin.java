package mb.statix.solver.query;

import java.io.Serializable;

import mb.nabl2.terms.ITerm;
import mb.nabl2.terms.substitution.ISubstitution;
import mb.nabl2.util.TermFormatter;
import mb.statix.spec.Rule;

public class QueryMin implements IQueryMin, Serializable {
    private static final long serialVersionUID = 1L;

    private final Rule pathConstraint;
    private final Rule dataConstraint;

    public QueryMin(Rule pathConstraint, Rule dataConstraint) {
        this.pathConstraint = pathConstraint;
        this.dataConstraint = dataConstraint;
    }

    @Override public IQueryMin apply(ISubstitution.Immutable subst) {
        return new QueryMin(pathConstraint.apply(subst), dataConstraint.apply(subst));
    }

    @Override public Rule getLabelOrder() {
        return pathConstraint;
    }

    @Override public Rule getDataEquiv() {
        return dataConstraint;
    }

    @Override public String toString(TermFormatter termToString) {
        final StringBuilder sb = new StringBuilder();
        sb.append("min ");
        sb.append(pathConstraint.toString(termToString));
        sb.append(" and ");
        sb.append(dataConstraint.toString(termToString));
        return sb.toString();
    }

    @Override public String toString() {
        return toString(ITerm::toString);
    }

}