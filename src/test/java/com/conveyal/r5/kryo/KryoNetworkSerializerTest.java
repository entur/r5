package com.conveyal.r5.kryo;

import com.conveyal.gtfs.GTFSFeed;
import com.conveyal.r5.analyst.scenario.FakeGraph;
import com.conveyal.r5.diff.ObjectDiffer;
import com.conveyal.r5.streets.IntHashGrid;
import com.conveyal.r5.transit.TransportNetwork;
import com.esotericsoftware.kryo.Kryo;
import com.google.common.cache.LoadingCache;
import org.junit.Test;

import java.io.File;
import java.util.BitSet;

import static com.conveyal.r5.analyst.scenario.FakeGraph.buildNetwork;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

/**
 * Tests that serialization and deserialization of TransportNetworks functions as expected and does not corrupt objects.
 * Created by abyrd on 2018-11-05
 */
public class KryoNetworkSerializerTest {

    /**
     * We do not cross-check the result using the checksum, because the checksum is performed using serialization.
     */
    @Test
    public void testRoundTrip () throws Exception {

        // Build a network, including distance tables and a linked grid pointset for Analysis.
        TransportNetwork originalNetwork = buildNetwork(FakeGraph.TransitNetwork.MULTIPLE_LINES);
        originalNetwork.rebuildLinkedGridPointSet();

        // Save the network to a temporary file on disk.
        File tempFile = File.createTempFile("r5-serialization-test-", ".dat");
        tempFile.deleteOnExit();
        KryoNetworkSerializer.write(originalNetwork, tempFile);

        // Re-load the saved network, and confirm that the re-loaded graph is identical to the built one.
        // Reading the file also rebuilds transient indexes and primes the linkage cache with any saved linkage.
        TransportNetwork copiedNetwork1 = KryoNetworkSerializer.read(tempFile);
        assertNoDifferences(originalNetwork, copiedNetwork1);

        // Load the graph again and confirm that the second loaded graph is identical to the first.
        TransportNetwork copiedNetwork2 = KryoNetworkSerializer.read(tempFile);
        copiedNetwork2.rebuildTransientIndexes();
        assertNoDifferences(copiedNetwork1, copiedNetwork2);

    }

    /**
     * Create an ObjectDiffer configured to work on R5 TransportNetworks.
     * Make some exclusions for classes that are inherently transient or contain unordered lists we can't yet compare.
     * Apply the ObjectDiffer to two TransportNetworks and assert that there are no differences between them.
     */
    private static void assertNoDifferences(TransportNetwork a, TransportNetwork b) {
        ObjectDiffer objectDiffer = new ObjectDiffer();
        // Skip some transient fields on StreetLayer and TransitLayer.
        // FIXME these should not be fields on the resultant objects, they are only used when building the layer.
        objectDiffer.ignoreFields("permissionLabeler", "stressLabeler", "typeOfEdgeLabeler", "speedLabeler", "osm", "stopForIndex");
        // Skip the feed field on GTFS model objects, which is also transient.
        // FIXME do we actually use that field for anything? Should we remove it from gtfs-lib?
        objectDiffer.ignoreFields("feed");
        // Skip the somewhat unnecessary spatial index field on PointSets - these contain unordered lists
        // (and should probably be eliminated on gridded point sets).
        objectDiffer.ignoreFields("spatialIndex");
        // Skip the linkage LoadingCache which has no comparison method defined and can have its values evicted.
        objectDiffer.ignoreFields("linkageCache");
        objectDiffer.useEquals(BitSet.class);
        // IntHashGrid contains unordered lists of elements in each bin. Lists are compared as ordered.
        objectDiffer.ignoreClasses(IntHashGrid.class);
        objectDiffer.compareTwoObjects(a, b);
        objectDiffer.printDifferences();
        assertFalse(objectDiffer.hasDifferences());
    }

}
