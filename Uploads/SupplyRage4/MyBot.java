
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map.Entry;
import java.util.Set;

/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
/**
 *
 * @author Sander
 */
public class MyBot {

    private static int turn = 0;
    private static final int MAX_TURNS = 201;
    private static boolean defensive = true;

    public static void doTurn(DuelPlanetWars pw) {
        turn++;

        // Predict the future states of all planets
        Prediction2[] predictions = predictFuture(pw);

        if (turn == 1) {
            // Grab cheap and close neutrals
            attack(pw, predictions);
        } else {
            // Send reinforcements to the front lines
            supply(pw, predictions);

            // Attack vulnerable planets
            rage(pw, predictions);
        }

        return;
    }

    public static void main(String[] args) throws IOException {
        String line;
        String message = "";

        DuelPlanetWars pw = null;
        BufferedReader in = new BufferedReader(new InputStreamReader(System.in));

        while ((line = in.readLine()) != null) {
            if (line.equals("go")) {
                if (pw == null) {
                    pw = new DuelPlanetWars(message);
                } else {
                    pw.finalizeUpdate();
                }

                doTurn(pw);
                pw.finishTurn();
                pw.initializeUpdate();

                message = "";
            } else {
                if (pw == null) {
                    message += line + "\n";
                } else {
                    pw.updateLine(line);
                }
            }
        }
    }

    private static Prediction2[] predictFuture(DuelPlanetWars pw) {
        Prediction2[] predictions = new Prediction2[pw.getNumPlanets()];

        for (Planet planet : pw.getPlanets()) {
            predictions[planet.getPlanetID()] = new Prediction2(planet);
        }

        for (Fleet fleet : pw.getMyFleets()) {
            predictions[fleet.getDestination()].addIncomingFleet(fleet, false);
        }

        for (Fleet fleet : pw.getEnemyFleets()) {
            predictions[fleet.getDestination()].addIncomingFleet(fleet, false);
        }

        for (int i = 0; i < predictions.length; i++) {
            predictions[i].computePredictions();
        }

        return predictions;
    }

    private static class Attack implements Comparable<Attack> {

        Planet target;
        List<Planet> attackers;
        int takeOverTurn;
        int closerEnemies;
        int shipsNeeded;
        int shipsAvailable;
        double score;

        Attack(Planet target, List<Planet> attackers, int takeOverTurn, int closerEnemies, int shipsNeeded, int shipsAvailable) {
            this.target = target;
            this.attackers = attackers;
            this.takeOverTurn = takeOverTurn;
            this.closerEnemies = closerEnemies;
            this.shipsNeeded = shipsNeeded;
            this.shipsAvailable = shipsAvailable;
        }

        public void computeScore(Prediction2[] predictions) {
            // Let's see how many troops this gives me
            int nResultingShips = 0;

            if (predictions[target.getPlanetID()].getOwnerAt(MAX_TURNS - turn) == 2) {
                nResultingShips = predictions[target.getPlanetID()].getNumShipsAt(MAX_TURNS - turn); // The number of enemy ships we prevent
            }

            predictions[target.getPlanetID()].addIncomingFleet(1, shipsAvailable, takeOverTurn, true);
            nResultingShips += predictions[target.getPlanetID()].getNumShipsAt(MAX_TURNS - turn, true);
            predictions[target.getPlanetID()].removeIncomingFleet(1, shipsAvailable, takeOverTurn, true);

            if (defensive) {
                if (closerEnemies > 0) {
                    score = 0;
                } else {
                    score = (nResultingShips - shipsAvailable) / shipsNeeded;

                    if (shipsAvailable >= shipsNeeded + closerEnemies) {
                        score *= 2;
                    }
                }
            } else {
                score = (nResultingShips - shipsAvailable) / (shipsNeeded * (closerEnemies + 1));
            }
        }

        public int compareTo(Attack o) {
            return -Double.compare(score, o.score);
        }

        @Override
        public String toString() {
            return "Attack{" + "target=" + target + "attackers=" + attackers + "takeOverTurn=" + takeOverTurn + "closerEnemies=" + closerEnemies + "shipsNeeded=" + shipsNeeded + "shipsAvailable=" + shipsAvailable + "score=" + score + '}';
        }
    }

    private static void attack(DuelPlanetWars pw, final Prediction2[] predictions) {
        // Find out which attacks are possible
        List<Attack> attacks = new ArrayList<Attack>();

        for (Planet target : pw.getPlanets()) {
            if (predictions[target.getPlanetID()].getOwnerAt(MAX_TURNS - turn) != 1) {
                Attack attack = findBestAttack(pw, predictions, target);

                if (attack != null) {
                    attacks.add(attack);
                }
            }
        }

        Collections.sort(attacks);

        for (Attack attack : attacks) {
            if (attack.score > 0) {
                // Is it still possible?
                int shipsAvailable = 0;
                List<Pair<Planet, Pair<Integer, Double>>> attackers = new ArrayList<Pair<Planet, Pair<Integer, Double>>>();

                for (Planet attacker : attack.attackers) {
                    int surplus = getAttackSurplus(pw, predictions, attacker, attack.takeOverTurn - pw.getDistance(attacker, attack.target));

                    if (surplus > 0) {
                        shipsAvailable += surplus;
                        attackers.add(new Pair<Planet, Pair<Integer, Double>>(attacker, new Pair<Integer, Double>(surplus, 0d)));
                    }
                }

                if (shipsAvailable >= attack.shipsNeeded) {
                    // Sort the attackers by cost
                    Collections.sort(attackers, new Comparator<Pair<Planet, Pair<Integer, Double>>>() {

                        public int compare(Pair<Planet, Pair<Integer, Double>> o1, Pair<Planet, Pair<Integer, Double>> o2) {
                            return Double.compare(o1.getSecond().getSecond(), o2.getSecond().getSecond());
                        }
                    });

                    if (shipsAvailable >= attack.shipsNeeded + attack.closerEnemies) {
                        // Make sure this attack can't be defended against
                        attack.shipsNeeded += attack.closerEnemies;
                    }

                    // Take as many attackers with the lowest cost as possible, until the number of ships is large enough
                    for (Pair<Planet, Pair<Integer, Double>> attacker : attackers) {
                        int shipsToSend = Math.min(attack.shipsNeeded, attacker.getSecond().getFirst());
                        int turnsRemaining = attack.takeOverTurn - pw.getDistance(attacker.getFirst(), attack.target);

                        // Only send the order now if absolutely necessary
                        if (turnsRemaining == 0 && shipsToSend > 0) {
                            pw.issueOrder(attacker.getFirst(), attack.target, shipsToSend);
                        }

                        attack.shipsNeeded -= shipsToSend;

                        // Make sure we don't try to send more troops from this planet than possible and record the incoming fleet at the target
                        predictions[attacker.getFirst().getPlanetID()].addOutgoingFleet(1, shipsToSend, turnsRemaining, true);
                        predictions[attack.target.getPlanetID()].addIncomingFleet(1, shipsToSend, attack.takeOverTurn, false);

                        if (attack.shipsNeeded == 0) {
                            break;
                        }
                    }

                    // Update predictions for the target
                    predictions[attack.target.getPlanetID()].computePredictions();
                }
            }
        }
    }

    private static Attack findBestAttack(DuelPlanetWars pw, Prediction2[] predictions, Planet target) {
        Set<Integer> turns = new HashSet<Integer>();

        for (Entry<Integer, Force> entry : predictions[target.getPlanetID()].getIncomingForces().entrySet()) {
            if (entry.getValue().ships1 > 0) {
                // We want to coordinate this attack
                turns.add(entry.getKey());
            }

            if (entry.getValue().ships2 > 0) {
                // We want to snipe the planet
                turns.add(entry.getKey() + 1);
            }
        }

        for (Planet planet : pw.getMyPlanets()) {
            // We might want to attack from this planet
            turns.add(pw.getDistance(target, planet));
        }

        Attack bestAttack = null;
        double bestScore = Double.NEGATIVE_INFINITY;

        for (Integer t : turns) {
            Attack attack = findAttack(pw, predictions, target, t);

            if (attack != null) {
                attack.computeScore(predictions);

                if (attack.score > bestScore) {
                    bestAttack = attack;
                    bestScore = attack.score;
                }
            }
        }

        return bestAttack;
    }

    private static Attack findAttack(DuelPlanetWars pw, Prediction2[] predictions, Planet target, Integer t) {
        int shipsAvailable = 0;
        int closerEnemies = 0;
        List<Planet> attackers = new ArrayList<Planet>();

        for (Planet planet : pw.getClosestPlanets(target)) {
            int dist = pw.getDistance(target, planet);

            if (dist <= t) {
                if (planet.getOwner() == 1) {
                    int surplus = getAttackSurplus(pw, predictions, planet, t - dist);

                    if (surplus > 0) {
                        shipsAvailable += surplus;
                        attackers.add(planet);
                    }
                } else if (planet.getOwner() == 2 && dist < t) {
                    int nShips = predictions[planet.getPlanetID()].getNumShipsAt(t - dist, true);

                    if (nShips < 0) {
                        closerEnemies -= nShips;
                    }
                }
            } else {
                break;
            }
        }

        if (shipsAvailable > 0) {
            int shipsNeeded = predictions[target.getPlanetID()].getAttackForceRequiredAt(t);

            if (shipsAvailable >= shipsNeeded) {
                return new Attack(target, attackers, t, closerEnemies, shipsNeeded, shipsAvailable);
            }
        }

        return null;
    }

    private static int getAttackSurplus(DuelPlanetWars pw, Prediction2[] predictions, Planet attacker, int t) {
        // TODO: Optimization - don't add or remove fleets, it's slow
        if (defensive) {
            // The attacking planet should be able to survive the closest enemy planet's attack, counting reinforcements
            for (Planet planet : pw.getClosestPlanets(attacker)) {
                int closeFriendlyShips = 0;

                if (planet.getOwner() == 1) {
                    closeFriendlyShips += planet.getNumShips();
                }

                if (planet.getOwner() == 2) {
                    int nShips = planet.getNumShips();

                    if (closeFriendlyShips >= nShips) {
                        return predictions[attacker.getPlanetID()].getSurplusAt(t);
                    } else {
                        int dist = pw.getDistance(planet, attacker);

                        predictions[attacker.getPlanetID()].addIncomingFleet(2, nShips - closeFriendlyShips, dist, true);
                        int surplus = predictions[attacker.getPlanetID()].getSurplusAt(t);
                        predictions[attacker.getPlanetID()].removeIncomingFleet(2, nShips - closeFriendlyShips, dist, true);

                        return surplus;
                    }
                }
            }

            return predictions[attacker.getPlanetID()].getSurplusAt(t);
        } else {
            return predictions[attacker.getPlanetID()].getSurplusAt(t);
        }
    }

    private static void rage(DuelPlanetWars pw, final Prediction2[] predictions) {
        // Send all surplus to the closest enemy planet
        for (Planet planet : pw.getMyPlanets()) {
            int surplus = predictions[planet.getPlanetID()].getSurplusAt(0);

            if (surplus > 0) {
                for (Planet target : pw.getClosestPlanets(planet)) {
                    int dist = pw.getDistance(planet, target);

                    if (predictions[target.getPlanetID()].getOwnerAt(MAX_TURNS - turn) == 2 && predictions[target.getPlanetID()].getOwnerAt(dist - 1) != 0) {
                        pw.issueOrder(planet, target, surplus);
                        break;
                    }
                }
            }
        }
    }

    private static void supply(DuelPlanetWars pw, Prediction2[] predictions) {
        Set<Planet> frontier = findFrontier(pw, predictions);

        if (frontier.isEmpty()) {
            // No frontier means we can't supply anything
            return;
        }

        for (Planet planet : pw.getMyPlanets()) {
            if (!frontier.contains(planet)) {
                // Find the closest frontier planet
                Planet supplyTarget = null;

                for (Planet p : pw.getClosestPlanets(planet)) {
                    if (frontier.contains(p)) {
                        supplyTarget = p;
                        break;
                    }
                }

                assert supplyTarget != null; // This only happens if the frontier is empty, which should never be the case

                // See if there are intermediate planets we can route through without it costing too much time
                int supplyDist = pw.getDistance(planet, supplyTarget);
                int maxDist = (int) Math.ceil(1.2 * supplyDist);
                int bestScore = Integer.MAX_VALUE;
                Planet bestRelay = null;

                for (Planet p : pw.getClosestPlanets(planet)) {
                    int dist = pw.getDistance(p, supplyTarget);

                    if (dist < supplyDist && predictions[p.getPlanetID()].getOwnerAt(dist) == 1) {
                        // This is a potential intermediate stop, check if we don't make too large a detour
                        int distToP = pw.getDistance(planet, p);

                        if (dist + distToP <= maxDist) {
                            int score = dist * dist + distToP * distToP; // TODO: fine-tune the score?

                            if (score < bestScore) {
                                bestScore = score;
                                bestRelay = p;
                            }
                        }
                    }

                    if (p == supplyTarget) {
                        // We have seen all closer planets
                        break;
                    }
                }

                assert bestRelay != null; // supplyTarget is a valid candidate, so this shouldn't occur

                // Send stuff to the best relay
                int surplus = predictions[planet.getPlanetID()].getSurplusAt(0);

                if (surplus > 0) {
                    pw.issueOrder(planet, bestRelay, surplus);
                    // TODO: updates might not be needed
                    predictions[planet.getPlanetID()].addOutgoingFleet(1, surplus, 0, true);
                    predictions[bestRelay.getPlanetID()].addIncomingFleet(1, surplus, pw.getDistance(planet, bestRelay), true);
                }
            }
        }
    }

    /**
     * Determines which planets make up the frontier, but doesn't send any ships yet.
     * @param pw
     * @param predictions
     * @return
     */
    private static Set<Planet> findFrontier(DuelPlanetWars pw, Prediction2[] predictions) {
        // All planets that don't have other planets that are closer to them than the nearest enemy planet are automatically in the frontier
        Set<Planet> frontier = new HashSet<Planet>(pw.getNumPlanets());

        for (Planet mine : pw.getMyPlanets()) {
            int closestAlly = -1;

            for (Planet planet : pw.getClosestPlanets(mine)) {
                int dist = pw.getDistance(mine, planet);

                if (closestAlly > -1) {
                    if (dist > closestAlly) {
                        // This planet has an ally that is strictly closer than the closest enemy planet; it is not necessarily in the frontier
                        break;
                    }
                } else {
                    if (planet.getOwner() == 1) {
                        closestAlly = dist;
                    }
                }

                if (planet.getOwner() == 2) {
                    // There is no strictly closer allied planet (else we would have left the loop before): this planet is on the frontier
                    frontier.add(mine);
                    break;
                }
            }
        }

        // Greedy frontier selection. Makes sure that every non-frontier planet has a frontier planet that is closer than the closest enemy planet.
        // For every non-frontier planet:
        // 1. If there is a frontier planet that is closer than any enemy planet, we're good
        // 2. Otherwise, add the allied planet closest to the closest enemy planet that is still closer to me to the frontier
        // 3. If there is no such planet, I am a frontier planet
        for (Planet planet : pw.getMyPlanets()) {
            if (!frontier.contains(planet)) {
                Planet closestEnemy = null;
                int closestEnemyDist = -1;

                for (Planet p : pw.getClosestPlanets(planet)) {
                    if (frontier.contains(p)) { // && predictions[p.getPlanetID()].getOwnerAt(pw.getDistance(p, planet)) == 1 ?
                        break;
                    } else if (p.getOwner() == 2) {
                        closestEnemy = p;
                        closestEnemyDist = pw.getDistance(p, planet);
                        break;
                    }
                }

                if (closestEnemy != null) {
                    // We encountered an enemy planet before finding a suitable frontier planet to send ships to.
                    // Find the allied planet closest to the enemy planet that is still closer to me and add it to the frontier.
                    int minDistToEnemy = closestEnemyDist;
                    Planet supplyTarget = planet;

                    for (Planet p : pw.getClosestPlanets(planet)) {
                        if (pw.getDistance(p, planet) >= closestEnemyDist) {
                            // We've seen all possible candidates, use the best.
                            frontier.add(supplyTarget);
                            break;
                        }

                        if (p.getOwner() == 1) { // && predictions[p.getPlanetID()].getOwnerAt(pw.getDistance(p, planet)) == 1 ?
                            // This planet is a possible supply target and it's closer to me than the closest enemy, only thing left is to compare distance to the enemy
                            int dist = pw.getDistance(p, closestEnemy);

                            if (dist < minDistToEnemy) {
                                minDistToEnemy = dist;
                                supplyTarget = p;
                            }
                        }
                    }
                }
            }
        }

        return frontier;
    }
}
