package mb.nabl2.solver.exceptions;

import mb.nabl2.scopegraph.CriticalEdgeException;

public class CriticalEdgeDelayException extends DelayException {

    private static final long serialVersionUID = 42L;

    private final CriticalEdgeException cause;

    public CriticalEdgeDelayException(CriticalEdgeException cause) {
        this.cause = cause;
    }

    @Override public CriticalEdgeException getCause() {
        return cause;
    }

}