package bot;



import java.util.Comparator;

public class Fleet implements Comparable<Fleet>, Cloneable {

    private int owner;
    private int numShips;
    private int sourcePlanet;
    private int destinationPlanet;
    private int totalTripLength;
    private int turnsRemaining;

    public Fleet(int owner, int numShips, int sourcePlanet, int destinationPlanet, int totalTripLength, int turnsRemaining) {
        this.owner = owner;
        this.numShips = numShips;
        this.sourcePlanet = sourcePlanet;
        this.destinationPlanet = destinationPlanet;
        this.totalTripLength = totalTripLength;
        this.turnsRemaining = turnsRemaining;
    }

    public Fleet(int owner, int numShips) {
        this.owner = owner;
        this.numShips = numShips;
        this.sourcePlanet = -1;
	this.destinationPlanet = -1;
	this.totalTripLength = -1;
	this.turnsRemaining = -1;
    }

    // Accessors and simple modification functions. These should be mostly
    // self-explanatory.
    public int getOwner() {
	return owner;
    }

    public int getNumShips() {
	return numShips;
    }

    public int getSource() {
	return sourcePlanet;
    }

    public int getDestination() {
	return destinationPlanet;
    }

    public int getTotalTripLength() {
	return totalTripLength;
    }

    public int getTurnsRemaining() {
	return turnsRemaining;
    }

    public void removeShips(int amount) {
	numShips -= amount;
    }

    /**
     * Subtracts one turn remaining. Call this function to make the fleet get
     * one turn closer to its destination.
     */
    public void timeStep() {
	if (turnsRemaining > 0) {
	    turnsRemaining--;
	} else {
	    turnsRemaining = 0;
	}
    }

    public int compareTo(Fleet o) {
        return this.numShips - o.numShips;
    }

    public static final Comparator<Fleet> arrivalTime = new Comparator<Fleet>() {

        public int compare(Fleet f1, Fleet f2) {
            return f1.turnsRemaining - f2.turnsRemaining;
        }
    };

    @Override
    public String toString() {
        return "Fleet{" + "owner=" + owner + "numShips=" + numShips + "sourcePlanet=" + sourcePlanet + "destinationPlanet=" + destinationPlanet + "totalTripLength=" + totalTripLength + "turnsRemaining=" + turnsRemaining + '}';
    }
}
