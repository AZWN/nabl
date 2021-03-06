package mb.statix.concurrent.p_raffrayi.impl;

import java.util.List;

import javax.annotation.Nullable;

import org.immutables.serial.Serial;
import org.immutables.value.Value;

import mb.statix.concurrent.p_raffrayi.IUnitResult;
import mb.statix.concurrent.p_raffrayi.IUnitStats;
import mb.statix.scopegraph.IScopeGraph;

@Value.Immutable
@Serial.Version(42L)
abstract class AUnitResult<S, L, D, R> implements IUnitResult<S, L, D, R> {

    @Value.Parameter @Override public abstract IScopeGraph.Immutable<S, L, D> scopeGraph();

    @Value.Parameter @Override public abstract @Nullable R analysis();

    @Value.Parameter @Override public abstract List<Throwable> failures();

    @Value.Parameter @Override public abstract IUnitStats stats();

}