package mb.statix.generator;

import java.util.Random;

import mb.statix.generator.nodes.SearchNodes;

public class NullSearchContext implements SearchContext {

    private final Random rnd;

    public NullSearchContext(Random rnd) {
        this.rnd = rnd;
    }

    @Override public int nextNodeId() {
        return 0;
    }

    @Override public Random rnd() {
        return rnd;
    }

    @Override public void failure(SearchNodes<?> nodes) {
    }

}