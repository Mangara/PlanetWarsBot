
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;
import java.util.Map.Entry;
import java.util.SortedMap;
import java.util.TreeMap;

/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
/**
 *
 * @author Sander
 */
public class Prediction2 {

    private Planet planet;
    private SortedMap<Integer, Force> forces;
    private List<DataPoint> points; // sorted by turn
    private boolean valid; // Whether the predictions have been updates since the last modifications

    public Prediction2(Planet planet) {
        this.planet = planet;
        forces = new TreeMap<Integer, Force>(); // TODO - Optimization: Would SkipLists be more efficient?
        points = new ArrayList<DataPoint>(); // TODO - Optimization: maybe store as array instead of List, to speed up lookups? We aren't modifying the points anyway.
        valid = false;
    }

    /**
     * Returns the predicted owner of the planet in the specified number of turns, where 0 indicates the current turn.
     * @param turn
     * @return
     */
    public int getOwnerAt(int turn) {
        if (!valid) {
            throw new IllegalStateException("Predictions are currently invalid");
        }

        return getPointBefore(turn).owner;
    }

    /**
     * Returns the predicted number of ships on the planet in the specified number of turns, where 0 indicates the current turn.
     * @param turn
     * @return
     */
    public int getNumShipsAt(int turn) {
        return getNumShipsAt(turn, false);
    }

    /**
     * Returns the predicted number of ships on the planet in the specified number of turns, where 0 indicates the current turn.
     * If signed is false, the result is always positive, otherwise it is negative if we do not own the planet.
     * Note that this does not allow you to decide who owns the planet if it has 0 ships.
     * @param turn
     * @param signed
     * @return
     */
    public int getNumShipsAt(int turn, boolean signed) {
        if (!valid) {
            throw new IllegalStateException("Predictions are currently invalid");
        }

        DataPoint lastPoint = getPointBefore(turn);

        int nShips = lastPoint.numShips;

        // Apply growth rate if the planet is not neutral
        if (lastPoint.owner != 0) {
            nShips += (turn - lastPoint.turn) * planet.getGrowthRate();
        }

        // Signed returns a negative value if the ships are not ours
        if (signed && lastPoint.owner != 1) {
            nShips *= -1;
        }

        return nShips;
    }

    /**
     * Returns true iff this planet is predicted to be owned by the player at some point in the future.
     * @return
     */
    public boolean isMineSometime() {
        if (!valid) {
            throw new IllegalStateException("Predictions are currently invalid");
        }

        for (DataPoint d : points) {
            if (d.owner == 1) {
                return true;
            }
        }

        return false;
    }

    /**
     * Returns a pair, consisting of the turn after the last turn that the planet is in our hands and the number of ships required to defend it against all incoming attacks. If there is no such turn, this method returns null.
     * @return
     */
    public Pair<Integer, Integer> getLastHostileTakeOver() {
        if (!valid) {
            throw new IllegalStateException("Predictions are currently invalid");
        }

        if (points.get(points.size() - 1).owner != 2) {
            // There is no last hostile take-over: we own the planet at the end of the prediction
            return null;
        }

        ListIterator<DataPoint> it = points.listIterator(points.size() - 1);
        DataPoint afterTakeOver = null;
        int index = points.size();

        while (it.hasPrevious()) {
            DataPoint d = it.previous();
            index--;

            if (d.owner == 0) {
                // This planet wasn't taken from us, it was neutral before
                return null;
            }

            if (d.owner == 1) {
                it.next(); // returns d
                afterTakeOver = it.next();
                break;
            }
        }

        if (afterTakeOver == null) {
            // This planet was never ours
            return null;
        } else {
            // Compute the number of ships required to keep this planet from falling into enemy hands
            int shipsRequired = afterTakeOver.numShips;

            if (shipsRequired > 0) {
                addIncomingFleet(1, shipsRequired, afterTakeOver.turn, true);
            }

            while (points.get(points.size() - 1).owner != 1) {
                if (MyBot.DEBUG_DEFENSE) {
                    MyBot.log.println("Checking... " + index + " points: " + points + " takeOverTurn: " + afterTakeOver.turn + " shReq: " + shipsRequired);
                }

                for (int i = index + 1; i < points.size(); i++) {
                    DataPoint d = points.get(i);

                    if (d.owner == 2) {
                        index = i;
                        shipsRequired += d.numShips;
                        addIncomingFleet(1, d.numShips, afterTakeOver.turn, true);
                        break;
                    }
                }
            }

            if (shipsRequired > 0) {
                removeIncomingFleet(1, shipsRequired, afterTakeOver.turn, true);
            }

            return new Pair<Integer, Integer>(afterTakeOver.turn, shipsRequired);
        }
    }

    /**
     * Returns the number of ships we can safely send away from this planet in the given number of turns, without losing it to forces already on the field.
     * Returns 0 if the planet is not ours in the given number of turns, or if we can't send any forces without losing the planet.
     * @param turn
     * @return
     */
    public int getSurplusAt(int turn) {
        if (!valid) {
            throw new IllegalStateException("Predictions are currently invalid");
        }

        if (points.get(points.size() - 1).owner != 1) {
            // We're losing this planet, so no forces to spare.
            return 0;
        }

        int pointBeforeIndex = getPointBeforeIndex(turn);
        DataPoint lastPoint = points.get(pointBeforeIndex);

        if (lastPoint.owner != 1) {
            // We don't own the planet at this turn, so no ships available
            return 0;
        }

        // Calculate the number of ships on the planet at the specified turn
        int surplus = lastPoint.numShips + (turn - lastPoint.turn) * planet.getGrowthRate();
        boolean lose = false; // Whether we lose this planet at some point in the future. We'll get it back after that, but if we lose it, we can't let the forces go to 0 or the enemy keeps control.

        for (int i = pointBeforeIndex + 1; i < points.size(); i++) {
            if (points.get(i).owner == 2) {
                lose = true;
            } else if (points.get(i).owner == 1) {
                surplus = Math.min(surplus, points.get(i).numShips);
            }
        }

        if (lose) {
            surplus--;
        }

        return Math.max(surplus, 0);
    }

    /**
     * Returns the size of the force required to capture this planet at the specified turn and keep it afterwards.
     * Precondition: i > 0
     * @param turn
     */
    public int getAttackForceRequiredAt(int turn) {
        int shipsNeeded = 0;
        int owner = getOwnerAt(turn - 1);
        Force incoming = forces.get(turn);
        int incoming1 = 0;
        int incoming2 = 0;

        if (incoming != null) {
            incoming1 = incoming.ships1;
            incoming2 = incoming.ships2;
        }

        if (owner == 0) {
            shipsNeeded = Math.max(getNumShipsAt(turn - 1), incoming2) + 1;
            shipsNeeded = Math.max(shipsNeeded - incoming1, 0);
        } else if (owner == 1) {
            shipsNeeded = 0;
        } else if (owner == 2) {
            shipsNeeded = getNumShipsAt(turn) + 1;
        }

        if (MyBot.DEBUG_ATTACK && MyBot.DEBUG_PREDICTIONS) {
            MyBot.log.println("Initial prediction:");
            for (int i = 0; i < 200; i++) {
                MyBot.log.print(getNumShipsAt(i, true) + ", ");
            }
            MyBot.log.println();
        }

        if (shipsNeeded > 0) {
            addIncomingFleet(1, shipsNeeded, turn, true);
        }

        while (points.get(points.size() - 1).owner != 1) {
            for (DataPoint dp : points) {
                if (dp.turn < turn) {
                    continue;
                }

                if (dp.owner == 2) {
                    shipsNeeded += dp.numShips;
                    addIncomingFleet(1, dp.numShips, turn, true);
                    break;
                }
            }
        }

        if (MyBot.DEBUG_ATTACK && MyBot.DEBUG_PREDICTIONS) {
            MyBot.log.println("Updated prediction:");
            for (int i = 0; i < 200; i++) {
                MyBot.log.print(getNumShipsAt(i, true) + ", ");
            }
            MyBot.log.println();
            MyBot.log.println("Ships needed: " + shipsNeeded);
        }

        // Restore this prediction to its previous state
        if (shipsNeeded > 0) {
            removeIncomingFleet(1, shipsNeeded, turn, true);
        }

        return shipsNeeded;
    }

    /**
     * Returns the turns at which a force
     * @return
     */
    public SortedMap<Integer, Force> getIncomingForces() {
        return forces;
    }

    /**
     * Adds an additional fleet to be used in the predictions.
     * If update is true, the predictions will be valid after this addition, otherwise they will be invalid.
     * Note: the destination of the given fleet must correspond to the planet which is being simulated.
     * @param f
     * @param update
     */
    public void addIncomingFleet(Fleet f, boolean update) {
        assert f.getDestination() == planet.getPlanetID();

        addIncomingFleet(f.getOwner(), f.getNumShips(), f.getTurnsRemaining(), update);
    }

    /**
     * Adds an additional fleet with the specified owner, numShips and turnsRemaining to be used in the predictions.
     * If update is true, the predictions will be valid after this addition, otherwise they will be invalid.
     * @param owner
     * @param numShips
     * @param turnsRemaining
     * @param update
     */
    public void addIncomingFleet(int owner, int numShips, int turnsRemaining, boolean update) {
        Force f = forces.get(turnsRemaining);

        if (f == null) {
            // There is no incoming force yet on this turn, create it
            f = new Force(owner, numShips);
            forces.put(turnsRemaining, f);
        } else {
            f.addShips(owner, numShips);
        }

        if (update) {
            if (valid) {
                updatePredictionsFrom(turnsRemaining);
            } else {
                computePredictions();
            }
        } else {
            valid = false;
        }
    }

    /**
     * Removes an incoming fleet from the predictions.
     * If update is true, the predictions will be valid after this addition, otherwise they will be invalid.
     * @param f
     * @param update
     */
    public void removeIncomingFleet(Fleet f, boolean update) {
        removeIncomingFleet(f.getOwner(), f.getNumShips(), f.getTurnsRemaining(), update);
    }

    /**
     * Removes an incoming fleet from the predictions.
     * If update is true, the predictions will be valid after this addition, otherwise they will be invalid.
     * @param owner
     * @param numShips
     * @param turnsRemaining
     * @param update
     */
    public void removeIncomingFleet(int owner, int numShips, int turnsRemaining, boolean update) {
        Force f = forces.get(turnsRemaining);

        if (f == null) {
            throw new IllegalArgumentException("Asked to remove (" + owner + ", " + numShips + ", " + turnsRemaining + "), but no such fleet found: " + forces);
        } else {
            f.removeShips(owner, numShips);

            if (f.ships1 == 0 && f.ships2 == 0) {
                forces.remove(turnsRemaining);
            }
        }

        if (update) {
            if (valid) {
                updatePredictionsFrom(turnsRemaining);
            } else {
                computePredictions();
            }
        } else {
            valid = false;
        }
    }

    /**
     * Removes the specified number of ships from this planet at the specified turn and updates the predictions accordingly.
     * Note: the planet should be in our control at the specified turn and the number of ships on it should be at least the specified number of ships.
     * @param numShips
     * @param turn
     */
    public void addOutgoingFleet(int owner, int numShips, int turn, boolean update) {
        // Check if this is possible
        if (MyBot.DEBUG_PREDICTIONS || MyBot.DEBUG_ATTACK || MyBot.DEBUG_DEFENSE) {
            if (!valid) {
                computePredictions();
            }

            assert getOwnerAt(turn) == owner && getNumShipsAt(turn) >= numShips;
        }

        // Add a negative incoming force
        Force f = forces.get(turn);

        if (f == null) {
            // There is no incoming force yet on this turn, create it
            f = new Force(owner, -numShips);
            forces.put(turn, f);
        } else {
            f.addShips(owner, -numShips);
        }

        if (update) {
            if (valid) {
                updatePredictionsFrom(turn);
            } else {
                computePredictions();
            }
        } else {
            valid = false;
        }
    }

    /**
     * Computes the predictions, based on the added fleets.
     */
    public void computePredictions() {
        if (valid) {
            return;
        }

        PrintWriter log = MyBot.log;

        if (MyBot.DEBUG_PREDICTIONS) {
            log.println("Computing predictions for " + planet);
            if (forces.size() > 0) {
                log.println("Incoming forces: ");
                for (Force f : forces.values()) {
                    log.println(f);
                }
            }
        }

        points.clear();

        int owner = planet.getOwner();
        int nShips = planet.getNumShips();

        if (forces.get(0) == null) {
            points.add(new DataPoint(owner, nShips, 0));
        }

        int prevTurn = 0;

        for (Entry<Integer, Force> entry : forces.entrySet()) {
            int turn = entry.getKey();
            Force f = entry.getValue();

            // Apply growth rate if necessary
            if (owner != 0) {
                nShips += planet.getGrowthRate() * (turn - prevTurn);
            }

            // Handle the incoming ships
            DataPoint afterFight = resolveFight(owner, nShips, f.ships1, f.ships2);
            afterFight.turn = turn;
            points.add(afterFight);

            owner = afterFight.owner;
            nShips = afterFight.numShips;

            prevTurn = turn;
        }

        valid = true;
    }

    private void updatePredictionsFrom(int turn) {
        if (turn == 0) {
            valid = false;
            computePredictions();
        } else {
            int pointBeforeIndex = getPointBeforeIndex(turn);

            if (points.get(pointBeforeIndex).turn == turn) {
                pointBeforeIndex--;
            }

            DataPoint pointBefore = points.get(pointBeforeIndex);

            // Remove all later points; we'll regenerate them
            points.subList(pointBeforeIndex + 1, points.size()).clear();

            int owner = pointBefore.owner;
            int nShips = pointBefore.numShips;

            // Apply growth rate if the planet is not neutral
            if (owner != 0) {
                nShips += (turn - pointBefore.turn) * planet.getGrowthRate();
            }

            int prevTurn = turn;

            for (Entry<Integer, Force> entry : forces.subMap(turn, Integer.MAX_VALUE).entrySet()) {
                int currentTurn = entry.getKey();
                Force f = entry.getValue();

                // Apply growth rate if necessary
                if (owner != 0) {
                    nShips += planet.getGrowthRate() * (currentTurn - prevTurn);
                }

                // Handle the incoming ships
                DataPoint afterFight = resolveFight(owner, nShips, f.ships1, f.ships2);
                afterFight.turn = currentTurn;
                points.add(afterFight);

                owner = afterFight.owner;
                nShips = afterFight.numShips;

                prevTurn = currentTurn;
            }

            valid = true;
        }
    }

    private DataPoint resolveFight(int owner, int nShips, int arrivingPlayer1Ships, int arrivingPlayer2Ships) {
        DataPoint result = new DataPoint(owner, nShips, 0);

        if (owner == 0) {
            // The largest force gets the planet, the second largest force is subtracted from the largest force
            int winner;
            int winnerShips;
            int secondShips;

            if (arrivingPlayer1Ships > nShips) {
                if (arrivingPlayer2Ships > nShips) {
                    if (arrivingPlayer1Ships > arrivingPlayer2Ships) {
                        // 1 2 0
                        winner = 1;
                        winnerShips = arrivingPlayer1Ships;
                        secondShips = arrivingPlayer2Ships;
                    } else {
                        // 2 1 0
                        winner = 2;
                        winnerShips = arrivingPlayer2Ships;
                        secondShips = arrivingPlayer1Ships;
                    }
                } else {
                    // 1 0 2
                    winner = 1;
                    winnerShips = arrivingPlayer1Ships;
                    secondShips = nShips;
                }
            } else {
                if (arrivingPlayer2Ships > nShips) {
                    // 2 0 1
                    winner = 2;
                    winnerShips = arrivingPlayer2Ships;
                    secondShips = nShips;
                } else {
                    if (arrivingPlayer1Ships > arrivingPlayer2Ships) {
                        // 0 1 2
                        winner = 0;
                        winnerShips = nShips;
                        secondShips = arrivingPlayer1Ships;
                    } else {
                        // 0 2 1
                        winner = 0;
                        winnerShips = nShips;
                        secondShips = arrivingPlayer2Ships;
                    }
                }
            }

            if (winnerShips > secondShips) {
                result.owner = winner;
                result.numShips = winnerShips - secondShips;
            } else {
                // owner doesn't change
                result.numShips = 0;
            }
        } else if (owner == 1) {
            result.numShips += arrivingPlayer1Ships;
            result.numShips -= arrivingPlayer2Ships;

            if (result.numShips < 0) {
                result.owner = 2;
                result.numShips *= -1;
            }
        } else {
            assert owner == 2;

            result.numShips += arrivingPlayer2Ships;
            result.numShips -= arrivingPlayer1Ships;

            if (result.numShips < 0) {
                result.owner = 1;
                result.numShips *= -1;
            }
        }

        return result;
    }

    /**
     * Performs a binary search, returning the last point before the given turn, or the point at the given turn if it exists.
     * Assumption: turn >= points.get(0).getFirst()
     * @param turn
     * @return
     */
    private DataPoint getPointBefore(int turn) {
        return points.get(getPointBeforeIndex(turn));
    }

    /**
     * Performs a binary search, returning the index of the last point before the given turn, or the point at the given turn if it exists.
     * Assumption: turn >= points.get(0).getFirst()
     * @param turn
     * @return
     */
    private int getPointBeforeIndex(int turn) {
        assert turn >= points.get(0).turn : "turn smaller than first recorded interpolation point";

        // These cases will probably happen a lot, so we handle them separately
        if (turn == points.get(0).turn) {
            return 0;
        }

        if (turn >= points.get(points.size() - 1).turn) {
            return points.size() - 1;
        }

        int currentBegin = -1;
        int currentEnd = points.size();
        int middle;

        while (currentBegin < (currentEnd - 1)) {
            middle = ((currentBegin + currentEnd) / 2);
            int middleTurn = points.get(middle).turn;

            if (turn < middleTurn) {
                currentEnd = middle;
            } else if (turn >= middleTurn) {
                currentBegin = middle;
            }
        }

        return Math.max(currentBegin, 0);
    }

    private class DataPoint implements Comparable<DataPoint> {

        int owner;
        int numShips;
        int turn;

        DataPoint(int owner, int numShips, int turn) {
            this.owner = owner;
            this.numShips = numShips;
            this.turn = turn;
        }

        public int compareTo(DataPoint o) {
            return turn - o.turn;
        }

        @Override
        public String toString() {
            return "{" + owner + ", " + numShips + ", " + turn + '}';
        }
    }
}
