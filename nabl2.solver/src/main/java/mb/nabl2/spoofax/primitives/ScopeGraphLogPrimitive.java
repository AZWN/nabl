package mb.nabl2.spoofax.primitives;

import static mb.nabl2.terms.matching.TermMatch.M;

import java.util.List;
import java.util.Optional;

import org.metaborg.util.log.ILogger;
import org.metaborg.util.log.Level;
import org.metaborg.util.log.LoggerUtils;
import org.spoofax.interpreter.core.InterpreterException;

import mb.nabl2.spoofax.analysis.IScopeGraphContext;
import mb.nabl2.terms.ITerm;

public abstract class ScopeGraphLogPrimitive extends ScopeGraphContextPrimitive {
    private static final ILogger logger = LoggerUtils.logger("org.metaborg.meta.nabl2");

    public ScopeGraphLogPrimitive(String name) {
        super(name, 1);
    }

    @Override public Optional<ITerm> call(IScopeGraphContext<?> context, ITerm term, List<ITerm> terms)
            throws InterpreterException {
        final ITerm messageTerm = terms.get(0);
        final String message = M.stringValue().match(messageTerm).orElseGet(() -> messageTerm.toString());
        logger.log(level(context), "{}", message);
        return fatal() ? Optional.empty() : Optional.of(term);
    }

    protected abstract Level level(IScopeGraphContext<?> context);

    protected boolean fatal() {
        return false;
    }

}