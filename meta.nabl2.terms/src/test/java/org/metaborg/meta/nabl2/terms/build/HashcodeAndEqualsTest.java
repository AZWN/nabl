package org.metaborg.meta.nabl2.terms.build;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.Arrays;
import java.util.List;

import org.immutables.serial.Serial;
import org.immutables.value.Value;
import org.junit.Test;
import org.metaborg.meta.nabl2.terms.IApplTerm;
import org.metaborg.meta.nabl2.terms.ITerm;

import com.google.common.collect.ImmutableClassToInstanceMap;
import com.google.common.collect.ImmutableList;

public class HashcodeAndEqualsTest {

    @Test public void testSameInts() {
        ITerm t1 = TB.newInt(1);
        ITerm t2 = TB.newInt(1);
        assertEquals(t1.hashCode(), t2.hashCode());
        assertTrue(t1.equals(t2));
    }

    @Test public void testDifferentInts() {
        ITerm t1 = TB.newInt(1);
        ITerm t2 = TB.newInt(2);
        assertFalse(t1.equals(t2));
    }

    @Test public void testSameStrings() {
        ITerm t1 = TB.newString("Hello!");
        ITerm t2 = TB.newString("Hello!");
        assertEquals(t1.hashCode(), t2.hashCode());
        assertTrue(t1.equals(t2));
    }

    @Test public void testDifferentStrings() {
        ITerm t1 = TB.newString("Hello!");
        ITerm t2 = TB.newString("World!");
        assertFalse(t1.equals(t2));
    }

    @Test public void testSameApplNullaryCtors() {
        ITerm t1 = TB.newAppl("Ctor");
        ITerm t2 = TB.newAppl("Ctor");
        assertEquals(t1.hashCode(), t2.hashCode());
        assertTrue(t1.equals(t2));
    }

    @Test public void testSameApplUnaryCtors() {
        ITerm t1 = TB.newAppl("Ctor", TB.newInt(1));
        ITerm t2 = TB.newAppl("Ctor", TB.newInt(1));
        assertEquals(t1.hashCode(), t2.hashCode());
        assertTrue(t1.equals(t2));
    }

    @Test public void testDifferentApplNullaryCtors() {
        ITerm t1 = TB.newAppl("Ctor1");
        ITerm t2 = TB.newAppl("Ctor2");
        assertFalse(t1.equals(t2));
    }

    @Test public void testDifferentApplArity() {
        ITerm t1 = TB.newAppl("Ctor1", TB.newInt(1));
        ITerm t2 = TB.newAppl("Ctor2", TB.newInt(1), TB.newString("Hello, world!"));
        assertFalse(t1.equals(t2));
    }

    @Test public void testSpecializedEqual() {
        ITerm t1 = ImmutableSpecializedAppl.of("Hello, world!", 42);
        ITerm t2 = ImmutableSpecializedAppl.of("Hello, world!", 42);
        assertEquals(t1.hashCode(), t2.hashCode());
        assertTrue(t1.equals(t2));
    }

    @Test public void testSpecializedAndGenericEqual() {
        ITerm t1 = ImmutableSpecializedAppl.of("Hello, world!", 42);
        ITerm t2 = TB.newAppl(SpecializedAppl.OP, TB.newString("Hello, world!"), TB.newInt(42));
        assertEquals(t1.hashCode(), t2.hashCode());
        assertTrue(t1.equals(t2));
    }

    @Test public void testGenericAndSpecializedEqual() {
        ITerm t1 = TB.newAppl(SpecializedAppl.OP, TB.newString("Hello, world!"), TB.newInt(42));
        ITerm t2 = ImmutableSpecializedAppl.of("Hello, world!", 42);
        assertEquals(t1.hashCode(), t2.hashCode());
        assertTrue(t1.equals(t2));
    }

    @Test public void testGenericApplEqualAfterSerialization() throws Exception {
        ITerm t1 = TB.newAppl(SpecializedAppl.OP, TB.newString("Hello, world!"), TB.newInt(42));
        ITerm t2 = deserialize(serialize(t1));
        assertEquals(t1.hashCode(), t2.hashCode());
        assertTrue(t1.equals(t2));
    }

    @Test public void testSpecializedApplEqualAfterSerialization() throws Exception {
        ITerm t1 = ImmutableSpecializedAppl.of("Hello, world!", 42);
        ITerm t2 = deserialize(serialize(t1));
        assertEquals(t1.hashCode(), t2.hashCode());
        assertTrue(t1.equals(t2));
    }

    @Test public void testSpecializedApplEqualsGenericAfterSerialization() throws Exception {
        ITerm t1 = deserialize(serialize(ImmutableSpecializedAppl.of("Hello, world!", 42)));
        ITerm t2 = TB.newAppl(SpecializedAppl.OP, TB.newString("Hello, world!"), TB.newInt(42));
        assertEquals(t1.hashCode(), t2.hashCode());
        assertTrue(t1.equals(t2));
    }

    @Test public void testGenericApplEqualsSepcializedAfterSerialization() throws Exception {
        ITerm t1 = deserialize(serialize(TB.newAppl(SpecializedAppl.OP, TB.newString("Hello, world!"), TB.newInt(42))));
        ITerm t2 = ImmutableSpecializedAppl.of("Hello, world!", 42);
        assertEquals(t1.hashCode(), t2.hashCode());
        assertTrue(t1.equals(t2));
    }

    @Test public void testSerializeGeneric() throws Exception {
        ITerm t = TB.newAppl(SpecializedAppl.OP, TB.newString("Hello, world!"), TB.newInt(42));
        byte[] b1 = serialize(t);
        byte[] b2 = serialize(deserialize(b1));
        assertTrue(Arrays.equals(b1, b2));
    }

    @Test public void testSerializeSpecialized() throws Exception {
        ITerm t = ImmutableSpecializedAppl.of("Hello, world!", 42);
        byte[] b1 = serialize(t);
        byte[] b2 = serialize(deserialize(b1));
        assertTrue(Arrays.equals(b1, b2));
    }


    @Value.Immutable
    @Serial.Version(42L)
    static abstract class SpecializedAppl extends AbstractApplTerm {

        static final String OP = "Specialized";

        @Value.Parameter public abstract String getFirstArg();

        @Value.Parameter public abstract int getSecondArg();

        @Override protected IApplTerm check() {
            return this;
        }

        @Override public String getOp() {
            return OP;
        }

        public List<ITerm> getArgs() {
            return ImmutableList.of(TB.newString(getFirstArg()), TB.newInt(getSecondArg()));
        }

        public IApplTerm withAttachments(ImmutableClassToInstanceMap<Object> value) {
            return ImmutableSpecializedAppl.copyOf(this).withAttachments(value);
        }

        public IApplTerm withLocked(boolean locked) {
            return ImmutableSpecializedAppl.copyOf(this).withLocked(locked);
        }

        @Override public int hashCode() {
            return super.hashCode();
        }

        @Override public boolean equals(Object other) {
            return super.equals(other);
        }

        @Override public String toString() {
            return getFirstArg() + ":" + getSecondArg();
        }

    }

    private static byte[] serialize(Object obj) throws IOException {
        try(final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
                final ObjectOutputStream objectOutputStream = new ObjectOutputStream(outputStream)) {
            objectOutputStream.writeObject(obj);
            objectOutputStream.flush();
            return outputStream.toByteArray();
        }
    }

    @SuppressWarnings("unchecked") private static <T> T deserialize(byte[] bytes)
            throws ClassNotFoundException, IOException {
        try(final ByteArrayInputStream inputStream = new ByteArrayInputStream(bytes);
                final ObjectInputStream objectInputStream = new ObjectInputStream(inputStream)) {
            return (T) objectInputStream.readObject();
        }
    }

}