package mb.statix.random;

import java.util.Optional;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;

import org.metaborg.core.MetaborgException;
import org.metaborg.util.log.ILogger;
import org.metaborg.util.log.LoggerUtils;

import com.google.common.collect.ImmutableList;

import mb.statix.solver.IConstraint;
import mb.statix.solver.persistent.State;
import mb.statix.spec.Spec;

public class RandomTermGenerator implements SearchNodes<SearchState> {

    private static final ILogger log = LoggerUtils.logger(RandomTermGenerator.class);

    private final SearchNodes<SearchState> nodes;

    public RandomTermGenerator(Spec spec, IConstraint constraint, SearchStrategy<SearchState, SearchState> strategy) {

        final long seed = System.currentTimeMillis();
        log.info("random seed: {}", seed);
        log.info("strategy: {}", strategy);
        log.info("constraint: {}", constraint);

        final AtomicInteger nodeId = new AtomicInteger();
        final Random rnd = new Random(seed);
        final SearchContext ctx = new SearchContext() {

            @Override public Random rnd() {
                return rnd;
            }

            @Override public int nextNodeId() {
                return nodeId.incrementAndGet();
            }

            @Override public void addFailed(SearchNode<SearchState> node) {
            }
        };

        final SearchState initState = SearchState.of(State.of(spec), ImmutableList.of(constraint));
        this.nodes = strategy.apply(ctx, initState, null);
    }

    @Override public Optional<SearchNode<SearchState>> next() throws MetaborgException, InterruptedException {
        return nodes.next();
    }

}