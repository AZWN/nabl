package mb.statix.search.strategies;

import mb.statix.search.*;
import mb.statix.sequences.Sequence;
import mb.statix.solver.IConstraint;

import java.util.Optional;
import java.util.function.Predicate;


/**
 * Focus on a single constraint.
 */
public final class FocusStrategy<C extends IConstraint> implements Strategy<SearchState, FocusedSearchState<C>, SearchContext> {

    private final Class<C> constraintClass;
    private final Predicate<C> predicate;

    /**
     * Initializes a new instance of the {@link FocusStrategy} class.
     *
     * @param constraintClass the class of constraints that can be focused on
     * @param predicate the predicate that determine which constraints to focus on
     */
    public FocusStrategy(Class<C> constraintClass, Predicate<C> predicate) {
        this.constraintClass = constraintClass;
        this.predicate = predicate;
    }

    @Override
    public Sequence<FocusedSearchState<C>> apply(SearchContext searchContext, SearchState input) throws InterruptedException {
        //noinspection unchecked
        Optional<C> focus = input.getConstraints().stream()
                .filter(c -> constraintClass.isAssignableFrom(c.getClass()))
                .map(c -> (C)c)
                .filter(predicate)
                .findFirst();
        if (!focus.isPresent()) { return Sequence.empty(); }

        return Sequence.of(new FocusedSearchState<>(input, focus.get()));
    }

    @Override
    public String toString() {
        return "focus(" + constraintClass.getSimpleName() + ")";
    }

}
