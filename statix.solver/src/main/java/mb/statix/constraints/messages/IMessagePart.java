package mb.statix.constraints.messages;

import mb.nabl2.terms.substitution.IRenaming;
import mb.nabl2.terms.substitution.ISubstitution;
import mb.nabl2.util.TermFormatter;

public interface IMessagePart {

    IMessagePart apply(ISubstitution.Immutable subst);

    IMessagePart apply(IRenaming subst);

    String toString(TermFormatter formatter);

}