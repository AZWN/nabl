package mb.statix.spoofax;

import static mb.nabl2.terms.build.TermBuild.B;

import java.util.List;
import java.util.Optional;

import org.spoofax.interpreter.core.IContext;
import org.spoofax.interpreter.core.InterpreterException;

import com.google.inject.Inject;

import mb.nabl2.terms.ITerm;
import mb.statix.modular.util.TTimings;

public class MSTX_end_run extends StatixPrimitive {

    @Inject public MSTX_end_run() {
        super(MSTX_end_run.class.getSimpleName(), 0);
    }

    @Override
    protected Optional<? extends ITerm> call(IContext env, ITerm term, List<ITerm> terms) throws InterpreterException {
        TTimings.serialize();
        return Optional.of(B.newInt(0));
    }
}