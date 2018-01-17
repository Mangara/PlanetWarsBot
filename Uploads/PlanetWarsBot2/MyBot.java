
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

public class MyBot {

    //// DEBUG FLAGS ////
    public static final boolean DEBUG_ATTACK = false;
    public static final boolean DEBUG_DEFENSE = false;
    public static final boolean DEBUG_PREDICTIONS = false;
    //// DEBUG FLAGS ////
    public static PrintWriter log = new PrintWriter(System.out);
    private static int turn = 0;
    private static final int MAX_TURNS = 1001;
    private static boolean defensive = true;

    public static void doTurn(DuelPlanetWars pw) {
        turn++;

        if (DEBUG_ATTACK || DEBUG_DEFENSE || DEBUG_PREDICTIONS) {
            log.println("Started turn " + turn + ". Turns left: " + (MAX_TURNS - turn));
        }

        // Predict the future states of all planets
        Prediction2[] predictions = predictFuture(pw);

        // switch between defensive and aggressive modes
        selectState(pw, predictions);

        // Defend planets that are under attack
        defend(pw, predictions);

        // Attack vulnerable planets
        attack(pw, predictions);

        // Send reinforcements to the front lines
        supply(pw, predictions);

        if (DEBUG_ATTACK || DEBUG_DEFENSE || DEBUG_PREDICTIONS) {
            log.flush();
        }

        return;
    }

    public static void main(String[] args) throws IOException {
        //// DEBUG ////
        //log = new PrintWriter("Log2.txt");
        //log = new PrintWriter("Log" + (new Random()).nextInt(500) + ".txt");
        //log = new PrintWriter(System.err);
        //// DEBUG ////

        String line;
        String message = "";

        DuelPlanetWars pw = null;
        BufferedReader in = new BufferedReader(new InputStreamReader(System.in));

        while ((line = in.readLine()) != null) {
            if (line.equals("go")) {
                if (pw == null) {
                    pw = new DuelPlanetWars(message);

                    //// DEBUG ////
                    //pw.setLogger(log);
                    //// DEBUG ////
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

    private static void selectState(DuelPlanetWars pw, Prediction2[] predictions) {
        if (turn < 40) {
            // Play defensive in the early game; don't overcommit
            defensive = true;
        } else {
            int totalShips = pw.getNumShips(1);
            int totalProduction = pw.getProduction(1);
            int enemyShips = pw.getNumShips(2);
            int enemyProduction = pw.getProduction(2);

            if (totalShips > 1.2 * enemyShips && totalProduction > 1.2 * enemyProduction) {
                // If we are far ahead, we don't need to be defensive
                defensive = false;
            } else {
                int myShipsFinal = 0;
                int enemyShipsFinal = 0;

                for (Prediction2 pred : predictions) {
                    int owner = pred.getOwnerAt(MAX_TURNS - turn);

                    if (owner == 1) {
                        myShipsFinal += pred.getNumShipsAt(MAX_TURNS - turn);
                    } else if (owner == 2) {
                        enemyShipsFinal += pred.getNumShipsAt(MAX_TURNS - turn);
                    }
                }

                if (enemyShipsFinal >= myShipsFinal) {
                    defensive = false;
                } else {
                    defensive = true;
                }
            }
        }
    }

    private static void defend(final DuelPlanetWars pw, Prediction2[] predictions) {
        // Find out which planets are under attack, sorted by importance (highest -> lowest)
        List<Planet> attacked = getPlanetsUnderAttack(pw, predictions);

        if (DEBUG_DEFENSE) {
            log.println("Planets under attack:");
            for (Planet p : attacked) {
                log.println(p);
            }
        }

        // Figure out how best to defend each planet, if at all possible
        for (Planet planet : attacked) {
            if (DEBUG_DEFENSE) {
                log.println("Defending " + planet);
            }

            // Determine the best defense strategy
            Prediction2 pred = predictions[planet.getPlanetID()];

            // TODO: defend against all incoming fleets, not just this hostile take-over
            Pair<Integer, Integer> takeOver = pred.getLastHostileTakeOver();
            int takeOverTurn = takeOver.getFirst();
            int shipsNeeded = takeOver.getSecond();
            int totalShipsAvailable = 0;

            if (DEBUG_DEFENSE) {
                log.println("takeOverTurn: " + takeOverTurn + " shipsNeeded: " + shipsNeeded);
            }

            List<Pair<Planet, Pair<Integer, Double>>> defenders = new ArrayList<Pair<Planet, Pair<Integer, Double>>>();

            // Can we defend in time?
            for (Planet p : pw.getClosestPlanets(planet)) {
                int distance = pw.getDistance(p, planet);

                if (distance <= takeOverTurn) {
                    // Ships from this planet can make it in time
                    int shipsAvailable = predictions[p.getPlanetID()].getSurplusAt(takeOverTurn - distance);

                    if (shipsAvailable > 0) {
                        totalShipsAvailable += shipsAvailable;
                        defenders.add(new Pair<Planet, Pair<Integer, Double>>(p, new Pair<Integer, Double>(shipsAvailable, 0.0)));
                    }
                } else {
                    // The planets are sorted, others won't make it in time either
                    break;
                }
            }

            if (DEBUG_DEFENSE) {
                log.println("Available defenders:");
                for (Pair<Planet, Pair<Integer, Double>> pair : defenders) {
                    log.println(pair.getFirst() + " with " + pair.getSecond().getFirst() + " ships.");
                }
            }

            if (totalShipsAvailable >= shipsNeeded) {
                // We can defend in time: decide on the distribution
                for (Pair<Planet, Pair<Integer, Double>> defender : defenders) {
                    defender.getSecond().setSecond(computeFleetCost(pw, predictions, defender.getFirst(), defender.getSecond().getFirst(), takeOverTurn, pw.getDistance(defender.getFirst(), planet), shipsNeeded, totalShipsAvailable));
                }

                // Sort the defenders by cost
                Collections.sort(defenders, new Comparator<Pair<Planet, Pair<Integer, Double>>>() {

                    public int compare(Pair<Planet, Pair<Integer, Double>> o1, Pair<Planet, Pair<Integer, Double>> o2) {
                        return Double.compare(o1.getSecond().getSecond(), o2.getSecond().getSecond());
                    }
                });

                if (DEBUG_DEFENSE) {
                    log.println("Sorted defenders:");
                    for (Pair<Planet, Pair<Integer, Double>> pair : defenders) {
                        log.println(pair.getFirst() + " with " + pair.getSecond().getFirst() + " ships, at cost " + pair.getSecond().getSecond());
                    }
                }

                // Take as many defenders with the lowest cost as possible, until the number of ships is large enough
                for (Pair<Planet, Pair<Integer, Double>> defender : defenders) {
                    int shipsToSend = Math.min(shipsNeeded, defender.getSecond().getFirst());
                    int turnsRemaining = takeOverTurn - pw.getDistance(defender.getFirst(), planet);

                    if (DEBUG_DEFENSE) {
                        log.print("Sending " + shipsToSend + " ships from " + defender.getFirst() + " in " + turnsRemaining + " turns.");
                    }

                    // Only send the order now if absolutely necessary
                    if (turnsRemaining == 0 && shipsToSend > 0) {
                        if (DEBUG_DEFENSE) {
                            log.println(" Command issued.");
                        }
                        pw.issueOrder(defender.getFirst(), planet, shipsToSend);
                    } else {
                        if (DEBUG_DEFENSE) {
                            log.println();
                        }
                    }

                    shipsNeeded -= shipsToSend;

                    // Make sure we don't try to send more troops from this planet than possible and record the incoming fleet at the target
                    predictions[defender.getFirst().getPlanetID()].addOutgoingFleet(1, shipsToSend, turnsRemaining, true);
                    predictions[planet.getPlanetID()].addIncomingFleet(1, shipsToSend, takeOverTurn, false);

                    if (DEBUG_DEFENSE) {
                        log.print("New prediction for planet " + defender.getFirst() + ": ");

                        Prediction2 p = predictions[defender.getFirst().getPlanetID()];
                        int n = 20;

                        for (int i = 0; i < n - 1; i++) {
                            log.print(p.getNumShipsAt(i, true) + ", ");
                        }

                        log.println(p.getNumShipsAt(n - 1, true));

                        log.println("Planet's surplus: " + p.getSurplusAt(0));
                    }

                    if (shipsNeeded == 0) {
                        break;
                    }
                }

                // Update predictions for the target
                predictions[planet.getPlanetID()].computePredictions();
            } else {
                // We can't defend in time =(
                if (DEBUG_DEFENSE) {
                    log.println("That isn't enough, let this one fall.");
                }
                continue;
            }
        }

        if (DEBUG_DEFENSE) {
            log.flush();
        }
    }

    private static List<Planet> getPlanetsUnderAttack(final DuelPlanetWars pw, Prediction2[] predictions) {
        List<Planet> attacked = new ArrayList<Planet>();

        for (Planet planet : pw.getPlanets()) {
            Prediction2 pred = predictions[planet.getPlanetID()];

            if (pred.isMineSometime() && pred.getOwnerAt(MAX_TURNS - turn) == 2) {
                attacked.add(planet);
            }
        }

        // Sort the attacked planets lexicographically by growth rate and average distance to my planets
        Collections.sort(attacked, new Comparator<Planet>() {

            public int compare(Planet p1, Planet p2) {
                if (p1.getGrowthRate() == p2.getGrowthRate()) {
                    double sumDistance1 = 0;
                    double sumDistance2 = 0;

                    for (Planet planet : pw.getMyPlanets()) {
                        sumDistance1 += pw.getDistance(planet, p1);
                        sumDistance2 += pw.getDistance(planet, p2);
                    }

                    return Double.compare(sumDistance1, sumDistance2);
                } else {
                    return p2.getGrowthRate() - p1.getGrowthRate();
                }
            }
        });

        return attacked;
    }

    private static double computeFleetCost(DuelPlanetWars pw, Prediction2[] predictions, Planet sender, int surplus, int takeOverTurn, int distance, int shipsNeeded, int totalShipsAvailable) {
        // What is the risk this planet will be conquered by the enemy if he attacks the turn after we defend and we send all our available ships and the other planets each send (shipsNeeded - surplus) / (totalShipsAvailable - surplus) fraction of their ships
        // Express the risk as enemyForces nearby / friendlyForces nearby. 'nearby' meaning until the first 3 enemy planets
        int attackTurn = takeOverTurn - distance + 1;
        int shipsToSend = Math.min(shipsNeeded, surplus);

        if (shipsToSend == totalShipsAvailable) {
            // To avoid dividing by zero; we're the only planet anyway
            return 1;
        }

        double surplusLeft = 1 - (shipsNeeded - shipsToSend) / (double) (totalShipsAvailable - shipsToSend);

        int enemyPlanets = 0;
        int enemyTroops = 0;
        double friendlyTroops = 0.000001; // don't divide by zero

        for (Planet p : pw.getClosestPlanets(sender)) {
            int owner = predictions[p.getPlanetID()].getOwnerAt(attackTurn);

            if (owner == 1) {
                friendlyTroops += surplusLeft * predictions[p.getPlanetID()].getSurplusAt(attackTurn);
            } else if (owner == 2) {
                enemyTroops += predictions[p.getPlanetID()].getNumShipsAt(attackTurn);
                enemyPlanets++;

                if (enemyPlanets == 3) {
                    break;
                }
            }
        }

        double risk = enemyTroops / friendlyTroops;

        return distance * risk;
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

        if (DEBUG_ATTACK || DEBUG_DEFENSE || DEBUG_PREDICTIONS) {
            for (Planet planet : pw.getPlanets()) {
                log.print("Prediction for planet " + planet + ": ");

                Prediction2 p = predictions[planet.getPlanetID()];
                int n = 20;

                for (int i = 0; i < n - 1; i++) {
                    log.print(p.getNumShipsAt(i, true) + ", ");
                }

                log.println(p.getNumShipsAt(n - 1, true));
            }
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
        if (DEBUG_ATTACK) {
            log.println("Deciding which planets to attack. Current mode: " + (defensive ? "defensive" : "offensive"));
            log.flush();
        }

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

        if (DEBUG_ATTACK) {
            log.println("Sorted attacks:");
            for (Attack attack : attacks) {
                log.println(attack);
            }
            log.flush();
        }

        for (Attack attack : attacks) {
            if (attack.score > 0) {
                if (DEBUG_ATTACK) {
                    log.println("Considering attack " + attack);
                }

                // Is it still possible?
                int shipsAvailable = 0;
                List<Pair<Planet, Pair<Integer, Double>>> attackers = new ArrayList<Pair<Planet, Pair<Integer, Double>>>();

                for (Planet attacker : attack.attackers) {
                    int surplus = getAttackSurplus(pw, predictions, attacker, attack.takeOverTurn - pw.getDistance(attacker, attack.target));

                    if (surplus > 0) {
                        shipsAvailable += surplus;
                        attackers.add(new Pair<Planet, Pair<Integer, Double>>(attacker, new Pair<Integer, Double>(surplus, computeFleetCost(pw, predictions, attacker, surplus, attack.takeOverTurn, pw.getDistance(attacker, attack.target), attack.shipsNeeded, attack.shipsAvailable))));
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

                        if (DEBUG_ATTACK) {
                            log.print("Sending " + shipsToSend + " ships from " + attacker.getFirst() + " in " + turnsRemaining + " turns.");
                        }

                        // Only send the order now if absolutely necessary
                        if (turnsRemaining == 0 && shipsToSend > 0) {
                            if (DEBUG_ATTACK) {
                                log.println(" Command issued.");
                            }
                            pw.issueOrder(attacker.getFirst(), attack.target, shipsToSend);
                        } else {
                            if (DEBUG_ATTACK) {
                                log.println();
                            }
                        }

                        attack.shipsNeeded -= shipsToSend;

                        // Make sure we don't try to send more troops from this planet than possible and record the incoming fleet at the target
                        predictions[attacker.getFirst().getPlanetID()].addOutgoingFleet(1, shipsToSend, turnsRemaining, true);
                        predictions[attack.target.getPlanetID()].addIncomingFleet(1, shipsToSend, attack.takeOverTurn, false);

                        if (DEBUG_ATTACK) {
                            log.print("New prediction for planet " + attacker.getFirst() + ": ");

                            Prediction2 p = predictions[attacker.getFirst().getPlanetID()];
                            int n = 20;

                            for (int i = 0; i < n - 1; i++) {
                                log.print(p.getNumShipsAt(i, true) + ", ");
                            }

                            log.println(p.getNumShipsAt(n - 1, true));

                            log.println("Planet's surplus: " + p.getSurplusAt(0));
                        }

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

    private static void supply(DuelPlanetWars pw, Prediction2[] predictions) {
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

        // Every non-frontier planet sends his entire surplus to:
        // 1. The closest frontier planet that is closer than any enemy planet
        // 2. The allied planet closest to the closest enemy planet that is still closer to me (could be this planet)
        // 3. Itself
        // These options are evaluated in the given order. Planets chosen in steps 2 and 3 are added to the frontier.
        for (Planet planet : pw.getMyPlanets()) {
            if (!frontier.contains(planet)) {
                Planet closestEnemy = null;
                int closestEnemyDist = -1;

                for (Planet p : pw.getClosestPlanets(planet)) {
                    if (frontier.contains(p) && predictions[p.getPlanetID()].getOwnerAt(pw.getDistance(p, planet)) == 1) {
                        int surplus = predictions[planet.getPlanetID()].getSurplusAt(0);

                        if (surplus > 0) {
                            pw.issueOrder(planet, p, surplus);
                            //predictions[planet.getPlanetID()].addOutgoingFleet(1, surplus, 0, false); // TODO: probably not needed
                        }

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
                            // We're through all possible candidates now, let's see what we've got
                            frontier.add(supplyTarget);

                            if (supplyTarget.getPlanetID() != planet.getPlanetID()) {
                                int surplus = predictions[planet.getPlanetID()].getSurplusAt(0);

                                if (surplus > 0) {
                                    pw.issueOrder(planet, supplyTarget, surplus);
                                    //predictions[planet.getPlanetID()].addOutgoingFleet(1, surplus, 0, false); // TODO: probably not needed
                                }
                            }

                            break;
                        }

                        if (p.getOwner() == 1 && predictions[p.getPlanetID()].getOwnerAt(pw.getDistance(p, planet)) == 1) {
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
    }
}
