public class Planet implements Cloneable {

    private int planetID;
    private int owner;
    private int numShips;
    private int growthRate;
    private double x, y;

    /**
     * Initializes a planet.
     * @param planetID
     * @param owner
     * @param numShips
     * @param growthRate
     * @param x
     * @param y
     */
    public Planet(int planetID, int owner, int numShips, int growthRate, double x, double y) {
        this.planetID = planetID;
        this.owner = owner;
        this.numShips = numShips;
        this.growthRate = growthRate;
        this.x = x;
        this.y = y;
    }

    // Accessors and simple modification functions. These should be mostly
    // self-explanatory.
    public int getPlanetID() {
	return planetID;
    }

    public int getOwner() {
	return owner;
    }

    public int getNumShips() {
	return numShips;
    }

    public int getGrowthRate() {
	return growthRate;
    }

    public double getX() {
	return x;
    }

    public double getY() {
	return y;
    }

    public void setOwner(int newOwner) {
	owner = newOwner;
    }

    public void setNumShips(int newNumShips) {
	numShips = newNumShips;
    }

    public void addShips(int amount) {
	numShips += amount;
    }

    public void removeShips(int amount) {
	numShips -= amount;
    }

    @Override
    public String toString() {
        return "Planet{" + "planetID=" + planetID + "owner=" + owner + "numShips=" + numShips + "growthRate=" + growthRate + "x=" + x + "y=" + y + '}';
    }

    
}
