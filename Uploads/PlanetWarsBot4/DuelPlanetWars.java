import java.io.BufferedReader;
import java.io.FileReader;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Contestants do not need to worry about anything in this file. This is just
 * helper code that does the boring stuff for you, so you can focus on the
 * interesting stuff. That being said, you're welcome to change anything in
 * this file if you know what you're doing.
 */
public class DuelPlanetWars {
    // Store all the planets and fleets.

    private ArrayList<Planet> planets;
    private ArrayList<Fleet> myFleets;
    private ArrayList<Fleet> enemyFleets;
    // Caching
    private int[][] distances;
    private ArrayList<Planet> myPlanets;
    private ArrayList<Planet> enemyPlanets;
    private ArrayList<Planet> neutralPlanets;
    private Planet[][] closestPlanets;
    // Temporary variable while updating
    private int updatePlanetID;
    // Debug stuff
    private boolean enableLogging = false;
    private PrintWriter log;

    /**
     * Constructs a PlanetWars object instance, given a string containing a
     * description of a game state.
     * @param gameStateString
     */
    public DuelPlanetWars(String gameStateString) {
        planets = new ArrayList<Planet>();
        myPlanets = new ArrayList<Planet>();
        enemyPlanets = new ArrayList<Planet>();
        neutralPlanets = new ArrayList<Planet>();
        myFleets = new ArrayList<Fleet>();
        enemyFleets = new ArrayList<Fleet>();
        parseGameState(gameStateString);
    }

    /**
     * Enables logging to the given PrintWriter
     * @param log
     */
    public void setLogger(PrintWriter log) {
        this.log = log;
        enableLogging = true;
    }

    /**
     * Returns the number of planets. Planets are numbered starting with 0.
     * @return
     */
    public int getNumPlanets() {
        return planets.size();
    }

    /**
     * Returns the planet with the given planet_id. There are NumPlanets()
     * planets. They are numbered starting at 0.
     * @param planetID
     * @return
     */
    public Planet getPlanet(int planetID) {
        return planets.get(planetID);
    }

    /**
     * Returns the number of fleets.
     * @return
     */
    public int getNumFleets() {
        return myFleets.size() + enemyFleets.size();
    }

    /**
     * Returns the fleet with the given fleet_id. Fleets are numbered starting
     * with 0. There are NumFleets() fleets. fleet_id's are not consistent from
     * one turn to the next.
     * @param fleetID
     * @return
     */
    public Fleet getFleet(int fleetID) {
        if (fleetID < myFleets.size()) {
            return myFleets.get(fleetID);
        } else {
            return enemyFleets.get(fleetID - myFleets.size());
        }
    }

    /**
     * Return a list of all the fleets.
     * NOTE: using getMyFleets and getEnemyFleets is quicker
     * @return
     */
    public List<Fleet> getFleets() {
        List<Fleet> fleets = new ArrayList<Fleet>(myFleets.size() + enemyFleets.size());
        fleets.addAll(myFleets);
        fleets.addAll(enemyFleets);
        return fleets;
    }

    /**
     * Return a list of all the fleets owned by the current player.
     * NOTE: returns a pointer, not a copy, so be careful.
     * @return
     */
    public List<Fleet> getMyFleets() {
        return myFleets;
    }

    /**
     * Return a list of all the fleets owned by enemy players.
     * NOTE: returns a pointer, not a copy, so be careful.
     * @return
     */
    public List<Fleet> getEnemyFleets() {
        return enemyFleets;
    }

    /**
     * Returns a list of all fleets with the given planet as destination.
     * @param dest
     * @return
     */
    public List<Fleet> getIncomingFleets(Planet dest) {
        List<Fleet> inc = new ArrayList<Fleet>();

        for (Fleet f : myFleets) {
            if (f.getDestination() == dest.getPlanetID()) {
                inc.add(f);
            }
        }

        for (Fleet f : enemyFleets) {
            if (f.getDestination() == dest.getPlanetID()) {
                inc.add(f);
            }
        }

        return inc;
    }

    /**
     * Returns a list of all fleets with the given planet as destination, sorted by the number of turns left before arrival.
     * @param dest
     * @return
     */
    public List<Fleet> getIncomingFleetsSorted(Planet dest) {
        List<Fleet> inc = getIncomingFleets(dest);
        Collections.sort(inc, Fleet.arrivalTime);
        return inc;
    }

    /**
     * Returns a list of all the planets.
     * @return
     */
    public List<Planet> getPlanets() {
        return planets;
    }

    /**
     * Return a list of all the planets owned by the current player. By
     * convention, the current player is always player number 1.
     * @return
     */
    public List<Planet> getMyPlanets() {
        if (myPlanets == null) {
            myPlanets = new ArrayList<Planet>();
            for (Planet p : planets) {
                if (p.getOwner() == 1) {
                    myPlanets.add(p);
                }
            }
        }

        return myPlanets;
    }

    /**
     * Return a list of all neutral planets.
     * @return
     */
    public List<Planet> getNeutralPlanets() {
        if (neutralPlanets == null) {
            neutralPlanets = new ArrayList<Planet>();
            for (Planet p : planets) {
                if (p.getOwner() == 0) {
                    neutralPlanets.add(p);
                }
            }
        }

        return neutralPlanets;
    }

    /**
     * Return a list of all the planets owned by rival players. This excludes
     * planets owned by the current player, as well as neutral planets.
     * @return
     */
    public List<Planet> getEnemyPlanets() {
        if (enemyPlanets == null) {
            enemyPlanets = new ArrayList<Planet>();
            for (Planet p : planets) {
                if (p.getOwner() == 2) {
                    enemyPlanets.add(p);
                }
            }
        }

        return enemyPlanets;
    }

    /**
     * Return a list of all the planets that are not owned by the current
     * player. This includes all enemy planets and neutral planets.
     * NOTE: this method is not being cached (yet), so calling it often will be less efficient than the others.
     * @return
     */
    public List<Planet> getNotMyPlanets() {
        List<Planet> r = new ArrayList<Planet>();
        r.addAll(getEnemyPlanets());
        r.addAll(getNeutralPlanets());
        return r;
    }

    /**
     * Returns a list of all planets other than p, sorted according to their distance to p.
     * @return
     */
    public Planet[] getClosestPlanets(Planet p) {
        if (closestPlanets[p.getPlanetID()][0] == null) {
            // Add all planets to the array
            int n = 0;

            for (int i = 0; i < planets.size(); i++) {
                Planet planet = planets.get(i);

                if (p != planet) {
                    closestPlanets[p.getPlanetID()][n] = planet;
                    n++;
                }
            }

            // Sort this array
            Arrays.sort(closestPlanets[p.getPlanetID()], new DistanceTo(p));
        }

        return closestPlanets[p.getPlanetID()];
    }

    /**
     * Returns the distance between two planets, rounded up to the next highest
     * integer. This is the number of discrete time steps it takes to get
     * between the two planets.
     * @param sourcePlanet
     * @param destinationPlanet
     * @return
     */
    public int getDistance(int sourcePlanet, int destinationPlanet) {
        if (sourcePlanet == destinationPlanet) {
            return 0;
        }

        if (distances[sourcePlanet][destinationPlanet] < 0) {
            Planet source = planets.get(sourcePlanet);
            Planet destination = planets.get(destinationPlanet);
            double dx = source.getX() - destination.getX();
            double dy = source.getY() - destination.getY();
            distances[sourcePlanet][destinationPlanet] = (int) Math.ceil(Math.sqrt(dx * dx + dy * dy));
        }

        return distances[sourcePlanet][destinationPlanet];
    }

    /**
     * Returns the distance between two planets, rounded up to the next highest
     * integer. This is the number of discrete time steps it takes to get
     * between the two planets.
     * @param source
     * @param destination
     * @return
     */
    public int getDistance(Planet source, Planet destination) {
        if (source == destination) {
            return 0;
        }

        if (distances[source.getPlanetID()][destination.getPlanetID()] < 0) {
            double dx = source.getX() - destination.getX();
            double dy = source.getY() - destination.getY();
            int dist = (int) Math.ceil(Math.sqrt(dx * dx + dy * dy));
            distances[source.getPlanetID()][destination.getPlanetID()] = dist;
            distances[destination.getPlanetID()][source.getPlanetID()] = dist;
        }

        return distances[source.getPlanetID()][destination.getPlanetID()];
    }

    /**
     * Sends an order to the game engine. An order is composed of a source
     * planet number, a destination planet number, and a number of ships. A
     * few things to keep in mind:
     *   * you can issue many orders per turn if you like.
     *   * the planets are numbered starting at zero, not one.
     *   * you must own the source planet. If you break this rule, the game
     *     engine kicks your bot out of the game instantly.
     *   * you can't move more ships than are currently on the source planet.
     *   * the ships will take a few turns to reach their destination. Travel
     *     is not instant. See the Distance() function for more info.
     * @param sourcePlanet
     * @param destinationPlanet
     * @param numShips
     */
    public void issueOrder(int sourcePlanet, int destinationPlanet, int numShips) {
        System.out.println(sourcePlanet + " " + destinationPlanet + " " + numShips);
        System.out.flush();
    }

    /**
     * Sends an order to the game engine. An order is composed of a source
     * planet number, a destination planet number, and a number of ships. A
     * few things to keep in mind:
     *   * you can issue many orders per turn if you like.
     *   * the planets are numbered starting at zero, not one.
     *   * you must own the source planet. If you break this rule, the game
     *     engine kicks your bot out of the game instantly.
     *   * you can't move more ships than are currently on the source planet.
     *   * the ships will take a few turns to reach their destination. Travel
     *     is not instant. See the Distance() function for more info.
     * @param source
     * @param dest
     * @param numShips
     */
    public void issueOrder(Planet source, Planet dest, int numShips) {
        System.out.println(source.getPlanetID() + " " + dest.getPlanetID() + " " + numShips);
        System.out.flush();
    }

    /**
     * Sends the game engine a message to let it know that we're done sending
     * orders. This signifies the end of our turn.
     */
    public void finishTurn() {
        System.out.println("go");
        System.out.flush();
    }

    /**
     * Returns true if the named player owns at least one planet or fleet.
     * Otherwise, the player is deemed to be dead and false is returned.
     * @param playerID
     * @return
     */
    public boolean isAlive(int playerID) {
        if ((playerID == 1 && !myFleets.isEmpty())
                || (playerID == 2 && !enemyFleets.isEmpty())) {
            return true;
        }

        for (Planet p : planets) {
            if (p.getOwner() == playerID) {
                return true;
            }
        }

        return false;
    }

    /**
     * If the game is not yet over (ie: at least two players have planets or
     * fleets remaining), returns -1. If the game is over (ie: only one player
     * is left) then that player's number is returned. If there are no
     * remaining players, then the game is a draw and 0 is returned.
     * @return
     */
    public int getWinner() {
        boolean player1Lives = !myFleets.isEmpty();
        boolean player2Lives = !enemyFleets.isEmpty();

        // If both players have fleets, there is no winner yet, otherwise we need to check the planets
        if (player1Lives && player2Lives) {
            return -1;
        }

        for (Planet p : planets) {
            player1Lives = player1Lives || p.getOwner() == 1;
            player2Lives = player2Lives || p.getOwner() == 2;
        }

        if (player1Lives && player2Lives) {
            return -1;
        } else if (player1Lives) {
            return 1;
        } else if (player2Lives) {
            return 2;
        } else {
            return 0;
        }
    }

    /**
     * Returns the number of ships that the current player has, either located
     * on planets or in flight.
     * @param playerID
     * @return
     */
    public int getNumShips(int playerID) {
        int numShips = 0;

        for (Planet p : planets) {
            if (p.getOwner() == playerID) {
                numShips += p.getNumShips();
            }
        }

        if (playerID == 1) {
            for (Fleet f : myFleets) {
                numShips += f.getNumShips();
            }
        } else if (playerID == 2) {
            for (Fleet f : enemyFleets) {
                numShips += f.getNumShips();
            }
        }

        return numShips;
    }

    /**
     * Returns the total production of the given player, i.e. the sum of all growth rates of his planets.
     * @param playerID
     * @return
     */
    public int getProduction(int playerID) {
        int production = 0;

        if (playerID == 1) {
            for (Planet p : myPlanets) {
                production += p.getGrowthRate();
            }
        } else if (playerID == 2) {
            for (Planet p : enemyPlanets) {
                production += p.getGrowthRate();
            }
        }

        return production;
    }

    /**
     * Updates the game state to reflect the one in the message. Assumes that the map (planets) doesn't change, although other planet attributes are updated.
     * @param s
     */
    public void update(String s) {
        initializeUpdate();
        String[] lines = s.split("\n");

        for (int i = 0; i < lines.length; ++i) {
            updateLine(lines[i]);
        }

        finalizeUpdate();
    }

    public void initializeUpdate() {
        // Clear the fleets, we'll be building them again
        myFleets.clear();
        enemyFleets.clear();
        updatePlanetID = 0;
    }

    public void updateLine(String line) {
        if (enableLogging) {
            log.println(line);
        }

        String s = line;

        // Remove comments if necessary
        int commentBegin = s.indexOf('#');

        if (commentBegin >= 0) {
            s = s.substring(0, commentBegin);
        }

        if (s.trim().length() == 0) {
            return;
        }

        // Parse each line
        String[] tokens = s.split(" ");

        if (tokens.length == 0) {
            return;
        }

        if (tokens[0].equals("P")) {
            if (tokens.length != 6) {
                System.err.println("Wrong number of tokens for a planet.");
                return;
            }

            int owner = Integer.parseInt(tokens[3]);
            int numShips = Integer.parseInt(tokens[4]);

            Planet p = planets.get(updatePlanetID);

            if (owner != p.getOwner()) {
                // Update the per-owner planet lists
                switch (p.getOwner()) {
                    case 0:
                        neutralPlanets.remove(p);
                        break;
                    case 1:
                        myPlanets.remove(p);
                        break;
                    case 2:
                        enemyPlanets.remove(p);
                        break;
                }

                p.setOwner(owner);

                switch (owner) {
                    case 0:
                        neutralPlanets.add(p);
                        break;
                    case 1:
                        myPlanets.add(p);
                        break;
                    case 2:
                        enemyPlanets.add(p);
                        break;
                }
            }

            p.setNumShips(numShips);

            updatePlanetID++;
        } else if (tokens[0].equals("F")) {
            if (tokens.length != 7) {
                System.err.println("Wrong number of tokens for a fleet.");
                return;
            }

            int owner = Integer.parseInt(tokens[1]);
            int numShips = Integer.parseInt(tokens[2]);
            int source = Integer.parseInt(tokens[3]);
            int destination = Integer.parseInt(tokens[4]);
            int totalTripLength = Integer.parseInt(tokens[5]);
            int turnsRemaining = Integer.parseInt(tokens[6]);

            Fleet f = new Fleet(owner,
                    numShips,
                    source,
                    destination,
                    totalTripLength,
                    turnsRemaining);

            if (owner == 1) {
                myFleets.add(f);
            } else if (owner == 2) {
                enemyFleets.add(f);
            }
        } else {
            System.err.println("Unexpected token: " + tokens);
            return;
        }
    }

    public void finalizeUpdate() {
        // Nothing, for now
    }

    /**
     * Parses a game state from a string. On success, returns 1. On failure,
     * returns 0.
     * @param s
     * @return
     */
    private int parseGameState(String s) {
        int planetID = 0;
        String[] lines = s.split("\n");
        for (int i = 0; i < lines.length; ++i) {
            String line = lines[i];
            int commentBegin = line.indexOf('#');
            if (commentBegin >= 0) {
                line = line.substring(0, commentBegin);
            }
            if (line.trim().length() == 0) {
                continue;
            }
            String[] tokens = line.split(" ");
            if (tokens.length == 0) {
                continue;
            }
            if (tokens[0].equals("P")) {
                if (tokens.length != 6) {
                    return 0;
                }
                double x = Double.parseDouble(tokens[1]);
                double y = Double.parseDouble(tokens[2]);
                int owner = Integer.parseInt(tokens[3]);
                int numShips = Integer.parseInt(tokens[4]);
                int growthRate = Integer.parseInt(tokens[5]);
                Planet p = new Planet(planetID++,
                        owner,
                        numShips,
                        growthRate,
                        x, y);
                planets.add(p);

                switch (p.getOwner()) {
                    case 0:
                        neutralPlanets.add(p);
                        break;
                    case 1:
                        myPlanets.add(p);
                        break;
                    case 2:
                        enemyPlanets.add(p);
                        break;
                }
            } else if (tokens[0].equals("F")) {
                if (tokens.length != 7) {
                    return 0;
                }
                int owner = Integer.parseInt(tokens[1]);
                int numShips = Integer.parseInt(tokens[2]);
                int source = Integer.parseInt(tokens[3]);
                int destination = Integer.parseInt(tokens[4]);
                int totalTripLength = Integer.parseInt(tokens[5]);
                int turnsRemaining = Integer.parseInt(tokens[6]);
                Fleet f = new Fleet(owner,
                        numShips,
                        source,
                        destination,
                        totalTripLength,
                        turnsRemaining);

                if (owner == 1) {
                    myFleets.add(f);
                } else if (owner == 2) {
                    enemyFleets.add(f);
                }
            } else {
                return 0;
            }
        }

        distances = new int[planets.size()][planets.size()];

        for (int i = 0; i < planets.size(); i++) {
            Arrays.fill(distances[i], -1);
        }

        if (planets.size() > 0) {
            closestPlanets = new Planet[planets.size()][planets.size() - 1];
        } else {
            closestPlanets = new Planet[0][0];
        }

        return 1;
    }

    /**
     * Loads a map from a text file. The text file contains a description of
     * the starting state of a game. See the project wiki for a description of
     * the file format. It should be called the Planet Wars Point-in-Time
     * format. On success, return 1. On failure, returns 0.
     * @param mapFilename
     * @return
     */
    private int loadMapFromFile(String mapFilename) {
        String s = "";
        BufferedReader in = null;
        try {
            in = new BufferedReader(new FileReader(mapFilename));
            int c;
            while ((c = in.read()) >= 0) {
                s += (char) c;
            }
        } catch (Exception e) {
            return 0;
        } finally {
            try {
                in.close();
            } catch (Exception e) {
                // Fucked.
            }
        }
        return parseGameState(s);
    }

    private class DistanceTo implements Comparator<Planet> {

        Planet origin;

        DistanceTo(Planet origin) {
            this.origin = origin;
        }

        public int compare(Planet p1, Planet p2) {
            return getDistance(p1, origin) - getDistance(p2, origin);
        }
    }
}
