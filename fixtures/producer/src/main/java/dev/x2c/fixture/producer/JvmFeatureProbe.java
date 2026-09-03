package dev.x2c.fixture.producer;

import java.util.ArrayList;
import java.util.List;

/** Deterministic runtime probe for JVM bytecode that must survive class-JAR packaging and D8. */
public final class JvmFeatureProbe {
    private final int base;

    private JvmFeatureProbe(int base) {
        this.base = base;
    }

    public static String runAll() {
        List<String> passed = new ArrayList<>();
        int checksum = 0;

        JvmFeatureProbe probe = new JvmFeatureProbe(7);
        int inner = probe.new InnerAccumulator().add(5);
        require(inner == 12, "non-static inner class");
        passed.add("inner");
        checksum += inner;

        int nested = StaticMath.mix(6, 3);
        require(nested == 27, "static nested class/bit operations");
        passed.add("nested");
        checksum += nested;

        IntOperation lambda = value -> value * 3 + 1;
        int lambdaValue = lambda.apply(4);
        require(lambdaValue == 13, "lambda");
        passed.add("lambda");
        checksum += lambdaValue;

        IntOperation methodReference = StaticMath::square;
        int referenceValue = methodReference.apply(5);
        require(referenceValue == 25, "method reference");
        passed.add("methodRef");
        checksum += referenceValue;

        IntOperation anonymous = new IntOperation() {
            @Override
            public int apply(int value) {
                return value - 2;
            }
        };
        int anonymousValue = anonymous.apply(11);
        require(anonymousValue == 9, "anonymous class");
        passed.add("anonymous");
        checksum += anonymousValue;

        Box<String> box = new Box<>("generic");
        require("generic".equals(box.value()), "generic class");
        passed.add(box.value());
        checksum += box.value().length();

        NamedFeature feature = () -> "demo";
        require("default:demo".equals(feature.decoratedName()), "interface default method");
        passed.add("defaultMethod");
        checksum += feature.decoratedName().length();

        int switched = switch (Mode.ADVANCED) {
            case BASIC -> 3;
            case ADVANCED -> 11;
        };
        require(switched == 11, "enum/switch expression");
        passed.add("switch");
        checksum += switched;

        class LocalMultiplier {
            int multiply(int left, int right) {
                return left * right;
            }
        }
        int local = new LocalMultiplier().multiply(3, 4);
        require(local == 12, "local class");
        passed.add("local");
        checksum += local;

        int exceptionValue = exceptionFlow();
        require(exceptionValue == 18, "try/catch/finally");
        passed.add("exceptions");
        checksum += exceptionValue;

        int arraySum = 0;
        for (int[] row : new int[][] {{1, 2}, {3, 4}}) {
            for (int value : row) {
                arraySum += value;
            }
        }
        require(arraySum == 10, "arrays/enhanced loop");
        passed.add("arrays");
        checksum += arraySum;

        boolean logic = ((5 > 3 && 2 < 4) || false) && !(1 == 2);
        require(logic, "boolean logic");
        passed.add("logic");
        checksum += 1;

        ProbeRecord record = new ProbeRecord("record", 6);
        require(record.weight() == record.name().length(), "record");
        passed.add(record.name());
        checksum += record.weight();

        SynchronizedCounter counter = new SynchronizedCounter();
        counter.increment();
        counter.increment();
        require(counter.value() == 2, "synchronized method");
        passed.add("synchronized");
        checksum += counter.value();

        try {
            Class<?> nestedClass = Class.forName(StaticMath.class.getName(), false,
                    JvmFeatureProbe.class.getClassLoader());
            require(nestedClass == StaticMath.class, "ClassLoader identity");
            passed.add("classLoader");
            checksum += nestedClass.getSimpleName().length();
        } catch (ClassNotFoundException error) {
            throw new AssertionError("Nested class missing from dynamic JAR", error);
        }

        require(checksum == 175, "checksum");
        return "PASS checksum=" + checksum + " [" + join(passed) + "]";
    }

    private static int exceptionFlow() {
        int result = 0;
        try {
            Integer.parseInt("not-a-number");
        } catch (NumberFormatException expected) {
            result = 17;
        } finally {
            result++;
        }
        return result;
    }

    private static void require(boolean condition, String feature) {
        if (!condition) {
            throw new AssertionError("JVM feature probe failed: " + feature);
        }
    }

    private static String join(List<String> values) {
        StringBuilder result = new StringBuilder();
        for (String value : values) {
            if (result.length() > 0) {
                result.append(',');
            }
            result.append(value);
        }
        return result.toString();
    }

    private final class InnerAccumulator {
        int add(int value) {
            return base + value;
        }
    }

    private static final class StaticMath {
        static int mix(int left, int right) {
            return (left << 2) ^ right;
        }

        static int square(int value) {
            return value * value;
        }
    }

    private static final class Box<T> {
        private final T value;

        private Box(T value) {
            this.value = value;
        }

        T value() {
            return value;
        }
    }

    private static final class SynchronizedCounter {
        private int value;

        synchronized void increment() {
            value++;
        }

        synchronized int value() {
            return value;
        }
    }

    private enum Mode {
        BASIC,
        ADVANCED
    }

    private record ProbeRecord(String name, int weight) {}

    @FunctionalInterface
    private interface IntOperation {
        int apply(int value);
    }

    @FunctionalInterface
    private interface NamedFeature {
        String name();

        default String decoratedName() {
            return "default:" + name();
        }
    }
}
