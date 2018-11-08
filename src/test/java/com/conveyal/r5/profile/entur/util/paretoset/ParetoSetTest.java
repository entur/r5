package com.conveyal.r5.profile.entur.util.paretoset;

import org.junit.Ignore;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import static com.conveyal.r5.profile.entur.util.paretoset.ParetoFunction.createParetoFunctions;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class ParetoSetTest {

    @Test
    public void initiallyEmpty() {
        // Given a empty set
        ParetoSet<Vector> set = new ParetoSet<>(createParetoFunctions().lessThen());

        assertEquals("The initial set should be empty.", "{}", set.toString());
        assertTrue("The initial set should be empty.", set.isEmpty());
    }

    @Test
    public void addVector() {
        // Given a empty set
        ParetoSet<Vector> set = new ParetoSet<>(createParetoFunctions().lessThen());

        // When one element is added
        set.add(new Vector("V0", 5));

        // Then the element should be the only element in the set
        assertEquals("{V0[5]}", set.toString());
    }

    @Test
    public void testLessThen() {
        // Given a set with one element: [5]
        ParetoSet<Vector> set = new ParetoSet<>(createParetoFunctions().lessThen());
        set.add(new Vector("V0", 5));

        // When adding the same value
        set.add(new Vector("Not", 5));
        // Then expect no change in the set
        assertEquals("{V0[5]}", set.toString());

        // When adding a greater value
        set.add(new Vector("Not", 6));
        // Then expect no change in the set
        assertEquals("{V0[5]}", set.toString());

        // When adding the a lesser value
        set.add(new Vector("V1", 4));
        // Then the lesser value should replace the bigger one
        assertEquals("{V1[4]}", set.toString());
    }

    @Test
    public void testGreaterThen() {
        // Given a set with one element: [5]
        ParetoSet<Vector> set = new ParetoSet<>(createParetoFunctions().greaterThen());
        set.add(new Vector("V0", 5));

        // When adding the same value
        set.add(new Vector("Not", 5));
        // Then NO elements should be added to the set
        assertEquals("{V0[5]}", set.toString());

        // When adding a lesser value
        set.add(new Vector("Not", 4));
        // Then expect no change in the set
        assertEquals("{V0[5]}", set.toString());

        // When adding the a greater value
        set.add(new Vector("V1", 6));
        // Then the greather value should replace the bigger one
        assertEquals("{V1[6]}", set.toString());
    }

    @Test
    public void testDifferent() {
        // Given a set with one element: [5]
        ParetoSet<Vector> set = new ParetoSet<>(createParetoFunctions().different());
        set.add(new Vector("V0", 5));

        // When adding the same value
        set.add(new Vector("NOT ADDED", 5));
        // Then expect no change in the set
        assertEquals("{V0[5]}", set.toString());

        // When adding the a different value
        set.add(new Vector("D1", 6));
        // Then both values should be included
        assertEquals("{D1[6], V0[5]}", set.toString());

        // When adding the several more different values
        set.add(new Vector("D2", 3));
        set.add(new Vector("D3", 4));
        set.add(new Vector("D4", 8));
        // Then all values should be included
        assertEquals("{D1[6], D2[3], D3[4], D4[8], V0[5]}", set.toString());
    }

    @Test
    public void testTwoCriteriaWithLessThen() {
        // Given a set with one element with 2 criteria: [5, 5]
        // and a function where at least one value is less then to make it into the set
        ParetoSet<Vector> set = new ParetoSet<>(createParetoFunctions().lessThen().lessThen());
        Vector v0 = new Vector("V0", 5, 5);


        // Cases that does NOT make it into the set
        testNotAdded(set, v0, vector(6, 5), "Add a new vector where 1st value disqualifies it");
        testNotAdded(set, v0, vector(5, 6), "Add a new vector where 2nd value disqualifies it");
        testNotAdded(set, v0, vector(5, 5), "Add a new vector identical to the initial vector");

        // Cases that replaces the initial V0 vector
        testReplace(set, v0, vector(4, 5), "Add a new vector where 1st value qualifies it");
        testReplace(set, v0, vector(5, 4), "Add a new vector where 2st value qualifies it");

        // Cases that both vectors are kept
        keepBoth(set, v0, vector(4, 6), "First value is better, second value is worse => keep both");
        keepBoth(set, v0, vector(6, 4), "First value is worse, second value is better => keep both");
    }

    @Test
    public void testTwoCriteria_lessThen_and_different() {
        // Given a set with one element with 2 criteria: [5, 5]
        ParetoSet<Vector> set = new ParetoSet<>(createParetoFunctions().lessThen().different());
        Vector v0 = new Vector("V0", 5, 5);


        // Cases that does NOT make it into the set
        testNotAdded(set, v0, vector(6, 5), "1st value disqualifies it");
        testNotAdded(set, v0, vector(5, 5), "2nd value disqualifies it - equals v0");

        // Cases that replaces the initial V0 vector
        testReplace(set, v0, vector(4, 5), "1st value qualifies it");

        // Cases that both vectors are kept
        keepBoth(set, v0, vector(1, 7), "2nd value mutually qualifies, 1st is don´t care");
        keepBoth(set, v0, vector(5, 7), "2nd value mutually qualifies, 1st is don´t care");
        keepBoth(set, v0, vector(9, 7), "2nd value mutually qualifies, 1st is don´t care");
    }

    @Test
    public void testTwoCriteria_lessThen_and_lessThenValue() {
        // Given a set with one element with 2 criteria: [5, 5]
        ParetoSet<Vector> set = new ParetoSet<>(createParetoFunctions().lessThen().lessThen(2));
        Vector v0 = new Vector("V0", 5, 5);


        // Cases that does NOT make it into the set
        testNotAdded(set, v0, vector(6, 5), "1st value disqualifies it");
        testNotAdded(set, v0, vector(5, 3), "regarded as the same value");
        testNotAdded(set, v0, vector(5, 7), "regarded as the same value");
        testNotAdded(set, v0, vector(5, 8), "2nd value disqualifies it");

        // Cases that replaces the initial V0 vector
        testReplace(set, v0, vector(4, 7), "1st value qualifies it");
        testReplace(set, v0, vector(5, 2), "2nd value qualifies it");

        // Cases that both vectors are kept
        keepBoth(set, v0, vector(4, 8), "1st value qualifies it, 2nd does not");
        keepBoth(set, v0, vector(6, 2), "2nd value qualifies it, 1st does not");
    }

    @Test
    public void testFourCriteria() {
        // Given a set with one element with 2 criteria: [5, 5]
        // and the pareto function is: <, !=, >, <+2
        ParetoSet<Vector> set = new ParetoSet<>(
                createParetoFunctions().lessThen().different().greaterThen().lessThen(2)
        );
        Vector v0 = new Vector("V0", 5, 5, 5, 5);


        // Cases that does NOT make it into the set
        testNotAdded(set, v0, vector(6, 5, 5, 5), "1st value disqualifies it");
        testNotAdded(set, v0, vector(5, 5, 5, 5), "same as v0");
        testNotAdded(set, v0, vector(5, 5, 4, 5), "3rd value disqualifies it");
        testNotAdded(set, v0, vector(5, 5, 5, 3), "4th value disqualifies it");

        // Cases that replaces the initial V0 vector
        testReplace(set, v0, vector(4, 5, 5, 5), "1st value qualifies it");
        testReplace(set, v0, vector(5, 5, 6, 5), "3rd value qualifies it");
        testReplace(set, v0, vector(5, 5, 5, 2), "4th value qualifies it");

        // 2nd value is mutually dominant - other values does not matter
        keepBoth(set, v0, vector(5, 4, 5, 5), "2nd value mutually dominates - other values are equal");
        keepBoth(set, v0, vector(9, 4, 1, 9), "2nd value mutually dominates - other values disqualifies");
        keepBoth(set, v0, vector(1, 4, 9, 1), "2nd value mutually dominates - other values qualify");

        // Cases that both vectors are kept
        keepBoth(set, v0, vector(4, 5, 4, 5), "1st value dominates, 3rd value does not");
        keepBoth(set, v0, vector(4, 5, 5, 8), "1st value dominates, 4th value does not");
        keepBoth(set, v0, vector(4, 5, 4, 8), "1st value dominates, 3rd and 4th value do not");

        keepBoth(set, v0, vector(6, 5, 6, 5), "3rd value dominates, 1st value does not");
        keepBoth(set, v0, vector(5, 5, 6, 8), "3rd value dominates, 4th value does not");
        keepBoth(set, v0, vector(6, 5, 6, 8), "3rd value dominates, 1st and 4th value does not");

        keepBoth(set, v0, vector(6, 5, 5, 2), "4th value dominates, 1st value does not");
        keepBoth(set, v0, vector(5, 5, 4, 2), "4th value dominates, 3rd value does not");
        keepBoth(set, v0, vector(6, 5, 4, 2), "4th value dominates, 1sr and 3rd value does not");
    }

    @Test
    public void testAutoScalingOfParetoSet() {
        // Given a set with 2 criteria
        ParetoSet<Vector> set = new ParetoSet<>(
                createParetoFunctions().lessThen().lessThen()
        );

        // The initial size is set to 16.
        // Add 100 mutually dominant values
        for (int i = 1; i <= 100; i++) {
            // When a new value is added
            set.add(vector(i, 101 - i));
            // the size should match
            assertEquals(i, set.size());
        }

        // When adding a vector witch dominates all existing vectors
        set.add(vector(0, 0));
        // Then the set should shrink to size 1
        assertEquals("{Test[0, 0]}", set.toString());
    }


    @Test
    public void testAddingMultipleElements() {
        // Given a set with 2 criteria: LT and LT
        ParetoSet<Vector> set = new ParetoSet<>(
                createParetoFunctions().lessThen().lessThen()
        );
        Vector v55 = new Vector("v55", 5, 5);
        Vector v53 = new Vector("v53", 5, 3);
        Vector v44 = new Vector("v44", 4, 4);
        Vector v35 = new Vector("v35", 3, 5);
        Vector v25 = new Vector("v25", 2, 5);
        Vector v22 = new Vector("v22", 2, 2);

        // A dominant vector should replace more than one other vector
        //test(set, "v25", v25, v35);
        //test(set, "v53 v25", v53, v25, v35);

        // A dominant vector should replace more than one other vector
        //test(set, "v53 v25 v44", v53, v25, v44);
        test(set, "v22", v53, v25, v44, v22);

        // Mutually dominance
        test(set, "v53 v35", v53, v35);
        test(set, "v35 v53", v35, v53);

        // Mutually dominance with duplicates
        test(set, "v53 v35", v53, v35, v53, v35);
        test(set, "v35 v53", v35, v53, v35, v53);

        // A vector is added only once
        test(set, "v55", v55, v55);
        test(set, "v53 v35", v53, v35, v53, v35);

        // Vector [2,5] dominates [3,5], but not [5,3]
        test(set, "v25 v53", v35, v53, v25);
        test(set, "v53 v25", v53, v35, v25);
    }

    /**
     * This test is used to generate test cases. It have no
     * asserts in it - therefor the @Ignore. Instead it prints
     * a long list of tests with the results. Use it to
     * manually inspect and find test examples.
     */
    @Test
    @Ignore
    public void randomlyGenerateVectorsAndOutputResult() {
        // Given a set with 2 criteria: LT and LT
        ParetoSet<Vector> set = new ParetoSet<>(
                createParetoFunctions().lessThen().lessThen()
        );
        List<Vector> values = new ArrayList<>(Arrays.asList(
                new Vector("0", 5, 5),
                new Vector("1", 5, 3),
                new Vector("2", 3, 5),
                new Vector("3", 1, 5),
                new Vector("4", 5, 1)
        ));

        Random rnd = new Random(2);
        Set<String> results = new TreeSet<>();

        for (int i = 0; i < 200; i++) {
            List<Integer> indexes = new ArrayList<>();
            for (int j = 0; j < 4; j++) {
                indexes.add(rnd.nextInt(values.size()));
            }
            results.add(log(set, values, indexes));
        }
        results.forEach(System.out::println);
    }

    private void test(ParetoSet<Vector> set, String expected, Vector... vectorsToAdd) {
        set.clear();
        for (Vector v : vectorsToAdd) {
            // Copy vector to avoid any identity pitfalls
            set.add(new Vector(v));
        }
        assertEquals(expected, names(set, false));
    }

    private String log(ParetoSet<Vector> set, List<Vector> values, List<Integer> indexes) {
        set.clear();
        for (int index : indexes) {
            set.add(new Vector(values.get(index)));
        }
        String result = names(set, true);
        System.out.println("  " + indexes + " => " + result);
        return result;
    }

    private static String names(Iterable<Vector> set, boolean sort) {
        Stream<String> stream = StreamSupport
                .stream(set.spliterator(), false)
                .map(it -> it == null ? "null" : it.name);
        if (sort) {
            stream = stream.sorted();
        }

        return stream.collect(Collectors.joining(" "));
    }

    private static Vector vector(int a, int b) {
        return new Vector("Test", a, b);
    }

    private static Vector vector(int a, int b, int c, int d) {
        return new Vector("Test", a, b, c, d);
    }

    private static void testNotAdded(ParetoSet<Vector> set, Vector v0, Vector v1, String description) {
        test(set, v0, v1, description, v0);
    }

    private static void testReplace(ParetoSet<Vector> set, Vector v0, Vector v1, String description) {
        test(set, v0, v1, description, v1);
    }

    private static void keepBoth(ParetoSet<Vector> set, Vector v0, Vector v1, String description) {
        test(set, v0, v1, description, v0, v1);
    }

    private static void test(ParetoSet<Vector> set, Vector v0, Vector v1, String description, Vector... expected) {
        new TestCase(v0, v1, description, expected).run(set);
    }

    private static class Vector implements ParetoSortable {
        final String name;
        final int[] values;

        Vector(String name, int... values) {
            this.name = name;
            this.values = new int[values.length];

            for (int i = 0; i < values.length; ++i) {
                this.values[i] = values[i];
            }
        }

        Vector(Vector o) {
            this.name = o.name;
            values = Arrays.copyOf(o.values, o.values.length);
        }

        @Override
        public int paretoValue1() {
            return values[0];
        }

        @Override
        public int paretoValue2() {
            return values[1];
        }

        @Override
        public int paretoValue3() {
            return values[2];
        }

        @Override
        public int paretoValue4() {
            return values[3];
        }

        @Override
        public String toString() {
            return name + Arrays.toString(values);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Vector vector = (Vector) o;
            return Arrays.equals(values, vector.values);
        }

        @Override
        public int hashCode() {
            return Arrays.hashCode(values);
        }
    }

    static class TestCase {
        final Vector v0;
        final Vector v1;
        final String expected;
        final String description;

        TestCase(Vector v0, Vector v1, String description, Vector... expected) {
            this.v0 = v0;
            this.v1 = v1;
            this.expected = "{" + Arrays.stream(expected).map(Objects::toString).sorted().collect(Collectors.joining(", ")) + "}";
            this.description = description;
        }

        void run(ParetoSet<Vector> set) {
            set.clear();
            set.add(v0);
            set.add(v1);
            assertEquals(description, expected, set.toString());
        }
    }
}