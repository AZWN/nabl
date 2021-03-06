package mb.nabl2.terms.build;

import java.io.Serializable;

import io.usethesource.capsule.Map;
import mb.nabl2.terms.IAttachments;

import javax.annotation.Nullable;

public class Attachments implements IAttachments, Serializable {
    private static final long serialVersionUID = 1L;

    private static final IAttachments EMPTY = new EmptyAttachments();

    private final Map.Immutable<Class<?>, Object> attachments;

    private Attachments(Map.Immutable<Class<?>, Object> attachments) {
        this.attachments = attachments;
    }

    @SuppressWarnings("unchecked") @Override @Nullable public <T> T get(Class<T> key) {
        return (T) attachments.get(key);
    }

    @Override public boolean isEmpty() {
        return attachments.isEmpty();
    }

    @Override public Builder toBuilder() {
        return new Builder(attachments.asTransient());
    }

    @Override public int hashCode() {
        return attachments.hashCode();
    }

    @Override public boolean equals(Object obj) {
        if(obj == null)
            return false;
        if(obj == this)
            return true;
        if(obj.getClass() != getClass())
            return false;
        Attachments other = (Attachments) obj;
        return other.attachments.equals(attachments);
    }

    public static IAttachments empty() {
        return EMPTY;
    }

    public static <T> Attachments of(Class<T> cls, T value) {
        return new Attachments(Map.Immutable.of(cls, value));
    }

    public static <T1, T2> Attachments of(Class<T1> cls1, T1 value1, Class<T2> cls2, T2 value2) {
        return new Attachments(Map.Immutable.of(cls1, value1, cls2, value2));
    }

    private static class EmptyAttachments implements IAttachments, Serializable {
        private static final long serialVersionUID = 1L;

        @Override public boolean isEmpty() {
            return true;
        }

        @Override public <T> T get(Class<T> cls) {
            return null;
        }

        @Override public Builder toBuilder() {
            return new Attachments.Builder(null);
        }

    }

    public static class Builder implements IAttachments.Builder {

        private Map.Transient<Class<?>, Object> attachments;

        private Builder(Map.Transient<Class<?>, Object> attachments) {
            this.attachments = attachments;
        }

        @Override public <T> void put(Class<T> key, T value) {
            if(attachments == null) {
                attachments = Map.Transient.of();
            }
            attachments.__put(key, value);
        }

        @Override public boolean isEmpty() {
            return attachments == null || attachments.isEmpty();
        }

        @Override public IAttachments build() {
            return isEmpty() ? EMPTY : new Attachments(attachments.freeze());
        }

        public static Builder of() {
            return new Builder(null);
        }

    }

}
