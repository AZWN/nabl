package mb.statix.concurrent.p_raffrayi.impl;

import org.metaborg.util.task.ICancel;

import mb.nabl2.util.collections.MultiSet;
import mb.statix.concurrent.actors.IActorRef;
import mb.statix.concurrent.actors.deadlock.Clock;
import mb.statix.concurrent.p_raffrayi.ITypeChecker;
import mb.statix.concurrent.p_raffrayi.impl.tokens.IWaitFor;

/**
 * Protocol accepted by the broker, from units
 */
public interface IUnitContext<S, L, D, R> {

    ICancel cancel();

    S makeScope(String name);

    IActorRef<? extends IUnit<S, L, D, R>> owner(S scope);

    IActorRef<? extends IUnit<S, L, D, R>> add(String id, ITypeChecker<S, L, D, R> unitChecker, S root);

    //////////////////////////////////////////////////////////////////////////
    // Deadlock detection
    //////////////////////////////////////////////////////////////////////////

    void waitFor(IWaitFor<S, L, D> token, IActorRef<? extends IUnit<S, L, D, R>> unit);

    boolean isWaiting();

    boolean isWaitingFor(IWaitFor<S, L, D> token);

    MultiSet.Immutable<IWaitFor<S, L, D>> getTokens(IActorRef<? extends IUnit<S, L, D, R>> unit);

    void granted(IWaitFor<S, L, D> token, IActorRef<? extends IUnit<S, L, D, R>> unit);

    void suspended(Clock<IActorRef<? extends IUnit<S, L, D, R>>> clock);

}