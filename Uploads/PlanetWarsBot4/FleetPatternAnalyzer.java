import java.util.Collection;
import java.util.HashSet;

/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
/**
 *
 * @author Sander
 */
public class FleetPatternAnalyzer {

    HashSet<FleetEntry> prevTurn;
    HashSet<FleetEntry> currentTurn;
    HashSet<Fleet> streams;

    public FleetPatternAnalyzer() {
        prevTurn = new HashSet<FleetEntry>();
        currentTurn = new HashSet<FleetEntry>();
        streams = new HashSet<Fleet>();
    }

    /**
     * Readies the analyzer for a new turn
     */
    public void newTurn() {
        // Throw away the old data, then swap the two maps
        prevTurn.clear();
        HashSet<FleetEntry> temp = prevTurn;

        prevTurn = currentTurn;
        currentTurn = temp;

        // We'll regenerate anything in here
        streams.clear();
    }

    public void addFleet(Fleet f) {
        if (f.getTurnsRemaining() == f.getTotalTripLength() - 1) {
            // This fleet was launched last turn
            FleetEntry fe = new FleetEntry(f);

            if (prevTurn.contains(fe)) {
                streams.add(f);
            }

            currentTurn.add(fe);
        }
    }

    public void addFleets(Collection<Fleet> fleets) {
        for (Fleet f : fleets) {
            addFleet(f);
        }
    }

    public HashSet<Fleet> getStreams() {
        return streams;
    }

    private class FleetEntry {

        int numShips;
        int sourcePlanet;
        int destinationPlanet;
        int totalTripLength;

        FleetEntry(Fleet f) {
            this(f.getNumShips(), f.getSource(), f.getDestination(), f.getTotalTripLength());
        }

        FleetEntry(int numShips, int sourcePlanet, int destinationPlanet, int totalTripLength) {
            this.numShips = numShips;
            this.sourcePlanet = sourcePlanet;
            this.destinationPlanet = destinationPlanet;
            this.totalTripLength = totalTripLength;
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == null) {
                return false;
            }
            if (getClass() != obj.getClass()) {
                return false;
            }
            final FleetEntry other = (FleetEntry) obj;
            if (this.numShips != other.numShips) {
                return false;
            }
            if (this.sourcePlanet != other.sourcePlanet) {
                return false;
            }
            if (this.destinationPlanet != other.destinationPlanet) {
                return false;
            }
            return true;
        }

        @Override
        public int hashCode() {
            int hash = 3;
            hash = 23 * hash + this.numShips;
            hash = 23 * hash + this.sourcePlanet;
            hash = 23 * hash + this.destinationPlanet;
            return hash;
        }
    }
}
