package mb.nabl2.terms.build;

import java.util.Objects;

import org.immutables.serial.Serial;
import org.immutables.value.Value;

import com.google.common.base.Preconditions;

import io.usethesource.capsule.Set;
import mb.nabl2.terms.IListTerm;
import mb.nabl2.terms.ITerm;
import mb.nabl2.terms.ITermVar;

@Value.Immutable(builder = true, copy = true, prehash = false, lazyhash = false)
@Serial.Version(value = 42L)
public abstract class ATermVar extends AbstractTerm implements ITermVar {

    @Value.Derived @Override public int getMinSize() {
        return 0;
    }

    @Value.Parameter @Override public abstract String getResource();

    @Value.Parameter @Override public abstract String getName();

    @Value.Check protected void check() {
        Preconditions.checkState(!(getResource().isEmpty() && getName().isEmpty()),
                "'resource' and 'name' cannot both be empty");
    }

    @Override public boolean isGround() {
        return false;
    }

    @Override public Set.Immutable<ITermVar> getVars() {
        return Set.Immutable.of(this);
    }

    @Override public <T> T match(ITerm.Cases<T> cases) {
        return cases.caseVar(this);
    }

    @Override public <T, E extends Throwable> T matchOrThrow(ITerm.CheckedCases<T, E> cases) throws E {
        return cases.caseVar(this);
    }

    @Override public <T> T match(IListTerm.Cases<T> cases) {
        return cases.caseVar(this);
    }

    @Override public <T, E extends Throwable> T matchOrThrow(IListTerm.CheckedCases<T, E> cases) throws E {
        return cases.caseVar(this);
    }

    private volatile int hashCode;

    @Override public int hashCode() {
        int result = hashCode;
        if(result == 0) {
            result = Objects.hash(getResource(), getName());
            hashCode = result;
        }
        return result;
    }

    @Override public boolean equals(Object other) {
        if(this == other)
            return true;
        if(!(other instanceof ITermVar))
            return false;
        ITermVar that = (ITermVar) other;
        if(this.hashCode() != that.hashCode())
            return false;
        // @formatter:off
        return Objects.equals(this.getResource(), that.getResource())
            && Objects.equals(this.getName(), that.getName());
        // @formatter:on
    }

    @Override public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("?");
        if(!getResource().isEmpty()) {
            sb.append(getResource());
            sb.append("-");
        }
        sb.append(getName());
        return sb.toString();
    }

}
